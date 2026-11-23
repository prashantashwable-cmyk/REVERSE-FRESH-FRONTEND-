package com.aiec.elevatoros.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

fun JsonObject.str(k: String): String = (this[k] as? JsonPrimitive)?.let { if (it is JsonNull) "" else it.content } ?: ""
fun JsonObject.strOrNull(k: String): String? = str(k).ifEmpty { null }
fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
fun JsonObject.int(k: String): Int? = num(k)?.toInt()
fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
fun JsonObject.arr(k: String): JsonArray = (this[k] as? JsonArray) ?: JsonArray(emptyList())
fun JsonObject.strList(k: String): List<String> = arr(k).mapNotNull { (it as? JsonPrimitive)?.content }
fun JsonObject.strMap(k: String): Map<String, String> = obj(k)?.mapValues { (_, v) -> (v as? JsonPrimitive)?.content ?: "" } ?: emptyMap()

/** Build event data without boilerplate: jsonOf("name" to "A", "floors" to 4, "ev" to listOf(...)). */
fun jsonOf(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(pairs.associate { (k, v) -> k to toJson(v) })
fun toJson(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to toJson(x) })
    is Iterable<*> -> JsonArray(v.map { toJson(it) })
    is GeoPoint -> jsonOf("lat" to v.lat, "lon" to v.lon, "acc" to v.acc, "t" to v.t)
    is EvidenceRef -> jsonOf("id" to v.id, "kind" to v.kind, "sha256" to v.sha256, "t" to v.t, "gps" to v.gps, "tag" to v.tag)
    else -> JsonPrimitive(v.toString())
}

fun JsonObject.gps(k: String = "gps"): GeoPoint? = obj(k)?.let { o ->
    val lat = o.num("lat"); val lon = o.num("lon")
    if (lat == null || lon == null) null else GeoPoint(lat, lon, o.num("acc"), o.strOrNull("t"))
}

fun JsonObject.evidence(): List<EvidenceRef> = arr("ev").mapNotNull { e ->
    val o = e as? JsonObject ?: return@mapNotNull null
    val id = o.str("id"); val sha = o.str("sha256")
    if (id.isEmpty() || sha.isEmpty()) null else EvidenceRef(id, o.str("kind").ifEmpty { "photo" }, sha, o.str("t"), o.gps(), o.str("tag"))
}

fun JsonElement?.asObj(): JsonObject = (this as? JsonObject) ?: JsonObject(emptyMap())

object Ids {
    private val rnd = SecureRandom()
    private const val ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    fun rid(n: Int): String = buildString { repeat(n) { append(ALPHA[rnd.nextInt(ALPHA.length)]) } }
    /** Sortable, unique event id: millis(base36) + device + random. Used as the idempotency key. */
    fun eventId(dev: String, now: Instant = Instant.now()): String = now.toEpochMilli().toString(36).padStart(9, '0') + "-" + dev.take(6) + "-" + rid(6)
    fun uid(): String = "U" + rid(10)
}

object Hash {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun sha256(s: String): String = sha256(s.toByteArray(Charsets.UTF_8))
}

object Time {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    fun now(): String = Instant.now().toString()
    /** never throws: a bad time (only possible from an old/forged record) counts as today */
    fun day(iso: String): LocalDate = (runCatching { Instant.parse(iso) }.getOrNull() ?: Instant.now()).atZone(IST).toLocalDate()
    fun addDaysIso(iso: String, days: Long): String = day(iso).plusDays(days).toString()
    fun addMonthsIso(iso: String, months: Long): String = day(iso).plusMonths(months).toString()

    /** Working days between two instants (Mon–Sat; Sunday counts only when [sundayWork]). Original counts days the same way. */
    fun workingDays(fromIso: String, toIso: String, sundayWork: Boolean): Int {
        var d = day(fromIso); val end = day(toIso); var n = 0
        if (!end.isAfter(d)) return 0
        while (d.isBefore(end)) { d = d.plus(1, ChronoUnit.DAYS); if (sundayWork || d.dayOfWeek != DayOfWeek.SUNDAY) n++ }
        return n
    }
}

/** Distance in metres between two GPS points (original haversine, line 5303). */
fun haversine(a: GeoPoint, b: GeoPoint): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
    return 2 * r * asin(sqrt(h))
}

/** Last 10 digits of an Indian mobile number, or "" (original phone10). */
fun phone10(s: String?): String { val d = (s ?: "").filter { it.isDigit() }; return if (d.length >= 10) d.takeLast(10) else "" }

internal fun JsonElement.contentOrEmpty(): String = (this as? JsonPrimitive)?.content ?: ""
internal fun JsonElement.objOrNull(): JsonObject? = runCatching { jsonObject }.getOrNull()
internal fun JsonElement.arrOrEmpty(): JsonArray = runCatching { jsonArray }.getOrDefault(JsonArray(emptyList()))
internal fun JsonElement.primOrNull(): JsonPrimitive? = runCatching { jsonPrimitive }.getOrNull()
