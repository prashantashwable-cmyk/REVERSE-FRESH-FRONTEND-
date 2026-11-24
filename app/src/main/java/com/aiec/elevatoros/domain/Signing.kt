package com.aiec.elevatoros.domain

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Every record is signed by the phone that made it (ECDSA P-256 / SHA-256), as the original app did.
 * A phone accepts a record when the signature verifies with
 *   (a) its own key (records made on this phone), or
 *   (b) a key the office approved for the record's author (KEY_ADDED + KEY_APPROVED), or
 *   (c) for a KEY_ADDED record, the key it registers (the registration itself still needs approval to be trusted).
 * So a file edited by hand, or a record claiming to come from another person or phone, is refused.
 */
object Signing {
    /** The exact bytes that are signed: every field except the signature, data with keys sorted at every level. */
    fun payload(e: Event): ByteArray =
        listOf(e.id, e.type, e.by, e.pid ?: "", e.t, e.dev, e.v.toString(), e.kid, signedData(e.data)).joinToString("\n").toByteArray(Charsets.UTF_8)

    /** Fields that may be cut from a file for another phone. Each is signed by its fingerprint, so cutting it keeps the signature valid. */
    val PRIVATE_KEYS = setOf("pin", "salt")

    fun fingerprint(v: JsonElement): String = (v as? JsonObject)?.get("_cut")?.let { (it as? JsonPrimitive)?.content } ?: Hash.sha256(canonical(v))

    fun signedData(d: JsonObject): String = canonical(JsonObject(d.mapValues { (k, v) -> if (k in PRIVATE_KEYS) JsonPrimitive("#" + fingerprint(v)) else v }))

    /** The same record with private fields replaced by their fingerprint (signature still verifies). */
    fun cut(e: Event, keys: Set<String> = PRIVATE_KEYS): Event =
        e.copy(data = JsonObject(e.data.mapValues { (k, v) -> if (k in keys) JsonObject(mapOf("_cut" to JsonPrimitive(fingerprint(v)))) else v }))

    fun isCut(v: JsonElement?): Boolean = (v as? JsonObject)?.containsKey("_cut") == true

    fun canonical(x: JsonElement): String = when (x) {
        is JsonObject -> x.keys.sorted().joinToString(",", "{", "}") { k -> JsonPrimitive(k).toString() + ":" + canonical(x.getValue(k)) }
        is JsonArray -> x.joinToString(",", "[", "]") { canonical(it) }
        is JsonNull -> "null"
        is JsonPrimitive -> x.toString()
    }

    fun kidOf(pubB64: String): String = Hash.sha256(pubB64).take(16)

    fun publicKey(b64: String): PublicKey? = runCatching {
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(b64)))
    }.getOrNull()

    fun sign(e: Event, kid: String, key: PrivateKey): Event {
        val unsigned = e.copy(kid = kid, sig = "")
        val s = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(payload(unsigned)) }.sign()
        return unsigned.copy(sig = Base64.getEncoder().encodeToString(s))
    }

    fun verifies(e: Event, pub: PublicKey): Boolean = runCatching {
        Signature.getInstance("SHA256withECDSA").apply { initVerify(pub); update(payload(e.copy(sig = ""))) }
            .verify(Base64.getDecoder().decode(e.sig))
    }.getOrDefault(false)

    /** Approved later in the history: trusted for the person's earlier records too, after verifying the signature. */
    fun checkWithLaterApproval(e: Event, approved: Map<Pair<String, String>, String>): Boolean =
        approved[e.by to e.kid]?.let { pubB64 -> publicKey(pubB64)?.let { verifies(e, it) } } == true

    /** This phone's own key, for (a). */
    class Own(val kid: String, val pub: PublicKey)

    /** null = trusted; otherwise why the record is refused. Runs before the business rules. */
    fun check(s: State, e: Event, own: Own?): Err? {
        if (e.sig.isBlank() || e.kid.isBlank()) return Err("err_unsigned")
        if (own != null && e.kid == own.kid) return if (verifies(e, own.pub)) null else Err("err_signature")
        // the company record carries the founding phone's key: it defines the company, as a new phone's first file did in the original
        if (e.type == "ORG_CREATED" && e.data.str("pub").isNotEmpty()) {
            val pub = publicKey(e.data.str("pub")) ?: return Err("err_signature")
            return if (e.kid == kidOf(e.data.str("pub")) && e.data.str("kid") == e.kid && verifies(e, pub)) null else Err("err_signature")
        }
        if (e.type == "KEY_ADDED") {
            val pubB64 = e.data.str("pub"); val pub = publicKey(pubB64) ?: return Err("err_signature")
            return if (e.kid == kidOf(pubB64) && verifies(e, pub)) null else Err("err_signature")
        }
        val reg = s.keys[e.by]?.firstOrNull { it.kid == e.kid } ?: (if (e.kid == s.foundingKey?.kid && s.users.isEmpty()) s.foundingKey else null) ?: return Err("err_key_unknown")
        if (!reg.ok) return Err("err_key_pending")
        val pub = publicKey(reg.pub) ?: return Err("err_signature")
        return if (verifies(e, pub)) null else Err("err_signature")
    }
}
