package com.aiec.elevatoros

import com.aiec.elevatoros.domain.Err
import com.aiec.elevatoros.domain.Event
import com.aiec.elevatoros.domain.EvidenceRef
import com.aiec.elevatoros.domain.GeoPoint
import com.aiec.elevatoros.domain.Pin
import com.aiec.elevatoros.domain.Pricing
import com.aiec.elevatoros.domain.Reducer
import com.aiec.elevatoros.domain.Requirements
import com.aiec.elevatoros.domain.Rules
import com.aiec.elevatoros.domain.SOP_STEPS
import com.aiec.elevatoros.domain.Stage
import com.aiec.elevatoros.domain.State
import com.aiec.elevatoros.domain.Tasks
import com.aiec.elevatoros.domain.jsonOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** A tiny in-memory phone: every action passes Rules.validate then Reducer.apply, exactly like the app. */
class Harness {
    val s = State()
    val events = mutableListOf<Event>()
    private var clock = Instant.parse("2026-11-02T04:30:00Z") // a Monday, 10:00 IST
    private var n = 0

    fun tick(hours: Long = 1) { clock = clock.plusSeconds(hours * 3600) }

    fun act(by: String, type: String, pid: String? = null, vararg data: Pair<String, Any?>): Err? {
        tick()
        val ev = Event("E" + (n++).toString().padStart(5, '0'), type, by, pid, clock.toString(), "TESTDEV", data = jsonOf(*data))
        val err = Rules.validate(s, ev)
        if (err == null) { Reducer.apply(s, ev); events.add(ev) }
        return err
    }

    fun ok(by: String, type: String, pid: String? = null, vararg data: Pair<String, Any?>) {
        val e = act(by, type, pid, *data)
        assertNull("$type by $by refused: $e", e)
    }

    fun photo(tag: String = "") = listOf(EvidenceRef("P${n}x$tag", "photo", "a".repeat(64), clock.toString(), GeoPoint(18.5, 73.8, 12.0), tag))
    fun sign(k: Int = 1) = (1..k).map { EvidenceRef("S${n}x$it", "sign", "b".repeat(64), clock.toString()) }

    fun boot() {
        ok("hq", "ORG_CREATED", null, "orgId" to "ORG1", "name" to "AIEC")
        ok("hq", "USER_ADDED", null, "uid" to "hq", "name" to "Anil", "role" to "owner", "pin" to "p2\$x", "salt" to "s")
        for ((uid, role) in listOf("adm" to "admin", "fld" to "field", "sal" to "sales", "fin" to "finance", "qc" to "qc", "sup" to "supplier", "tec" to "tech"))
            ok("hq", "USER_ADDED", null, "uid" to uid, "name" to uid.uppercase(), "role" to role, "pin" to "p2\$x", "salt" to "s")
    }
}

class EngineTest {
    @Test fun pinHashMatchesOriginalScheme() {
        // Same algorithm as the web app: PBKDF2-SHA256, salt "aiec-pin2:" + salt. Fewer rounds here only to keep the test fast.
        val h = Pin.hash("1111", "abc", rounds = 1000)
        assertTrue(h.startsWith("p2$")); assertEquals(3 + 64, h.length)
        assertEquals(h, Pin.hash("1111", "abc", rounds = 1000))
        assertTrue(h != Pin.hash("1112", "abc", rounds = 1000))
        assertTrue(Pin.isTrivial("1111")); assertTrue(Pin.isTrivial("1234")); assertTrue(!Pin.isTrivial("4831"))
    }

    @Test fun quoteMathsMatchesOriginal() {
        // Original calcQuote with DEFAULT_CONFIG: auto lift, 4 stops, 6 persons, MS finish, new job, 5% discount.
        val c = Pricing.quote(com.aiec.elevatoros.domain.Config(), Requirements(stops = 4, capacity = 6), 5.0)
        // cost = 300000 + 80000 + 20000 + 0 + 60000 + 15000 = 475000; k = 1/(1-0.6) = 2.5
        assertEquals(475000L, c.cost)
        assertEquals(listOf(750000L, 200000L, 50000L, 150000L, 37500L), c.lines.map { it.second })
        assertEquals(1187500L, c.list)
        assertEquals(59400L, c.discount) // r100(59375)
        assertEquals(1128100L, c.price)
        assertEquals(203058L, c.gst)
        assertEquals(1331158L, c.total)
        assertTrue(c.why.isEmpty())
        assertEquals("₹13,31,158", Pricing.money(c.total))
        val sch = Pricing.schedule(com.aiec.elevatoros.domain.Config(), c.total)
        assertEquals(10000L, sch["token"]); assertEquals(1198042L - 10000L, sch["arrival"]); assertEquals(c.total, sch.values.sum())
    }

    @Test fun fullLeadToHandoverWithSeparationOfDuties() {
        val h = Harness(); h.boot()
        val pid = "P1"
        // field worker must add a building photo
        assertEquals("err_photos", h.act("fld", "LEAD_CREATED", pid, "name" to "Mr Kale", "phone" to "9876543210", "address" to "Plot 12")?.key)
        h.ok("fld", "LEAD_CREATED", pid, "name" to "Mr Kale", "phone" to "+91 98765 43210", "address" to "Plot 12, Sinhagad Rd", "floors" to 4, "ev" to h.photo("building"))
        val p = h.s.projects.getValue(pid)
        assertEquals("9876543210", p.contactPhone)
        assertTrue(p.no.startsWith("AIEC-PUN-2026-00001"))
        // field cannot accept their own lead
        assertEquals("err_role", h.act("fld", "LEAD_ACCEPTED", pid)?.key)
        // the task belongs to Sales, and Sales sees it
        assertTrue(Tasks.tasksFor(h.s, h.s.users.getValue("sal"), h.s.byId.values.last().t).any { it.need.key == "check_lead" })
        h.ok("sal", "LEAD_ACCEPTED", pid)
        h.ok("sal", "CONTACT_LOGGED", pid, "channel" to "call", "outcome" to "interested")
        assertEquals(Stage.CONTACTED, p.stage)
        h.ok("sal", "REQ_SAVED", pid, "req" to mapOf("type" to "auto", "stops" to 4, "capacity" to 6, "finish" to "ms", "door" to "auto", "power3" to true, "job" to "new"))
        // 12% discount is over the Sales limit → needs approval by someone else
        h.ok("sal", "QUOTE_CREATED", pid, "discountPct" to 12, "reason" to "builder volume")
        assertEquals(Stage.QUOTE_APPROVAL, p.stage)
        assertEquals("err_role", h.act("sal", "QUOTE_DECISION", pid, "decision" to "approve")?.key)
        h.ok("adm", "QUOTE_DECISION", pid, "decision" to "approve")
        h.ok("sal", "QUOTE_SENT", pid, "channel" to "whatsapp")
        h.ok("sal", "DEAL_WON", pid)
        assertEquals(Stage.WON, p.stage)
        h.ok("sal", "AGREEMENT_PREPARED", pid)
        h.ok("sal", "AGREEMENT_SIGNED", pid, "signer" to "Mr Kale", "ev" to h.sign())
        // token: Sales records, Sales cannot confirm, Finance confirms
        val token = p.schedule.getValue("token")
        h.ok("sal", "PAYMENT_RECORDED", pid, "payId" to "PAY1", "purpose" to "token", "amount" to token, "method" to "upi", "ref" to "UTR123")
        assertEquals("err_pay_exists", h.act("sal", "PAYMENT_RECORDED", pid, "payId" to "PAY2", "purpose" to "token", "amount" to token, "method" to "upi", "ref" to "UTR9")?.key)
        assertEquals("err_role", h.act("sal", "PAYMENT_STATUS", pid, "payId" to "PAY1", "status" to "confirmed", "ref" to "x")?.key)
        h.ok("fin", "PAYMENT_STATUS", pid, "payId" to "PAY1", "status" to "confirmed", "ref" to "BANK-1")
        assertEquals(Stage.SITE_PREP, p.stage)
        // QC cannot be requested before every prep item has a photo
        assertEquals("err_prep_open", h.act("sal", "QC_REQUESTED", pid)?.key)
        for (item in listOf("shaft", "pit", "power", "clean", "light", "barricade")) h.ok("sal", "PREP_ITEM_DONE", pid, "item" to item, "ev" to h.photo())
        h.ok("sal", "QC_REQUESTED", pid)
        // shaft below the minimum cannot simply be approved
        val low = mapOf("width" to 1400, "depth" to 1600, "pit" to 1300, "head" to 3900)
        assertEquals("err_shaft_low", h.act("qc", "QC_SITE_RESULT", pid, "outcome" to "approved", "meas" to low, "ev" to h.photo())?.key)
        h.ok("qc", "QC_SITE_RESULT", pid, "outcome" to "corrections", "meas" to low, "items" to listOf("Widen shaft by 100 mm"), "ev" to h.photo())
        assertEquals(Stage.SITE_FIX, p.stage)
        val cid = p.corrections.single().id
        h.ok("sal", "CORRECTION_DONE", pid, "cid" to cid, "ev" to h.photo())
        h.ok("sal", "QC_REQUESTED", pid)
        h.ok("qc", "QC_SITE_RESULT", pid, "outcome" to "approved", "meas" to mapOf("width" to 1600, "depth" to 1600, "pit" to 1300, "head" to 3900), "ev" to h.photo())
        assertEquals(Stage.SITE_OK, p.stage)
        h.ok("adm", "RELEASED", pid, "supplier" to "sup", "tech" to "tec", "drawings" to "DWG-7")
        h.ok("sup", "DISPATCHED", pid, "seal" to "SEAL-44", "vehicle" to "MH12AB1234", "ev" to h.photo())
        h.ok("tec", "ARRIVED", pid, "sealSeen" to "seal-44", "sealIntact" to "yes", "condition" to "ok", "ev" to h.photo())
        assertTrue(p.incidents.isEmpty())
        // handover is blocked until the arrival payment is confirmed
        assertEquals("err_wait_payment", h.act("tec", "MATERIAL_HANDED", pid, "custName" to "Mr Kale", "ev" to h.sign(2) + h.photo())?.key)
        h.ok("sal", "PAYMENT_RECORDED", pid, "payId" to "PAY3", "purpose" to "arrival", "amount" to p.schedule.getValue("arrival"), "method" to "bank", "ref" to "NEFT77")
        h.ok("fin", "PAYMENT_STATUS", pid, "payId" to "PAY3", "status" to "confirmed", "ref" to "BANK-2")
        h.ok("tec", "MATERIAL_HANDED", pid, "custName" to "Mr Kale", "ev" to h.sign(2) + h.photo())
        assertEquals(Stage.INSTALLING, p.stage)
        // steps strictly in order, each with a photo
        assertEquals("err_step_order", h.act("tec", "SOP_STEP_DONE", pid, "step" to 3, "ev" to h.photo())?.key)
        for (i in SOP_STEPS.indices) h.ok("tec", "SOP_STEP_DONE", pid, "step" to i, "ev" to h.photo())
        h.ok("tec", "INSPECTION_REQUESTED", pid)
        val checks = mapOf("ride" to "pass", "level" to "pass", "doors" to "pass", "alarm" to "pass", "plate" to "pass", "safetydev" to "pass", "wiring" to "pass")
        assertEquals("err_pass_with_fail", h.act("qc", "QC_FINAL_RESULT", pid, "outcome" to "pass", "checks" to checks + ("doors" to "fail"), "ev" to h.photo())?.key)
        h.ok("qc", "QC_FINAL_RESULT", pid, "outcome" to "fail", "checks" to checks + ("doors" to "fail"), "items" to listOf("Door gap"), "ev" to h.photo())
        assertEquals(Stage.INSTALL_FIX, p.stage)
        h.ok("tec", "CORRECTION_DONE", pid, "cid" to p.corrections.last().id, "ev" to h.photo())
        h.ok("tec", "INSPECTION_REQUESTED", pid)
        h.ok("qc", "QC_FINAL_RESULT", pid, "outcome" to "pass", "checks" to checks, "ev" to h.photo())
        assertEquals(Stage.FINAL_PAY, p.stage)
        h.ok("sal", "PAYMENT_RECORDED", pid, "payId" to "PAY4", "purpose" to "final", "amount" to p.schedule.getValue("final"), "method" to "cheque", "ref" to "CHQ-1")
        h.ok("fin", "PAYMENT_STATUS", pid, "payId" to "PAY4", "status" to "confirmed", "ref" to "BANK-3")
        assertEquals(Stage.HANDOVER, p.stage)
        assertEquals("err_signatures_open", h.act("adm", "HANDOVER_COMPLETED", pid)?.key)
        h.ok("tec", "HANDOVER_SIGNED", pid, "slot" to "customer", "signer" to "Mr Kale", "why" to "no_app", "ev" to h.sign())
        h.ok("tec", "HANDOVER_SIGNED", pid, "slot" to "tech", "signer" to "TEC", "ev" to h.sign())
        h.ok("qc", "HANDOVER_SIGNED", pid, "slot" to "qc", "signer" to "QC", "ev" to h.sign())
        h.ok("adm", "HANDOVER_COMPLETED", pid)
        assertEquals(Stage.AMC, p.stage)
        assertNotNull(p.liftCode); assertEquals(4, p.amcVisits.size)
        // service ticket cycle
        h.ok("adm", "TICKET_OPENED", pid, "tid" to "T1", "urgency" to "stopped", "note" to "Lift stuck at 2nd floor")
        h.ok("adm", "TICKET_ASSIGNED", pid, "tid" to "T1", "uid" to "tec")
        h.ok("tec", "TICKET_UPDATED", pid, "tid" to "T1", "note" to "Door contact cleaned", "ev" to h.photo())
        h.ok("adm", "TICKET_CLOSED", pid, "tid" to "T1", "ok" to true)
        assertEquals("closed", p.tickets.single().status)

        // the same history rebuilds to the same state (event sourcing), and a duplicate import is applied once
        val again = Reducer.rebuild(h.events + h.events)
        assertEquals(Stage.AMC, again.projects.getValue(pid).stage)
        assertEquals(p.hist.map { it.stage }, again.projects.getValue(pid).hist.map { it.stage })
    }

    @Test fun ownerCoversEmptyOfficeRolesButNeverPhysicalWork() {
        val h = Harness()
        h.ok("hq", "ORG_CREATED", null, "orgId" to "O", "name" to "Small")
        h.ok("hq", "USER_ADDED", null, "uid" to "hq", "name" to "HQ", "role" to "owner", "pin" to "p2\$x", "salt" to "s")
        h.ok("hq", "USER_ADDED", null, "uid" to "tec", "name" to "T", "role" to "tech", "pin" to "p2\$x", "salt" to "s")
        h.ok("hq", "LEAD_CREATED", "P", "name" to "A", "phone" to "9999988888", "address" to "x")
        h.ok("hq", "LEAD_ACCEPTED", "P") // no Sales, no Admin: HQ covers
        assertTrue(Rules.allowed(h.s, "hq", "CONTACT_LOGGED", "P"))
        assertTrue(!Rules.allowed(h.s, "hq", "SOP_STEP_DONE", "P")) // physical work is never covered
    }

    @Test fun forgedEventsAreRefusedOnRebuild() {
        val h = Harness(); h.boot()
        h.ok("sal", "LEAD_CREATED", "P", "name" to "A", "phone" to "9999988888", "address" to "x")
        val forged = Event("ZZZ", "DEAL_WON", "fld", "P", "2026-11-10T00:00:00Z", "EVIL")
        val rejected = mutableListOf<String>()
        val s = Reducer.rebuild(h.events + forged) { ev, _ -> rejected.add(ev.id) }
        assertEquals(listOf("ZZZ"), rejected)
        assertEquals(Stage.NEW, s.projects.getValue("P").stage)
        assertEquals("err_newer_app", Rules.validate(h.s, forged.copy(v = 99))?.key)
    }

    @Test fun escalationLadderUsesWorkingDays() {
        val h = Harness(); h.boot()
        h.ok("sal", "LEAD_CREATED", "P", "name" to "A", "phone" to "9999988888", "address" to "x")
        val p = h.s.projects.getValue("P")
        // created Monday; Thursday = 3 working days → Admin level; next Monday (Sunday off) = 6 → still level 2; Tuesday = 7 → HQ
        assertEquals(2, Tasks.escalation(h.s, p, "2026-11-05T06:00:00Z").second)
        assertEquals(2, Tasks.escalation(h.s, p, "2026-11-09T06:00:00Z").second)
        assertEquals(3, Tasks.escalation(h.s, p, "2026-11-10T06:00:00Z").second)
    }

    @Test fun practiceCompanyIsBuiltThroughTheRules() {
        val evs = com.aiec.elevatoros.domain.Practice.build("p2\$x", "salt", "DEV1")
        val rejected = mutableListOf<String>()
        val s = Reducer.rebuild(evs) { ev, err -> rejected.add(ev.type + ":" + err.key) }
        assertEquals(emptyList<String>(), rejected)
        assertEquals(Stage.AMC, s.projects.getValue("DEMO-1").stage)
        assertEquals(Stage.INSTALLING, s.projects.getValue("DEMO-2").stage)
        assertEquals(Stage.QUOTE_APPROVAL, s.projects.getValue("DEMO-3").stage)
        assertEquals(Stage.SIGNED, s.projects.getValue("DEMO-4").stage)
        assertEquals(Stage.NEW, s.projects.getValue("DEMO-5").stage)
        assertTrue(evs.zipWithNext().all { (a, b) -> a.t <= b.t })
    }
}
