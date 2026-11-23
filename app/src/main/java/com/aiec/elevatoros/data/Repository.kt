package com.aiec.elevatoros.data

import android.content.Context
import android.content.SharedPreferences
import com.aiec.elevatoros.domain.Err
import com.aiec.elevatoros.domain.Event
import com.aiec.elevatoros.domain.Ids
import com.aiec.elevatoros.domain.JSON
import com.aiec.elevatoros.domain.Reducer
import com.aiec.elevatoros.domain.Rules
import com.aiec.elevatoros.domain.State
import com.aiec.elevatoros.domain.Time
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import com.aiec.elevatoros.domain.str
import java.time.Instant

enum class Mode(val dbName: String) { LIVE("aiec_live.db"), PRACTICE("aiec_practice.db") }

/** Small per-phone settings. Never holds PINs or company data. */
class Prefs(context: Context) {
    private val sp: SharedPreferences = context.getSharedPreferences("aiec_prefs", Context.MODE_PRIVATE)
    var lang: String
        get() = sp.getString("lang", "mr") ?: "mr"
        set(v) = sp.edit().putString("lang", v).apply()
    var theme: String
        get() = sp.getString("theme", "system") ?: "system"
        set(v) = sp.edit().putString("theme", v).apply()
    var mode: Mode?
        get() = sp.getString("mode", null)?.let { runCatching { Mode.valueOf(it) }.getOrNull() }
        set(v) = sp.edit().putString("mode", v?.name).apply()
    var lastUser: String?
        get() = sp.getString("lastUser", null)
        set(v) = sp.edit().putString("lastUser", v).apply()
    var lastExport: String?
        get() = sp.getString("lastExport", null)
        set(v) = sp.edit().putString("lastExport", v).apply()
    val deviceId: String
        get() = sp.getString("dev", null) ?: Ids.rid(8).also { sp.edit().putString("dev", it).apply() }
    fun failedPins(uid: String) = sp.getInt("pinfail_$uid", 0)
    fun setFailedPins(uid: String, n: Int) = sp.edit().putInt("pinfail_$uid", n).apply()
    fun lockedUntil(uid: String) = sp.getLong("pinlock_$uid", 0L)
    fun setLockedUntil(uid: String, t: Long) = sp.edit().putLong("pinlock_$uid", t).apply()
}

/**
 * The phone's single source of truth: the event log (SQLite) and the State rebuilt from it.
 * perform() is the only way to change anything: build event → Rules.validate → save durably → apply.
 * The UI observes [state]; a refusal comes back as an Err and nothing is saved.
 */
class Repository(private val context: Context, val prefs: Prefs) {
    private var db: EventDb? = null
    var mode: Mode? = null; private set
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val _version = MutableStateFlow(0)
    /** bumps on every change so Compose recomposes even though State is mutable */
    val version: StateFlow<Int> = _version

    fun open(m: Mode) {
        if (mode == m && db != null) return
        db?.close()
        db = EventDb(context, m.dbName)
        mode = m
        reload()
    }

    fun close() { db?.close(); db = null; mode = null; _state.value = State(); _version.value++ }

    fun reload() {
        val d = db ?: return
        val events = d.all()
        _state.value = Reducer.rebuild(events) { ev, err -> d.reject(ev.id, ev.toString(), err.key, Time.now()) }
        _version.value++
    }

    fun hasCompany(m: Mode): Boolean {
        val f = context.getDatabasePath(m.dbName)
        if (!f.exists()) return false
        return EventDb(context, m.dbName).use { it.hasOrg() }
    }

    fun newEvent(by: String, type: String, pid: String?, data: JsonObject, now: Instant = Instant.now()): Event =
        Event(Ids.eventId(prefs.deviceId, now), type, by, pid, now.toString(), prefs.deviceId, data = data)

    @Synchronized
    fun perform(by: String, type: String, pid: String?, data: JsonObject): Err? {
        val d = db ?: return Err("err_no_org")
        val ev = newEvent(by, type, pid, data)
        val s = _state.value
        Rules.validate(s, ev)?.let { return it }
        if (!d.insert(ev, EventDb.PENDING)) return Err("err_already") // durably saved before the screen says "saved"
        Reducer.apply(s, ev)
        _version.value++
        return null
    }

    fun syncCounts(): Map<String, Int> = db?.counts() ?: emptyMap()
    fun rejectedCount(): Int = db?.rejectedCount() ?: 0

    /**
     * A records file for another company phone (the original app's file sync). Every record travels so the receiving
     * phone can re-check all of them, but PIN hashes are removed for everyone except [forUid] — the person who will
     * log in on that phone — so a copied file cannot be used to recover other people's PINs.
     */
    fun exportJson(forUid: String, onlyBy: String? = null): String {
        val d = db ?: return "{}"
        // people outside the office send only the records they made themselves
        val evs = d.all().filter { onlyBy == null || it.by == onlyBy }
        val s = _state.value
        val wire = evs.map { e ->
            val w = EventWire.of(e)
            if ((e.type == "USER_ADDED" && e.data.str("uid") != forUid) || (e.type == "USER_UPDATED" && e.data.str("uid") != forUid && e.data.containsKey("pin")))
                w.copy(data = JsonObject(e.data.filterKeys { it != "pin" && it != "salt" } + ("stripped" to kotlinx.serialization.json.JsonPrimitive(true))))
            else w
        }
        return buildJsonObject {
            put("format", "aiec-events"); put("v", 1); put("org", s.org?.id ?: ""); put("orgName", s.org?.name ?: "")
            put("from", prefs.deviceId); put("for", forUid); put("t", Time.now())
            put("events", buildJsonArray { wire.forEach { add(JSON.encodeToJsonElement(EventWire.serializer(), it)) } })
        }.toString().also { d.markShared(evs.map { it.id }); prefs.lastExport = Time.now() }
    }

    data class ImportResult(val added: Int, val duplicates: Int, val rejected: Int, val wrongCompany: Boolean)

    /** Load another phone's file: each new record passes the same rules, in order; duplicates are skipped by id. */
    /**
     * Records files are not signed in this version (see 06/12). To limit what a forged file can do, an imported record
     * may not create a Head Quarter or Admin, change anyone's PIN or role, or change business rules unless the person
     * importing is the Head Quarter (who is then shown the counts).
     */
    fun importJson(text: String, importerRole: com.aiec.elevatoros.domain.Role?): ImportResult {
        val d = db ?: return ImportResult(0, 0, 0, true)
        val root = runCatching { JSON.parseToJsonElement(text).jsonObject }.getOrNull() ?: return ImportResult(0, 0, 0, true)
        if (root["format"]?.toString()?.trim('"') != "aiec-events") return ImportResult(0, 0, 0, true)
        val incoming = root["events"]?.jsonArray?.mapNotNull { runCatching { JSON.decodeFromJsonElement(EventWire.serializer(), it).toEvent() }.getOrNull() } ?: emptyList()
        val s = _state.value
        val fileOrg = root["org"]?.toString()?.trim('"').orEmpty()
        if (s.org != null && fileOrg.isNotEmpty() && fileOrg != s.org!!.id) return ImportResult(0, 0, 0, true)
        val known = d.all().map { it.id }.toSet()
        val hq = importerRole == com.aiec.elevatoros.domain.Role.OWNER
        fun sensitive(e: Event) = !hq && (e.type == "CONFIG_SET" || e.type == "ORG_CREATED" ||
            (e.type == "USER_ADDED" && e.data.str("role") in setOf("owner", "admin")) ||
            (e.type == "USER_UPDATED" && (e.data.containsKey("pin") || e.data.containsKey("role"))))
        val blocked = incoming.filter { it.id !in known && sensitive(it) }
        blocked.forEach { d.reject(it.id, it.toString(), "err_import_sensitive", Time.now()) }
        val fresh = incoming.filter { it.id !in known && !sensitive(it) }
        // the merged history is ordered by time then id, the same on every phone
        val merged = (d.all() + fresh).sortedWith(compareBy({ it.t }, { it.id }))
        val rejectedIds = mutableSetOf<String>()
        // local records that no longer pass after the merge are reported too, never silently dropped
        val rebuilt = Reducer.rebuild(merged) { ev, err -> if (ev.id !in known) rejectedIds += ev.id; d.reject(ev.id, ev.toString(), err.key, Time.now()) }
        d.insertAll(fresh.filter { it.id !in rejectedIds }, EventDb.IMPORTED)
        _state.value = rebuilt; _version.value++
        return ImportResult(fresh.size - rejectedIds.size, incoming.count { it.id in known }, rejectedIds.size + blocked.size, false)
    }

    fun wipe(m: Mode) {
        db?.close(); db = null; mode = null
        context.deleteDatabase(m.dbName)
        _state.value = State(); _version.value++
    }
}

@kotlinx.serialization.Serializable
data class EventWire(val id: String, val type: String, val by: String, val pid: String? = null, val t: String, val dev: String, val v: Int, val data: JsonObject) {
    fun toEvent() = Event(id, type, by, pid, t, dev, v, data)
    companion object { fun of(e: Event) = EventWire(e.id, e.type, e.by, e.pid, e.t, e.dev, e.v, e.data) }
}
