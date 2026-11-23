package com.aiec.elevatoros.domain

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN hashing identical to the original app (pinHash, AIEC-Elevator-OS.html line 5352):
 * PBKDF2-HMAC-SHA256, 150,000 rounds, salt "aiec-pin2:<salt>", 256 bits, stored as "p2$<hex>".
 * Keeping the same scheme means a person's PIN record means the same thing in both apps.
 */
object Pin {
    const val ROUNDS = 150_000

    fun hash(pin: String, salt: String, rounds: Int = ROUNDS): String {
        val spec = PBEKeySpec(pin.toCharArray(), "aiec-pin2:$salt".toByteArray(Charsets.UTF_8), rounds, 256)
        val bits = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return "p2$" + bits.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, u: User): Boolean {
        val stored = u.pin ?: return false
        val salt = u.salt ?: return false
        if (!stored.startsWith("p2$")) return false
        return constantTimeEquals(hash(pin, salt), stored)
    }

    fun valid(pin: String) = pin.length in 4..6 && pin.all { it.isDigit() } && !isTrivial(pin)

    /** 1111, 1234, 0000 … are refused for real companies (practice mode keeps 1111 on purpose). */
    fun isTrivial(pin: String) = pin.toSet().size == 1 || "0123456789".contains(pin) || "9876543210".contains(pin)

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }
}
