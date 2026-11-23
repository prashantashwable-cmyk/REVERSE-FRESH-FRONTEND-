package com.aiec.elevatoros.domain

import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Practice company (the original's "Practice mode", every PIN 1111). It is built by running real actions through
 * the same rules engine — no shortcut, no fake state — and is stored in its own database, separate from live data.
 */
object Practice {
    const val PIN = "1111"
    val PEOPLE = listOf(
        Triple("P_HQ", "Anil Deshmukh", Role.OWNER), Triple("P_ADM", "Sunita Patil", Role.ADMIN),
        Triple("P_FLD", "Ramesh Jadhav", Role.FIELD), Triple("P_SAL", "Priya Kulkarni", Role.SALES),
        Triple("P_FIN", "Meena Joshi", Role.FINANCE), Triple("P_QC", "Vikas Shinde", Role.QC),
        Triple("P_SUP", "Kaveri Lift Supply", Role.SUPPLIER), Triple("P_TEC", "Raju Pawar", Role.TECH),
    )

    /** [pinHash] is Pin.hash(PIN, salt) computed once by the caller (it is deliberately slow). */
    fun build(pinHash: String, salt: String, dev: String, now: Instant = Instant.now()): List<Event> {
        val s = State(); val out = mutableListOf<Event>()
        // history starts 12 days ago so the escalation ladder and deadline bars have something to show
        var clock = now.minus(12, ChronoUnit.DAYS)
        var n = 0
        fun act(by: String, type: String, pid: String?, vararg d: Pair<String, Any?>) {
            clock = clock.plusSeconds(600)
            val ev = Event(Ids.eventId(dev, clock) + n++, type, by, pid, clock.toString(), dev, data = jsonOf(*d))
            val err = Rules.validate(s, ev)
            check(err == null) { "practice seed refused $type: $err" }
            Reducer.apply(s, ev); out.add(ev)
        }
        fun ev(kind: String, tag: String = "") = listOf(EvidenceRef("DEMO" + Ids.rid(8), kind, Hash.sha256(tag + n), clock.toString(), GeoPoint(18.5204, 73.8567, 15.0), tag))
        fun day(k: Long) { clock = clock.plus(k, ChronoUnit.DAYS) }

        act("P_HQ", "ORG_CREATED", null, "orgId" to "PRACTICE", "name" to "AIEC (Practice)")
        for ((uid, name, role) in PEOPLE) act("P_HQ", "USER_ADDED", null, "uid" to uid, "name" to name, "role" to role.key, "pin" to pinHash, "salt" to salt, "pinLen" to 4)

        val req = mapOf("type" to "auto", "stops" to 4, "capacity" to 6, "finish" to "ms", "door" to "auto", "power3" to true, "job" to "new")
        val checksOk = QC_FINAL_CHECKS.associateWith { "pass" }

        // 1. a site at Handover/AMC with an open fault ticket
        act("P_FLD", "LEAD_CREATED", "DEMO-1", "name" to "Mr. Deshmukh", "phone" to "9822000001", "address" to "Plot 12, Sinhagad Road, Pune", "floors" to 4, "gps" to GeoPoint(18.4800, 73.8200, 12.0), "ev" to ev("photo", "building"))
        act("P_SAL", "LEAD_ACCEPTED", "DEMO-1"); act("P_SAL", "CONTACT_LOGGED", "DEMO-1", "channel" to "call", "outcome" to "interested")
        act("P_SAL", "REQ_SAVED", "DEMO-1", "req" to req); act("P_SAL", "QUOTE_CREATED", "DEMO-1", "discountPct" to 0)
        act("P_SAL", "QUOTE_SENT", "DEMO-1", "channel" to "whatsapp"); act("P_SAL", "DEAL_WON", "DEMO-1")
        act("P_SAL", "AGREEMENT_PREPARED", "DEMO-1"); act("P_SAL", "AGREEMENT_SIGNED", "DEMO-1", "signer" to "Mr. Deshmukh", "ev" to ev("sign"))
        val p1 = s.projects.getValue("DEMO-1")
        act("P_SAL", "PAYMENT_RECORDED", "DEMO-1", "payId" to "D1T", "purpose" to "token", "amount" to p1.schedule.getValue("token"), "method" to "upi", "ref" to "UTR1001")
        act("P_FIN", "PAYMENT_STATUS", "DEMO-1", "payId" to "D1T", "status" to "confirmed", "ref" to "BANK1001")
        PREP_ITEMS.forEach { act("P_SAL", "PREP_ITEM_DONE", "DEMO-1", "item" to it.first, "ev" to ev("photo")) }
        act("P_SAL", "QC_REQUESTED", "DEMO-1")
        val okMeas = mapOf("width" to 1600, "depth" to 1600, "pit" to 1300, "head" to 3900)
        act("P_QC", "QC_SITE_RESULT", "DEMO-1", "outcome" to "approved", "meas" to okMeas, "ev" to ev("photo"))
        act("P_ADM", "RELEASED", "DEMO-1", "supplier" to "P_SUP", "tech" to "P_TEC", "drawings" to "DWG-101")
        act("P_SUP", "DISPATCHED", "DEMO-1", "seal" to "S-5521", "vehicle" to "MH12 AB 4410", "ev" to ev("photo"))
        act("P_TEC", "ARRIVED", "DEMO-1", "sealSeen" to "S-5521", "sealIntact" to "yes", "condition" to "ok", "ev" to ev("photo"))
        act("P_SAL", "PAYMENT_RECORDED", "DEMO-1", "payId" to "D1A", "purpose" to "arrival", "amount" to p1.schedule.getValue("arrival"), "method" to "bank", "ref" to "NEFT2001")
        act("P_FIN", "PAYMENT_STATUS", "DEMO-1", "payId" to "D1A", "status" to "confirmed", "ref" to "BANK2001")
        act("P_TEC", "MATERIAL_HANDED", "DEMO-1", "custName" to "Mr. Deshmukh", "ev" to ev("sign") + ev("sign") + ev("photo"))
        SOP_STEPS.indices.forEach { act("P_TEC", "SOP_STEP_DONE", "DEMO-1", "step" to it, "ev" to ev("photo")) }
        act("P_TEC", "INSPECTION_REQUESTED", "DEMO-1")
        act("P_QC", "QC_FINAL_RESULT", "DEMO-1", "outcome" to "pass", "checks" to checksOk, "ev" to ev("photo"))
        act("P_SAL", "PAYMENT_RECORDED", "DEMO-1", "payId" to "D1F", "purpose" to "final", "amount" to p1.schedule.getValue("final"), "method" to "cheque", "ref" to "CHQ3001")
        act("P_FIN", "PAYMENT_STATUS", "DEMO-1", "payId" to "D1F", "status" to "confirmed", "ref" to "BANK3001")
        act("P_TEC", "HANDOVER_SIGNED", "DEMO-1", "slot" to "customer", "signer" to "Mr. Deshmukh", "why" to "no_app", "ev" to ev("sign"))
        act("P_TEC", "HANDOVER_SIGNED", "DEMO-1", "slot" to "tech", "signer" to "Raju Pawar", "ev" to ev("sign"))
        act("P_QC", "HANDOVER_SIGNED", "DEMO-1", "slot" to "qc", "signer" to "Vikas Shinde", "ev" to ev("sign"))
        act("P_ADM", "HANDOVER_COMPLETED", "DEMO-1")
        act("P_ADM", "TICKET_OPENED", "DEMO-1", "tid" to "TK1", "urgency" to "stopped", "kind" to "breakdown", "note" to "Lift stops at 2nd floor")

        // 2. installation in progress (5 of 12 steps)
        day(2)
        act("P_FLD", "LEAD_CREATED", "DEMO-2", "name" to "Mrs. Kale", "phone" to "9822000002", "address" to "Shivaji Nagar, Pune", "floors" to 5, "gps" to GeoPoint(18.5300, 73.8470, 9.0), "ev" to ev("photo", "building"))
        act("P_SAL", "LEAD_ACCEPTED", "DEMO-2"); act("P_SAL", "CONTACT_LOGGED", "DEMO-2", "channel" to "visit", "outcome" to "interested")
        act("P_SAL", "REQ_SAVED", "DEMO-2", "req" to req + ("stops" to 5)); act("P_SAL", "QUOTE_CREATED", "DEMO-2", "discountPct" to 5, "reason" to "Referral")
        act("P_SAL", "QUOTE_SENT", "DEMO-2", "channel" to "in_person"); act("P_SAL", "DEAL_WON", "DEMO-2")
        act("P_SAL", "AGREEMENT_PREPARED", "DEMO-2"); act("P_SAL", "AGREEMENT_SIGNED", "DEMO-2", "signer" to "Mrs. Kale", "ev" to ev("sign"))
        val p2 = s.projects.getValue("DEMO-2")
        act("P_SAL", "PAYMENT_RECORDED", "DEMO-2", "payId" to "D2T", "purpose" to "token", "amount" to p2.schedule.getValue("token"), "method" to "upi", "ref" to "UTR1002")
        act("P_FIN", "PAYMENT_STATUS", "DEMO-2", "payId" to "D2T", "status" to "confirmed", "ref" to "BANK1002")
        PREP_ITEMS.forEach { act("P_SAL", "PREP_ITEM_DONE", "DEMO-2", "item" to it.first, "ev" to ev("photo")) }
        act("P_SAL", "QC_REQUESTED", "DEMO-2"); act("P_QC", "QC_SITE_RESULT", "DEMO-2", "outcome" to "approved", "meas" to okMeas, "ev" to ev("photo"))
        act("P_ADM", "RELEASED", "DEMO-2", "supplier" to "P_SUP", "tech" to "P_TEC", "drawings" to "DWG-102")
        act("P_SUP", "DISPATCHED", "DEMO-2", "seal" to "S-7710", "vehicle" to "MH14 CD 1201", "ev" to ev("photo"))
        act("P_TEC", "ARRIVED", "DEMO-2", "sealSeen" to "S-7710", "sealIntact" to "yes", "condition" to "ok", "ev" to ev("photo"))
        act("P_SAL", "PAYMENT_RECORDED", "DEMO-2", "payId" to "D2A", "purpose" to "arrival", "amount" to p2.schedule.getValue("arrival"), "method" to "bank", "ref" to "NEFT2002")
        act("P_FIN", "PAYMENT_STATUS", "DEMO-2", "payId" to "D2A", "status" to "confirmed", "ref" to "BANK2002")
        act("P_TEC", "MATERIAL_HANDED", "DEMO-2", "custName" to "Mrs. Kale", "ev" to ev("sign") + ev("sign") + ev("photo"))
        (0 until 5).forEach { act("P_TEC", "SOP_STEP_DONE", "DEMO-2", "step" to it, "ev" to ev("photo")) }

        // 3. a quote over the Sales discount limit waiting for Admin approval
        day(3)
        act("P_SAL", "LEAD_CREATED", "DEMO-3", "name" to "Shree Builders", "phone" to "9822000003", "address" to "Baner, Pune", "floors" to 10, "bType" to "residential")
        act("P_SAL", "LEAD_ACCEPTED", "DEMO-3"); act("P_SAL", "CONTACT_LOGGED", "DEMO-3", "channel" to "call", "outcome" to "interested")
        act("P_SAL", "REQ_SAVED", "DEMO-3", "req" to req + ("stops" to 10) + ("capacity" to 8) + ("finish" to "ss"))
        act("P_SAL", "QUOTE_CREATED", "DEMO-3", "discountPct" to 12, "reason" to "Two lifts in the same building")

        // 4. site preparation with the token payment waiting for Finance
        day(1)
        act("P_FLD", "LEAD_CREATED", "DEMO-4", "name" to "Mr. Gokhale", "phone" to "9822000004", "address" to "Kothrud, Pune", "floors" to 3, "gps" to GeoPoint(18.5074, 73.8077, 20.0), "ev" to ev("photo", "building"))
        act("P_SAL", "LEAD_ACCEPTED", "DEMO-4"); act("P_SAL", "CONTACT_LOGGED", "DEMO-4", "channel" to "call", "outcome" to "interested")
        act("P_SAL", "REQ_SAVED", "DEMO-4", "req" to req + ("stops" to 3)); act("P_SAL", "QUOTE_CREATED", "DEMO-4", "discountPct" to 0)
        act("P_SAL", "QUOTE_SENT", "DEMO-4", "channel" to "whatsapp"); act("P_SAL", "DEAL_WON", "DEMO-4")
        act("P_SAL", "AGREEMENT_PREPARED", "DEMO-4"); act("P_SAL", "AGREEMENT_SIGNED", "DEMO-4", "signer" to "Mr. Gokhale", "ev" to ev("sign"))
        act("P_SAL", "PAYMENT_RECORDED", "DEMO-4", "payId" to "D4T", "purpose" to "token", "amount" to s.projects.getValue("DEMO-4").schedule.getValue("token"), "method" to "upi", "ref" to "UTR1004")

        // 5. a brand-new lead from the field worker, waiting for Sales
        day(2)
        act("P_FLD", "LEAD_CREATED", "DEMO-5", "name" to "Mr. Patil", "phone" to "9822000005", "address" to "Hadapsar, Pune", "floors" to 4, "gps" to GeoPoint(18.5089, 73.9260, 25.0), "landmark" to "Near bus depot", "ev" to ev("photo", "building"))
        return out
    }
}
