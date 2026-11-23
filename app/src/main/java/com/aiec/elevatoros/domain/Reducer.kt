package com.aiec.elevatoros.domain

/* Event → state (port of apply(), original lines 8347–8866, for the event types this version supports). */
object Reducer {
    private fun Project.setStage(s: Stage, ev: Event) {
        if (stage == s) return
        stage = s; since = ev.t; hist.add(StageMark(s, ev.t, ev.by))
    }

    fun newJobNo(s: State, t: String): String {
        val pre = "AIEC-" + s.config.cityCode + "-" + Time.day(t).year + "-"
        val max = s.jobNos.keys.filter { it.startsWith(pre) }.maxOfOrNull { it.removePrefix(pre).take(5).toIntOrNull() ?: 0 } ?: 0
        return pre + (max + 1).toString().padStart(5, '0')
    }

    fun apply(s: State, ev: Event) {
        val d = ev.data
        s.byId[ev.id] = ev
        val p: Project? = ev.pid?.let { s.projects[it] }
        when (ev.type) {
            "ORG_CREATED" -> s.org = Org(d.str("orgId"), d.str("name"), ev.t)
            "USER_ADDED" -> {
                val role = Role.of(d.str("role"))!!
                s.users[d.str("uid")] = User(
                    uid = d.str("uid"), name = d.str("name").trim(), role = role, pin = d.str("pin"), salt = d.str("salt"),
                    pinLen = d.int("pinLen") ?: 4, phone = phone10(d.str("phone")), projectId = d.strOrNull("projectId"), t = ev.t, by = ev.by
                )
                if (role == Role.CUSTOMER) s.projects[d.str("projectId")]?.customer = d.str("uid")
            }
            "USER_UPDATED" -> s.users[d.str("uid")]?.let { x ->
                if (d.containsKey("name") && d.str("name").isNotBlank()) x.name = d.str("name").trim()
                if (d.containsKey("active")) x.active = d.bool("active")
                if (d.containsKey("pin") && d.str("pin").isNotBlank()) { x.pin = d.str("pin"); x.salt = d.str("salt"); x.pinLen = d.int("pinLen") ?: x.pinLen }
                if (d.containsKey("role")) Role.of(d.str("role"))?.let { x.role = it }
            }
            "CONFIG_SET" -> s.config = s.config.with(d.str("key"), d.str("value"))
            "LEAD_CREATED" -> {
                var no = d.str("no").ifBlank { newJobNo(s, ev.t) }
                // two phones offline may make the same number: the later one in the shared history gets -B, -C ... (same on every phone)
                var k = 0; val base = no
                while (s.jobNos.containsKey(no)) { no = base + "-" + (if (k < 23) "BCDEFGHJKLMNPQRSTUVWXYZ"[k].toString() else (k + 2).toString()); k++ }
                val np = Project(ev.pid!!, no, ev.t, ev.by).apply {
                    address = d.str("address").trim(); landmark = d.str("landmark").trim(); gps = d.gps(); tower = d.str("tower").trim()
                    noPhone = d.bool("noPhone"); contactName = d.str("name").trim(); contactPhone = if (noPhone) "" else phone10(d.str("phone"))
                    floors = d.int("floors"); shaft = d.str("shaft").ifBlank { "unknown" }; bType = d.str("bType"); note = d.str("note").trim()
                    dupOf = d.strList("dupOf")
                }
                s.projects[np.id] = np; s.jobNos[no] = np.id
                np.log.add(ev.id); np.evidence.addAll(d.evidence())
                return
            }
            "SOS_RAISED" -> s.sos[d.str("sid")] = SosAlert(d.str("sid"), ev.by, ev.t, d.gps(), d.strOrNull("pid"))
            "SOS_CLOSED" -> s.sos[d.str("sid")]?.let { it.open = false; it.closedBy = ev.by }
        }
        if (p == null) return
        if (s.roleOf(ev.by) == Role.SALES && p.sales != ev.by && s.users[p.sales]?.let { !it.active || it.role != Role.SALES } != false && ev.type != "LEAD_ACCEPTED") p.sales = ev.by
        p.log.add(ev.id)
        p.evidence.addAll(d.evidence())
        when (ev.type) {
            "LEAD_ACCEPTED" -> { p.sales = d.strOrNull("salesUid") ?: ev.by; p.returnedReason = null; p.setStage(Stage.ACCEPTED, ev) }
            "LEAD_RETURNED" -> { p.returnedReason = d.str("reason"); p.setStage(Stage.RETURNED, ev) }
            "LEAD_RESUBMITTED" -> { p.note = listOf(p.note, d.str("note")).filter { it.isNotBlank() }.joinToString(" | "); p.setStage(Stage.NEW, ev) }
            "CONTACT_LOGGED" -> {
                val outcome = d.str("outcome")
                p.contacts.add(Contact(ev.t, ev.by, d.str("channel"), outcome, d.str("note")))
                if (outcome == "interested" && p.stage == Stage.ACCEPTED) p.setStage(Stage.CONTACTED, ev)
                if (d.str("next").isNotBlank()) p.followUp = d.str("next") else if (outcome == "interested") p.followUp = null
                if (outcome == "not_interested") { p.lostReason = d.str("reason"); p.followUp = null; p.setStage(Stage.LOST, ev) }
            }
            "REQ_SAVED" -> {
                val r = d.obj("req")!!
                p.req = Requirements(
                    type = r.str("type"), stops = r.int("stops") ?: 2, capacity = r.int("capacity") ?: 6, finish = r.str("finish"),
                    door = r.str("door").ifBlank { "auto" }, power3 = r.bool("power3"), job = r.str("job").ifBlank { "new" }
                )
                p.materials = Pricing.materials(p.req!!)
                if (p.stage == Stage.CONTACTED) p.setStage(Stage.REQUIREMENTS, ev)
            }
            "QUOTE_CREATED" -> {
                val c = Pricing.quote(s.config, p.req!!, d.num("discountPct") ?: 0.0)
                val ownerOk = s.roleOf(ev.by) == Role.OWNER && c.margin >= s.config.minMargin
                val needs = c.needsApproval && !ownerOk
                p.quotes.add(
                    Quote(
                        v = p.quotes.size + 1, t = ev.t, by = ev.by, discountPct = c.discountPct, list = c.list, discount = c.discount,
                        price = c.price, gstPct = c.gstPct, gst = c.gst, total = c.total, cost = c.cost, margin = c.margin, why = c.why,
                        status = if (needs) "pending" else "auto", reason = d.str("reason"), validity = s.config.validityDays, lines = c.lines
                    )
                )
                p.setStage(if (needs) Stage.QUOTE_APPROVAL else Stage.QUOTE_READY, ev)
            }
            "QUOTE_DECISION" -> {
                val q = p.lastQuote()!!; val ok = d.str("decision") == "approve"
                q.status = if (ok) "approved" else "rejected"; q.decision = Triple(ev.by, ev.t, d.str("reason"))
                p.setStage(if (ok) Stage.QUOTE_READY else Stage.REQUIREMENTS, ev)
            }
            "QUOTE_SENT" -> { p.lastQuote()!!.sentT = ev.t; p.setStage(Stage.QUOTED, ev) }
            "DEAL_WON" -> {
                val q = p.lastQuote()!!; q.acceptedT = ev.t
                p.schedule = Pricing.schedule(s.config, q.total); p.followUp = null; p.setStage(Stage.WON, ev)
            }
            "DEAL_LOST" -> { p.lostReason = d.str("reason"); p.followUp = null; p.setStage(Stage.LOST, ev) }
            "AGREEMENT_PREPARED" -> { p.agreementPrepared = Done(ev.t, ev.by); p.setStage(Stage.AGREEMENT, ev) }
            "AGREEMENT_SIGNED" -> {
                p.agreementSigned = Signature("customer", d.str("signer").trim(), ev.t, ev.by, d.evidence().first { it.kind == "sign" }.id)
                p.setStage(Stage.SIGNED, ev)
            }
            "PAYMENT_RECORDED" -> p.payments.add(
                Payment(d.str("payId"), d.str("purpose"), (d.num("amount") ?: 0.0).toLong(), d.str("method"), d.str("ref").trim(), ev.by, ev.t)
            )
            "PAYMENT_STATUS" -> {
                val pay = p.payments.first { it.id == d.str("payId") }
                pay.status = d.str("status"); pay.reason = d.str("reason")
                if (pay.status == "confirmed") {
                    pay.confirmedBy = ev.by; pay.confirmedAt = ev.t
                    if (pay.purpose == "token" && p.stage == Stage.SIGNED) p.setStage(Stage.SITE_PREP, ev)
                    if (pay.purpose == "final" && p.stage == Stage.FINAL_PAY) p.setStage(Stage.HANDOVER, ev)
                }
                // "not_found": the payment stays in the record with the reason; a new payment can be reported again
            }
            "PREP_ITEM_DONE" -> p.prep[d.str("item")] = Done(ev.t, ev.by, d.str("note"), d.evidence().map { it.id })
            "QC_REQUESTED" -> p.setStage(Stage.QC_SITE, ev)
            "QC_SITE_RESULT" -> {
                p.qc = ev.by; p.qcSiteOutcome = d.str("outcome")
                val items = d.arr("items").mapIndexedNotNull { i, x -> x.contentOrEmpty().trim().takeIf { it.isNotEmpty() }?.let { Correction(ev.id + "-" + i, "site", it, "customer") } }
                p.corrections.addAll(items)
                p.setStage(if (d.str("outcome") == "approved") Stage.SITE_OK else Stage.SITE_FIX, ev)
            }
            "CORRECTION_DONE" -> p.corrections.first { it.id == d.str("cid") }.done = Done(ev.t, ev.by, d.str("note"), d.evidence().map { it.id })
            "RELEASED" -> {
                p.supplier = d.str("supplier"); p.tech = d.str("tech"); p.drawings = d.str("drawings").trim()
                p.req?.let { p.materials = Pricing.materials(it) }
                p.setStage(Stage.MATERIAL_ORDER, ev)
            }
            "DISPATCHED" -> { p.dispatch = mapOf("seal" to d.str("seal").trim(), "vehicle" to d.str("vehicle").trim(), "serials" to d.str("serials").trim(), "t" to ev.t); p.setStage(Stage.DISPATCHED, ev) }
            "ARRIVED" -> {
                val seal = p.dispatch?.get("seal").orEmpty(); val seen = d.str("sealSeen").trim()
                p.arrival = mapOf("sealSeen" to seen, "sealIntact" to d.str("sealIntact"), "condition" to d.str("condition"), "t" to ev.t)
                if (!seen.equals(seal, ignoreCase = true)) p.incidents.add(Incident("I${ev.id}a", "seal_mismatch", ev.t, ev.by, "$seal ≠ $seen"))
                if (d.str("sealIntact") == "no") p.incidents.add(Incident("I${ev.id}b", "seal_broken", ev.t, ev.by, d.str("note")))
                if (d.str("condition") == "damaged") p.incidents.add(Incident("I${ev.id}c", "damage", ev.t, ev.by, d.str("note")))
                p.setStage(Stage.ARRIVED, ev)
            }
            "INCIDENT_RESOLVED" -> p.incidents.first { it.id == d.str("iid") }.open = false
            "MATERIAL_HANDED" -> { p.materialHanded = Done(ev.t, ev.by, d.str("note"), d.evidence().map { it.id }); p.setStage(Stage.INSTALLING, ev) }
            "SOP_STEP_DONE" -> p.sop[d.int("step")!!] = Done(ev.t, ev.by, d.str("note"), d.evidence().map { it.id })
            "INSPECTION_REQUESTED" -> p.setStage(Stage.QC_FINAL, ev)
            "QC_FINAL_RESULT" -> {
                p.qc = ev.by; p.qcFinalOutcome = d.str("outcome")
                val items = d.arr("items").mapIndexedNotNull { i, x -> x.contentOrEmpty().trim().takeIf { it.isNotEmpty() }?.let { Correction(ev.id + "-" + i, "install", it, "tech") } }
                p.corrections.addAll(items)
                p.setStage(if (d.str("outcome") == "pass") Stage.FINAL_PAY else Stage.INSTALL_FIX, ev)
            }
            "HANDOVER_SIGNED" -> p.hand[d.str("slot")] = Signature(d.str("slot"), d.str("signer").trim(), ev.t, ev.by, d.evidence().first { it.kind == "sign" }.id)
            "HANDOVER_COMPLETED" -> {
                val n = maxOf(1, s.config.amcVisits); val gap = Math.round(365.0 / n)
                p.handoverDone = Done(ev.t, ev.by)
                p.warrantyEnd = Time.addMonthsIso(ev.t, s.config.warrantyMonths.toLong())
                p.amcVisits = (1..n).map { Time.addDaysIso(ev.t, gap * it) }
                p.liftCode = "L" + Hash.sha256(p.id + ev.id).take(8).uppercase()
                p.setStage(Stage.AMC, ev)
            }
            "TICKET_OPENED" -> p.tickets.add(Ticket(d.str("tid"), d.str("kind").ifBlank { "breakdown" }, d.str("urgency"), d.str("note").trim(), ev.t, ev.by).also { it.history.add("open|${ev.t}|${ev.by}") })
            "TICKET_ASSIGNED" -> p.tickets.first { it.tid == d.str("tid") }.let { it.assignee = d.str("uid"); it.status = "assigned"; it.history.add("assigned|${ev.t}|${ev.by}") }
            "TICKET_UPDATED" -> p.tickets.first { it.tid == d.str("tid") }.let { it.status = "fixed"; it.fixNote = d.str("note"); it.history.add("fixed|${ev.t}|${ev.by}") }
            "TICKET_CLOSED" -> p.tickets.first { it.tid == d.str("tid") }.let {
                if (d.bool("ok")) { it.status = "closed"; it.history.add("closed|${ev.t}|${ev.by}") } else { it.status = "reopened"; it.history.add("reopened|${ev.t}|${ev.by}") }
            }
        }
    }

    /** Rebuild the whole state from the saved history, refusing (and reporting) any event the rules do not accept. */
    fun rebuild(events: List<Event>, onRejected: (Event, Err) -> Unit = { _, _ -> }): State {
        val skip = mutableSetOf<String>()
        while (true) {
            val s = State(); var bad: Event? = null
            val rejected = mutableListOf<Pair<Event, Err>>()
            for (ev in events) {
                if (ev.id in skip || s.byId.containsKey(ev.id)) continue // idempotent: the same record twice is applied once
                val err = Rules.validate(s, ev)
                if (err != null) { rejected.add(ev to err); continue }
                // safety net: a record that breaks the reducer is left out and the history is replayed without it,
                // so no half-applied change stays in the state and the app always starts
                try { apply(s, ev) } catch (e: RuntimeException) { bad = ev; break }
            }
            if (bad == null) { rejected.forEach { (e, r) -> onRejected(e, r) }; return s }
            skip += bad.id; onRejected(bad, Err("err_bad_record"))
        }
    }
}
