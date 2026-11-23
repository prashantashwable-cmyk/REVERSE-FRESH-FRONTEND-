package com.aiec.elevatoros.domain

import com.aiec.elevatoros.domain.Role.ADMIN
import com.aiec.elevatoros.domain.Role.CUSTOMER
import com.aiec.elevatoros.domain.Role.FIELD
import com.aiec.elevatoros.domain.Role.FINANCE
import com.aiec.elevatoros.domain.Role.OWNER
import com.aiec.elevatoros.domain.Role.QC
import com.aiec.elevatoros.domain.Role.SALES
import com.aiec.elevatoros.domain.Role.SUPPLIER
import com.aiec.elevatoros.domain.Role.TECH
import com.aiec.elevatoros.domain.Stage.*

/*
 * The single approval engine (port of RULES + validate(), original lines 6798–8345).
 * Every event — typed on this phone or loaded from another phone's file — passes the same checks:
 * known type → known active user → role allowed (or office covering an empty role) → project visible → stage allowed
 * → rule-specific check. The UI asks the same engine (allowed()) so a button never shows for an action the rule refuses.
 */
class Ctx(val s: State, val ev: Event, val u: User, val p: Project?) {
    val d get() = ev.data
    @get:JvmName("requireProject") val P: Project get() = p!!
}

class Rule(
    val roles: Set<Role>,
    val project: Boolean = true,
    val stages: Set<Stage>? = null,
    /** office may act when no other active person holds one of these office roles (original ownerCovers) */
    val coverable: Boolean = false,
    /** may act on a project the person cannot otherwise see (e.g. a new person raising SOS) */
    val open: Boolean = false,
    val check: Ctx.() -> Err? = { null },
)

private fun need(ok: Boolean, key: String, vars: Map<String, String> = emptyMap()): Err? = if (ok) null else Err(key, vars)
private val COVER_ROLES = setOf(SALES, FINANCE, QC)

object Rules {
    fun canSee(s: State, u: User, p: Project): Boolean = when (u.role) {
        OWNER, ADMIN, FINANCE -> true
        FIELD -> p.createdBy == u.uid
        SALES -> p.sales == null || p.sales == u.uid
        QC -> p.stage != LOST && p.stage.ordinal >= SITE_PREP.ordinal
        SUPPLIER -> p.supplier == u.uid
        TECH -> p.tech == u.uid || p.tickets.any { it.assignee == u.uid }
        // licensed partner company flows are not part of this version (documented in 12_KNOWN_LIMITATIONS_AND_RISKS.md)
        Role.PARTNER -> false
        CUSTOMER -> p.customer == u.uid
    }

    /** Office covering a role nobody else holds; with an Admin in the team the Admin covers, otherwise the Head Quarter. */
    fun covers(s: State, r: Rule, u: User): Boolean {
        if (!r.coverable || (u.role != OWNER && u.role != ADMIN)) return false
        val needRoles = r.roles.intersect(COVER_ROLES)
        if (needRoles.isEmpty()) return false
        if (s.users.values.any { it.active && it.uid != u.uid && it.role in needRoles }) return false
        val hasAdmin = s.users.values.any { it.active && it.role == ADMIN }
        return u.role == (if (hasAdmin) ADMIN else OWNER)
    }

    private val salesSide = setOf(SALES)

    val RULES: Map<String, Rule> = mapOf(
        "USER_ADDED" to Rule(setOf(OWNER, ADMIN, SALES), project = false) {
            val role = Role.of(d.str("role"))
            when {
                d.str("name").isBlank() || d.str("pin").isBlank() || d.str("salt").isBlank() -> Err("err_fill")
                role == null -> Err("err_role_bad")
                s.users.containsKey(d.str("uid")) -> Err("err_dup_user")
                u.role == ADMIN && role == OWNER -> Err("err_role", mapOf("roles" to "owner"))
                u.role == SALES && role != CUSTOMER -> Err("err_role", mapOf("roles" to "owner / admin"))
                role == CUSTOMER -> {
                    val p = s.projects[d.str("projectId")]
                    when {
                        p == null -> Err("err_no_project")
                        p.customer != null -> Err("err_has_customer")
                        u.role == SALES && (p.sales != u.uid || p.stage.ordinal < WON.ordinal || p.stage == LOST) -> Err("err_access")
                        else -> null
                    }
                }
                d.str("phone").isNotEmpty() && phone10(d.str("phone")).isEmpty() -> Err("err_phone")
                else -> null
            }
        },
        "USER_UPDATED" to Rule(setOf(OWNER, ADMIN), project = false) {
            val x = s.users[d.str("uid")]
            when {
                x == null -> Err("err_no_user")
                x.role == OWNER && u.role != OWNER -> Err("err_role", mapOf("roles" to "owner"))
                x.uid == u.uid && d.containsKey("active") && !d.bool("active") -> Err("err_self")
                d.containsKey("role") && Role.of(d.str("role")) == OWNER && u.role != OWNER -> Err("err_role", mapOf("roles" to "owner"))
                else -> null
            }
        },
        "CONFIG_SET" to Rule(setOf(OWNER), project = false) {
            Config.check(s.config, d.str("key"), d.str("value"))?.let { Err(it) }
        },
        "LEAD_CREATED" to Rule(setOf(FIELD, SALES, ADMIN, OWNER), project = false) {
            val ph = phone10(d.str("phone"))
            when {
                s.projects.containsKey(ev.pid) || ev.pid.isNullOrBlank() -> Err("err_dup_project")
                d.gps() == null && d.str("address").isBlank() -> Err("err_location_or_address")
                !d.bool("noPhone") && ph.isEmpty() -> Err("err_phone")
                !d.bool("noPhone") && d.str("name").isBlank() -> Err("err_fill")
                u.role == FIELD && d.evidence().none { it.tag == "building" } -> Err("err_photos", mapOf("n" to "1"))
                (d.int("floors") ?: 0) !in 0..60 -> Err("err_range")
                else -> null
            }
        },
        "LEAD_ACCEPTED" to Rule(setOf(SALES, ADMIN), stages = setOf(NEW), coverable = true),
        "LEAD_RETURNED" to Rule(setOf(SALES, ADMIN), stages = setOf(NEW), coverable = true) {
            need(d.str("reason") in RETURN_REASONS, "err_reason") ?: need(d.str("reason") != "other" || d.str("note").isNotBlank(), "err_reason")
        },
        "LEAD_RESUBMITTED" to Rule(setOf(FIELD, SALES, ADMIN, OWNER), stages = setOf(RETURNED)) {
            need(P.createdBy == u.uid || u.role.office, "err_access") ?: need(d.str("note").isNotBlank(), "err_fill")
        },
        "CONTACT_LOGGED" to Rule(setOf(SALES), stages = Stage.SALES_TALK, coverable = true) {
            val outcome = d.str("outcome")
            when {
                d.str("channel") !in setOf("call", "whatsapp", "visit") -> Err("err_fill")
                outcome !in setOf("interested", "no_answer", "call_later", "not_interested") -> Err("err_fill")
                outcome == "not_interested" && d.str("reason") !in LOST_REASONS -> Err("err_reason")
                outcome == "call_later" && d.str("next").isBlank() -> Err("err_date")
                else -> null
            }
        },
        "REQ_SAVED" to Rule(setOf(SALES), stages = setOf(CONTACTED, REQUIREMENTS, QUOTED), coverable = true) {
            val r = d.obj("req")
            when {
                r == null -> Err("err_fill")
                r.str("type") !in setOf("auto", "manual") || r.str("finish") !in setOf("ss", "ms") -> Err("err_fill")
                (r.int("stops") ?: 0) !in 2..40 -> Err("err_range")
                (r.int("capacity") ?: 0) !in setOf(4, 6, 8, 10, 13) -> Err("err_fill")
                r.str("job") !in setOf("new", "replace") -> Err("err_fill")
                else -> null
            }
        },
        "QUOTE_CREATED" to Rule(setOf(SALES), stages = setOf(REQUIREMENTS, QUOTED), coverable = true) {
            val disc = d.num("discountPct") ?: 0.0
            when {
                P.req == null -> Err("err_need_req")
                disc !in 0.0..90.0 -> Err("err_range")
                disc > 0 && d.str("reason").isBlank() -> Err("err_reason")
                else -> null
            }
        },
        "QUOTE_DECISION" to Rule(setOf(OWNER, ADMIN), stages = setOf(QUOTE_APPROVAL)) {
            val q = P.lastQuote()
            when {
                q == null -> Err("err_fill")
                q.by == u.uid -> Err("err_not_author")
                d.str("decision") !in setOf("approve", "reject") -> Err("err_fill")
                d.str("decision") == "reject" && d.str("reason").isBlank() -> Err("err_reason")
                d.str("decision") == "approve" && ("below_floor" in q.why || "over_admin" in q.why) && u.role != OWNER -> Err("err_role", mapOf("roles" to "owner"))
                else -> null
            }
        },
        "QUOTE_SENT" to Rule(setOf(SALES), stages = setOf(QUOTE_READY), coverable = true) {
            need(d.str("channel") in setOf("whatsapp", "print", "in_person", "email"), "err_fill")
        },
        "DEAL_WON" to Rule(setOf(SALES), stages = setOf(QUOTED), coverable = true) {
            val q = P.lastQuote()
            val sent = q?.sentT
            if (q == null || sent == null) Err("err_fill")
            else need(Time.workingDays(sent, ev.t, true) <= q.validity, "err_quote_expired")
        },
        "DEAL_LOST" to Rule(setOf(SALES, ADMIN, OWNER), stages = Stage.PRE_WON) {
            need(d.str("reason") in LOST_REASONS, "err_reason") ?: need(d.str("reason") != "other" || d.str("note").isNotBlank(), "err_reason")
        },
        "AGREEMENT_PREPARED" to Rule(setOf(SALES), stages = setOf(WON), coverable = true),
        "AGREEMENT_SIGNED" to Rule(setOf(CUSTOMER, SALES), stages = setOf(AGREEMENT), coverable = true) {
            need(d.str("signer").isNotBlank(), "err_fill") ?: need(d.evidence().any { it.kind == "sign" }, "err_sign")
        },
        "PAYMENT_RECORDED" to Rule(setOf(SALES, FINANCE, CUSTOMER), coverable = true) {
            val purpose = d.str("purpose"); val amt = d.num("amount") ?: 0.0
            when {
                PAY_STAGE[purpose] != P.stage -> Err("err_pay_not_open")
                P.payPending(purpose) || P.payConfirmed(purpose) -> Err("err_pay_exists")
                amt <= 0 || amt > 100_000_000 -> Err("err_amount")
                d.str("method") !in setOf("upi", "bank", "cash", "cheque") -> Err("err_fill")
                d.str("method") != "cash" && d.str("ref").isBlank() -> Err("err_ref")
                d.str("payId").isBlank() || P.payments.any { it.id == d.str("payId") } -> Err("err_fill")
                else -> null
            }
        },
        "PAYMENT_STATUS" to Rule(setOf(FINANCE), coverable = true) {
            val pay = P.payments.firstOrNull { it.id == d.str("payId") }
            when {
                pay == null -> Err("err_fill")
                pay.status != "pending" -> Err("err_already")
                pay.recordedBy == u.uid -> Err("err_own_payment")
                d.str("status") !in setOf("confirmed", "not_found") -> Err("err_fill")
                d.str("status") == "confirmed" && d.str("ref").isBlank() -> Err("err_ref")
                d.str("status") == "not_found" && d.str("reason").isBlank() -> Err("err_reason")
                else -> null
            }
        },
        "PREP_ITEM_DONE" to Rule(setOf(CUSTOMER, SALES), stages = setOf(SITE_PREP), coverable = true) {
            need(PREP_ITEMS.any { it.first == d.str("item") }, "err_fill") ?: need(d.evidence().isNotEmpty(), "err_photos", mapOf("n" to "1"))
        },
        "QC_REQUESTED" to Rule(setOf(CUSTOMER, SALES), stages = setOf(SITE_PREP, SITE_FIX), coverable = true) {
            if (P.stage == SITE_PREP) need(PREP_ITEMS.all { it.first in P.prep }, "err_prep_open")
            else need(P.openCorrections("site").isEmpty(), "err_corrections_open")
        },
        "QC_SITE_RESULT" to Rule(setOf(QC), stages = setOf(QC_SITE), coverable = true) {
            val outcome = d.str("outcome"); val m = d.obj("meas")
            val low = m?.let { measLow(s.config, it) } ?: listOf("width")
            when {
                outcome !in setOf("approved", "corrections") -> Err("err_fill")
                d.evidence().isEmpty() -> Err("err_photos", mapOf("n" to "1"))
                m == null -> Err("err_fill")
                outcome == "approved" && low.isNotEmpty() -> Err("err_shaft_low", mapOf("list" to low.joinToString(", ")))
                outcome == "corrections" && d.arr("items").isEmpty() -> Err("err_fill")
                else -> null
            }
        },
        "CORRECTION_DONE" to Rule(setOf(CUSTOMER, SALES, TECH), stages = setOf(SITE_FIX, INSTALL_FIX), coverable = true) {
            val c = P.corrections.firstOrNull { it.id == d.str("cid") }
            when {
                c == null -> Err("err_fill")
                c.done != null -> Err("err_already")
                (c.scope == "install") != (u.role == TECH) && !(u.role.office) -> Err("err_access")
                d.evidence().isEmpty() -> Err("err_photos", mapOf("n" to "1"))
                else -> null
            }
        },
        "RELEASED" to Rule(setOf(ADMIN, OWNER), stages = setOf(SITE_OK)) {
            val sup = s.users[d.str("supplier")]; val tech = s.users[d.str("tech")]
            when {
                sup == null || !sup.active || sup.role != SUPPLIER -> Err("err_pick_supplier")
                tech == null || !tech.active || tech.role != TECH -> Err("err_pick_tech")
                d.str("drawings").isBlank() -> Err("err_fill")
                else -> null
            }
        },
        "DISPATCHED" to Rule(setOf(SUPPLIER), stages = setOf(MATERIAL_ORDER)) {
            need(P.supplier == u.uid, "err_access") ?: need(d.str("seal").isNotBlank(), "err_fill") ?: need(d.evidence().isNotEmpty(), "err_photos", mapOf("n" to "1"))
        },
        "ARRIVED" to Rule(setOf(TECH, SUPPLIER), stages = setOf(DISPATCHED)) {
            need(P.tech == u.uid || P.supplier == u.uid, "err_access") ?: need(d.str("sealSeen").isNotBlank(), "err_fill")
                ?: need(d.str("sealIntact") in setOf("yes", "no") && d.str("condition") in setOf("ok", "damaged"), "err_fill")
                ?: need(d.evidence().isNotEmpty(), "err_photos", mapOf("n" to "1"))
        },
        "INCIDENT_RESOLVED" to Rule(setOf(ADMIN, OWNER)) {
            val i = P.incidents.firstOrNull { it.id == d.str("iid") }
            need(i != null && i.open, "err_already") ?: need(d.str("note").isNotBlank(), "err_reason")
        },
        "MATERIAL_HANDED" to Rule(setOf(TECH), stages = setOf(ARRIVED)) {
            when {
                P.tech != u.uid -> Err("err_access")
                !P.payConfirmed("arrival") -> Err("err_wait_payment")
                P.incidents.any { it.open } -> Err("err_incident_open")
                d.str("custName").isBlank() -> Err("err_fill")
                d.evidence().count { it.kind == "sign" } < 2 -> Err("err_sign")
                d.evidence().none { it.kind == "photo" } -> Err("err_photos", mapOf("n" to "1"))
                else -> null
            }
        },
        "SOP_STEP_DONE" to Rule(setOf(TECH), stages = setOf(INSTALLING)) {
            val step = d.int("step") ?: -1
            when {
                P.tech != u.uid -> Err("err_access")
                step != P.sopNext() -> Err("err_step_order")
                d.evidence().isEmpty() -> Err("err_photos", mapOf("n" to "1"))
                activeSos(s, P) -> Err("err_work_stopped")
                else -> null
            }
        },
        "INSPECTION_REQUESTED" to Rule(setOf(TECH), stages = setOf(INSTALLING, INSTALL_FIX)) {
            if (P.tech != u.uid) Err("err_access")
            else if (P.stage == INSTALLING) need(P.sopNext() >= SOP_STEPS.size, "err_steps_open")
            else need(P.openCorrections("install").isEmpty(), "err_corrections_open")
        },
        "QC_FINAL_RESULT" to Rule(setOf(QC), stages = setOf(QC_FINAL), coverable = true) {
            val checks = d.strMap("checks")
            when {
                P.tech == u.uid -> Err("err_qc_own")
                d.str("outcome") !in setOf("pass", "fail") -> Err("err_fill")
                QC_FINAL_CHECKS.any { checks[it] !in setOf("pass", "fail") } -> Err("err_checks")
                d.str("outcome") == "pass" && checks.values.any { it == "fail" } -> Err("err_pass_with_fail")
                d.str("outcome") == "fail" && d.arr("items").isEmpty() -> Err("err_fill")
                d.evidence().isEmpty() -> Err("err_photos", mapOf("n" to "1"))
                else -> null
            }
        },
        "HANDOVER_SIGNED" to Rule(setOf(CUSTOMER, TECH, QC), stages = setOf(HANDOVER), coverable = true) {
            val slot = d.str("slot")
            when {
                slot !in setOf("customer", "tech", "qc") -> Err("err_fill")
                slot in P.hand -> Err("err_already")
                slot == "tech" && (u.role != TECH || P.tech != u.uid) -> Err("err_access")
                slot == "qc" && u.role != QC && !(u.role.office && covers(s, RULES.getValue("HANDOVER_SIGNED"), u)) -> Err("err_access")
                slot == "customer" && u.role != CUSTOMER && u.role != TECH -> Err("err_access")
                slot == "customer" && u.role == TECH && d.str("why").isBlank() -> Err("err_reason")
                d.str("signer").isBlank() -> Err("err_fill")
                d.evidence().none { it.kind == "sign" } -> Err("err_sign")
                else -> null
            }
        },
        "HANDOVER_COMPLETED" to Rule(setOf(ADMIN, OWNER, QC), stages = setOf(HANDOVER)) {
            need(listOf("customer", "tech", "qc").all { it in P.hand }, "err_signatures_open")
        },
        "TICKET_OPENED" to Rule(setOf(CUSTOMER, SALES, ADMIN, OWNER, TECH), stages = setOf(AMC)) {
            need(d.str("tid").isNotBlank() && P.tickets.none { it.tid == d.str("tid") }, "err_fill")
                ?: need(d.str("urgency") in setOf("trapped", "unsafe", "stopped", "ordinary"), "err_fill")
                ?: need(d.str("note").isNotBlank(), "err_fill")
        },
        "TICKET_ASSIGNED" to Rule(setOf(ADMIN, OWNER, QC), stages = setOf(AMC)) {
            val t = P.tickets.firstOrNull { it.tid == d.str("tid") }; val who = s.users[d.str("uid")]
            need(t != null && t.status != "closed", "err_already") ?: need(who != null && who.active && who.role == TECH, "err_pick_tech")
        },
        "TICKET_UPDATED" to Rule(setOf(TECH, ADMIN, OWNER, QC), stages = setOf(AMC)) {
            val t = P.tickets.firstOrNull { it.tid == d.str("tid") }
            need(t != null && t.status in setOf("assigned", "open", "reopened"), "err_already")
                ?: need(u.role != TECH || t!!.assignee == u.uid, "err_access")
                ?: need(d.str("note").isNotBlank(), "err_fill") ?: need(d.evidence().isNotEmpty(), "err_photos", mapOf("n" to "1"))
        },
        "TICKET_CLOSED" to Rule(setOf(CUSTOMER, ADMIN, OWNER), stages = setOf(AMC)) {
            val t = P.tickets.firstOrNull { it.tid == d.str("tid") }
            need(t != null && t.status == "fixed", "err_not_fixed") ?: need(d.bool("ok") || d.str("note").isNotBlank(), "err_reason")
        },
        "SOS_RAISED" to Rule(Role.ALL - CUSTOMER, project = false) {
            need(d.str("sid").isNotBlank() && !s.sos.containsKey(d.str("sid")), "err_fill")
        },
        "SOS_CLOSED" to Rule(setOf(OWNER, ADMIN), project = false) {
            val x = s.sos[d.str("sid")]
            need(x != null && x.open, "err_already") ?: need(d.str("note").isNotBlank(), "err_reason")
        },
    )

    fun activeSos(s: State, p: Project) = s.sos.values.any { it.open && it.pid == p.id }

    /** QC measurements below the shaft minimums (original measLow with tolWidth/tolDepth/tolPit/tolHead). */
    fun measLow(cfg: Config, m: kotlinx.serialization.json.JsonObject): List<String> = buildList {
        if ((m.int("width") ?: 0) < cfg.tolWidth) add("width")
        if ((m.int("depth") ?: 0) < cfg.tolDepth) add("depth")
        if ((m.int("pit") ?: 0) < cfg.tolPit) add("pit")
        if ((m.int("head") ?: 0) < cfg.tolHead) add("head")
    }

    /** validate(): null = accepted. */
    fun validate(s: State, ev: Event): Err? {
        if (ev.v > Event.EVENT_VERSION) return Err("err_newer_app")
        if (ev.type == "ORG_CREATED") return if (s.org != null) Err("err_org_exists") else need(ev.data.str("orgId").isNotBlank() && ev.data.str("name").isNotBlank(), "err_fill")
        val org = s.org ?: return Err("err_no_org")
        // the first Head Quarter, made by the company setup
        if (ev.type == "USER_ADDED" && s.users.isEmpty()) {
            val d = ev.data
            return need(d.str("role") == "owner" && ev.by == d.str("uid") && d.str("name").isNotBlank() && d.str("pin").isNotBlank(), "err_boot")
        }
        val r = RULES[ev.type] ?: return Err("err_unknown")
        val u = s.users[ev.by]
        if (u == null || !u.active) return Err("err_no_user")
        if (u.role !in r.roles && !covers(s, r, u)) return Err("err_role", mapOf("roles" to r.roles.joinToString(" / ") { it.key }))
        var p: Project? = null
        if (r.project) {
            p = s.projects[ev.pid] ?: return Err("err_no_project")
            if (!r.open && !canSee(s, u, p) && !covers(s, r, u)) return Err("err_access")
            if (r.stages != null && p.stage !in r.stages) return Err("err_stage", mapOf("stage" to p.stage.key))
        }
        if (org.id.isBlank()) return Err("err_no_org")
        return r.check(Ctx(s, ev, u, p))
    }

    /**
     * May [uid] perform [type] on [pid] at all (role, visibility, stage)? Used by the UI to show only possible actions.
     * The data-specific checks run again when the event is made.
     */
    fun allowed(s: State, uid: String, type: String, pid: String?): Boolean {
        val r = RULES[type] ?: return false
        val u = s.users[uid] ?: return false
        if (!u.active) return false
        if (u.role !in r.roles && !covers(s, r, u)) return false
        if (!r.project) return true
        val p = s.projects[pid] ?: return false
        if (!r.open && !canSee(s, u, p) && !covers(s, r, u)) return false
        return r.stages == null || p.stage in r.stages
    }
}
