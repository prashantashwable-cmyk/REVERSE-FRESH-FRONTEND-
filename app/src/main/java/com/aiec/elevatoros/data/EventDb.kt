package com.aiec.elevatoros.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.aiec.elevatoros.domain.Event
import com.aiec.elevatoros.domain.JSON
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Durable, append-only event log in app-private SQLite (WAL). One file per mode: live company and practice never mix.
 * The event id is UNIQUE, so saving the same record twice (retry, re-import) is a no-op: the idempotency key.
 * sync_state follows the documented model: local_saved → pending_sync → syncing → synced | failed.
 */
class EventDb(context: Context, name: String) : SQLiteOpenHelper(context, name, null, VERSION) {
    companion object {
        const val VERSION = 1
        const val LOCAL = "local_saved"; const val PENDING = "pending_sync"; const val SYNCED = "synced"; const val FAILED = "failed"
        const val IMPORTED = "imported"
    }

    override fun onConfigure(db: SQLiteDatabase) { db.enableWriteAheadLogging(); db.setForeignKeyConstraintsEnabled(true) }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE events(
                seq INTEGER PRIMARY KEY AUTOINCREMENT,
                id TEXT NOT NULL UNIQUE, type TEXT NOT NULL, by_uid TEXT NOT NULL, pid TEXT, t TEXT NOT NULL,
                dev TEXT NOT NULL, v INTEGER NOT NULL, data TEXT NOT NULL,
                sync_state TEXT NOT NULL DEFAULT '$LOCAL', attempts INTEGER NOT NULL DEFAULT 0, last_error TEXT)"""
        )
        db.execSQL("CREATE INDEX events_pid ON events(pid)")
        db.execSQL("CREATE TABLE rejected(id TEXT PRIMARY KEY, raw TEXT NOT NULL, reason TEXT NOT NULL, t TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // schema v1 is the first release; future migrations go here, never by dropping company records
    }

    /** One canonical order on every phone and after every restart or import: time, then id. */
    fun all(): List<Event> = readableDatabase.rawQuery("SELECT id,type,by_uid,pid,t,dev,v,data FROM events ORDER BY t, id", null).use { c ->
        buildList {
            while (c.moveToNext()) add(
                Event(c.getString(0), c.getString(1), c.getString(2), if (c.isNull(3)) null else c.getString(3), c.getString(4), c.getString(5), c.getInt(6),
                    JSON.parseToJsonElement(c.getString(7)).jsonObject)
            )
        }
    }

    /** Returns false when an event with this id is already saved (duplicate = already applied). */
    fun insert(ev: Event, state: String = PENDING): Boolean {
        val cv = ContentValues().apply {
            put("id", ev.id); put("type", ev.type); put("by_uid", ev.by); put("pid", ev.pid); put("t", ev.t); put("dev", ev.dev); put("v", ev.v)
            put("data", JSON.encodeToString(JsonObject.serializer(), ev.data)); put("sync_state", state)
        }
        return writableDatabase.insertWithOnConflict("events", null, cv, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun insertAll(evs: List<Event>, state: String): Int {
        var n = 0
        val db = writableDatabase
        db.beginTransaction()
        try { for (e in evs) if (insert(e, state)) n++; db.setTransactionSuccessful() } finally { db.endTransaction() }
        return n
    }

    fun markShared(ids: Collection<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEach { db.execSQL("UPDATE events SET sync_state='$SYNCED', attempts=attempts+1 WHERE id=?", arrayOf(it)) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun hasOrg(): Boolean = readableDatabase.rawQuery("SELECT 1 FROM events WHERE type='ORG_CREATED' LIMIT 1", null).use { it.moveToFirst() }

    fun counts(): Map<String, Int> = readableDatabase.rawQuery("SELECT sync_state, COUNT(*) FROM events GROUP BY sync_state", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
    }

    fun rejectedCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM rejected", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun reject(id: String, raw: String, reason: String, t: String) {
        writableDatabase.insertWithOnConflict("rejected", null, ContentValues().apply { put("id", id); put("raw", raw); put("reason", reason); put("t", t) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun wipe() { writableDatabase.execSQL("DELETE FROM events"); writableDatabase.execSQL("DELETE FROM rejected") }
}
