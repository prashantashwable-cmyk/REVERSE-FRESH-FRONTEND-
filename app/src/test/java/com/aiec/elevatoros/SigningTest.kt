package com.aiec.elevatoros

import com.aiec.elevatoros.domain.Event
import com.aiec.elevatoros.domain.Reducer
import com.aiec.elevatoros.domain.Signing
import com.aiec.elevatoros.domain.Stage
import com.aiec.elevatoros.domain.jsonOf
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.Base64

/** Records signed per phone (06 C1): only the office-approved phone of the author can make a record other phones accept. */
class SigningTest {
    private class Phone(val dev: String) {
        val kp: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val pubB64: String = Base64.getEncoder().encodeToString(kp.public.encoded)
        val kid = Signing.kidOf(pubB64)
        val own = Signing.Own(kid, kp.public)
        fun sign(e: Event) = Signing.sign(e, kid, kp.private)
    }

    private val office = Phone("OFFICE"); private val salesPhone = Phone("SALESPH"); private val attacker = Phone("EVIL")
    private var clock = Instant.parse("2026-11-02T04:30:00Z"); private var n = 0
    private fun ev(p: Phone, by: String, type: String, pid: String?, vararg d: Pair<String, Any?>, dev: String = p.dev): Event {
        clock = clock.plusSeconds(600)
        return p.sign(Event("E${n++}".padStart(6, '0'), type, by, pid, clock.toString(), dev, data = jsonOf(*d)))
    }
    private fun keyAdded(p: Phone, by: String) = ev(p, by, "KEY_ADDED", null, "kid" to p.kid, "pub" to p.pubB64, "devName" to p.dev)

    private fun company(): MutableList<Event> = mutableListOf(
        ev(office, "hq", "ORG_CREATED", null, "orgId" to "O", "name" to "AIEC", "kid" to office.kid, "pub" to office.pubB64, "devName" to "OFFICE"),
        ev(office, "hq", "USER_ADDED", null, "uid" to "hq", "name" to "HQ", "role" to "owner", "pin" to "p2\$a", "salt" to "s"),
        ev(office, "hq", "USER_ADDED", null, "uid" to "sal", "name" to "Priya", "role" to "sales", "pin" to "p2\$b", "salt" to "t"),
    )

    private fun rebuildOn(p: Phone, events: List<Event>, rejected: MutableList<String> = mutableListOf()) =
        Reducer.rebuild(events.sortedWith(compareBy({ it.t }, { it.id })), { s, e -> Signing.check(s, e, p.own) }) { e, err -> rejected.add(e.type + ":" + err.key) }

    @Test fun approvedPhoneIsTrustedAlsoForRecordsMadeBeforeApproval() {
        val evs = company()
        evs += keyAdded(salesPhone, "sal")
        evs += ev(salesPhone, "sal", "LEAD_CREATED", "P", "name" to "Kale", "phone" to "9822000001", "address" to "Pune")
        val before = mutableListOf<String>()
        val s1 = rebuildOn(office, evs, before)
        assertTrue("pending phone must not be trusted yet: $before", before.contains("LEAD_CREATED:err_key_pending"))
        assertTrue(s1.projects.isEmpty())
        evs += ev(office, "hq", "KEY_APPROVED", null, "uid" to "sal", "kid" to salesPhone.kid)
        val after = mutableListOf<String>()
        val s2 = rebuildOn(office, evs, after)
        assertEquals(emptyList<String>(), after)
        assertEquals(Stage.NEW, s2.projects.getValue("P").stage)
    }

    @Test fun editedForgedSpoofedAndUnsignedRecordsAreRefused() {
        val evs = company()
        evs += keyAdded(salesPhone, "sal"); evs += ev(office, "hq", "KEY_APPROVED", null, "uid" to "sal", "kid" to salesPhone.kid)
        val good = ev(salesPhone, "sal", "LEAD_CREATED", "P", "name" to "Kale", "phone" to "9822000001", "address" to "Pune")
        // edited after signing (phone number changed)
        val edited = good.copy(id = "EDIT", data = JsonObject(good.data + ("phone" to JsonPrimitive("9000000000"))))
        // forged "by Head Quarter" with the attacker's key, pretending to come from the office phone
        val forged = ev(attacker, "hq", "USER_ADDED", null, "uid" to "evil", "name" to "Evil", "role" to "admin", "pin" to "p2\$c", "salt" to "u", dev = "OFFICE")
        // the attacker registers a key for Head Quarter: it is only pending, so it cannot make HQ records
        val sneakyKey = keyAdded(attacker, "hq")
        val forged2 = ev(attacker, "hq", "CONFIG_SET", null, "key" to "gst", "value" to "0")
        val unsigned = Event("UNS", "LEAD_CREATED", "sal", "Q", clock.plusSeconds(1).toString(), "SALESPH", data = jsonOf("name" to "A", "phone" to "9822000002", "address" to "x"))
        val rejected = mutableListOf<String>()
        val s = rebuildOn(office, evs + good + edited + forged + sneakyKey + forged2 + unsigned, rejected)
        assertEquals(listOf("LEAD_CREATED:err_signature", "USER_ADDED:err_key_unknown", "CONFIG_SET:err_key_pending", "LEAD_CREATED:err_unsigned"), rejected)
        assertTrue(s.users["evil"] == null)
        assertEquals(18.0, s.config.gst, 0.0)
        assertEquals(1, s.projects.size)
    }

    @Test fun cuttingPinFieldsKeepsTheSignatureValid() {
        val base = company().take(2)
        val e = ev(office, "hq", "USER_ADDED", null, "uid" to "sal", "name" to "Priya", "role" to "sales", "pin" to "p2\$secret", "salt" to "t")
        val cut = Signing.cut(e)
        assertTrue(Signing.verifies(cut, office.kp.public))
        assertTrue(!cut.data.toString().contains("secret"))
        // a cut record is still accepted by the rules, as a person who does not log in on that phone
        val evs = base + cut
        val rejected = mutableListOf<String>()
        val s = rebuildOn(salesPhone, evs, rejected)
        assertEquals(emptyList<String>(), rejected)
        assertEquals("", s.users.getValue("sal").pin)
    }

    @Test fun ownPhoneRecordsAreTrustedWithoutApproval() {
        val evs = company()
        evs += keyAdded(salesPhone, "sal")
        evs += ev(salesPhone, "sal", "LEAD_CREATED", "P", "name" to "Kale", "phone" to "9822000001", "address" to "Pune")
        // the office's history arrives on the Sales phone signed by the office key, which the company approved first
        val rejected = mutableListOf<String>()
        val s = rebuildOn(salesPhone, evs, rejected)
        assertEquals(emptyList<String>(), rejected)
        assertNull(Signing.check(s, evs.last(), salesPhone.own))
    }
}
