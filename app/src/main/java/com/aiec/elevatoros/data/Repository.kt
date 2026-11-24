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
    /** this phone's signing key (Android Keystore); every record made here is signed with it */
    val key by lazy { com.aiec.elevatoros.device.DeviceKey(context) }
    private val trust: (State, Event) -> Err? = { st, ev -> com.aiec.elevatoros.domain.Signing.check(st, ev, key.own) }
    private fun signed(e: Event): Event = com.aiec.elevatoros.domain.Signing.sign(e, key.kid, key.private)
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
        // records saved on this phone by version 1.0.0 (before signatures) are signed now with this phone's key
        d.all().filter { it.sig.isBlank() && it.dev == prefs.deviceId }.forEach { e -> signed(e).let { d.updateSignature(it.id, it.kid, it.sig) } }
        val events = d.all()
        _state.value = Reducer.rebuild(events, trust) { ev, err -> d.reject(ev.id, ev.toString(), err.key, Time.now()) }
        _version.value++
    }

    fun hasCompany(m: Mode): Boolean {
        val f = context.getDatabasePath(m.dbName)
        if (!f.exists()) return false
        return EventDb(context, m.dbName).use { it.hasOrg() }
    }

    fun newEvent(by: String, type: String, pid: String?, data: JsonObject, now: Instant = Instant.now()): Event =
        Event(Ids.eventId(prefs.deviceId, now), type, by, pid, now.toString(), prefs.deviceId, data = data)

    /** Like [performUnsafe], but an unexpected failure (storage full, key problem) is a refusal on screen, never a closed app. */
    @Synchronized
    fun perform(by: String, type: String, pid: String?, data: JsonObject): Err? =
        try { performUnsafe(by, type, pid, data) } catch (e: Exception) { CrashLog.note(context, e); Err("err_save_failed") }

    private fun performUnsafe(by: String, type: String, pid: String?, data: JsonObject): Err? {
        val d = db ?: return Err("err_no_org")
        val ev = signed(newEvent(by, type, pid, data))
        val s = _state.value
        Rules.validate(s, ev)?.let { return it }
        if (!d.insert(ev, EventDb.PENDING)) return Err("err_already") // durably saved before the screen says "saved"
        Reducer.apply(s, ev)
        _version.value++
        return null
    }

    /** Registers this phone's key for [uid] the first time they use this phone (KEY_ADDED). */
    fun ensureKey(uid: String): Err? {
        if (_state.value.keys[uid]?.any { it.kid == key.kid } == true) return null
        return perform(uid, "KEY_ADDED", null, com.aiec.elevatoros.domain.jsonOf("kid" to key.kid, "pub" to key.publicB64, "devName" to key.deviceName))
    }

    /** Signs records made elsewhere on this phone (the practice company is built before the database is open). */
    fun signAll(events: List<Event>): List<Event> = events.map { signed(it) }

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
            // other people's PIN records are cut to their fingerprint: the signature still verifies, the PIN can't be guessed
            if ((e.type == "USER_ADDED" || e.type == "USER_UPDATED") && e.data.str("uid") != forUid && e.data.containsKey("pin"))
                EventWire.of(com.aiec.elevatoros.domain.Signing.cut(e))
            else EventWire.of(e)
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
        val rebuilt = Reducer.rebuild(merged, trust) { ev, err -> if (ev.id !in known) rejectedIds += ev.id; d.reject(ev.id, ev.toString(), err.key, Time.now()) }
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
data class EventWire(val id: String, val type: String, val by: String, val pid: String? = null, val t: String, val dev: String, val v: Int, val data: JsonObject, val kid: String = "", val sig: String = "") {
    fun toEvent() = Event(id, type, by, pid, t, dev, v, data, kid, sig)
    companion object { fun of(e: Event) = EventWire(e.id, e.type, e.by, e.pid, e.t, e.dev, e.v, e.data, e.kid, e.sig) }
}
