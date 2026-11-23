package com.aiec.elevatoros.domain

import com.aiec.elevatoros.domain.Role.ADMIN
import com.aiec.elevatoros.domain.Role.CUSTOMER
import com.aiec.elevatoros.domain.Role.FINANCE
import com.aiec.elevatoros.domain.Role.OWNER
import com.aiec.elevatoros.domain.Role.QC
import com.aiec.elevatoros.domain.Role.SALES
import com.aiec.elevatoros.domain.Role.TECH
import com.aiec.elevatoros.domain.Stage.*

/**
 * What a job needs next and from whom (port of needs(), original line 8936, simplified to this version's events).
 * [action] is the event type the task opens; a task is shown to a person only when the rules allow that person
 * to perform the action — so a task and its button can never disagree.
 */
data class Need(val key: String, val action: String, val pid: String, val extra: String = "")

data class Task(val need: Need, val project: Project, val level: Int, val daysWaiting: Int, val limit: Int)

object Tasks {
    fun needs(s: State, p: Project): List<Need> {
        val id = p.id
        fun n(key: String, action: String, extra: String = "") = Need(key, action, id, extra)
        val out = mutableListOf<Need>()
        // money waiting for Finance comes first: nothing else moves until it is confirmed
        p.payments.filter { it.status == "pending" }.forEach { out.add(n("verify_payment", "PAYMENT_STATUS", it.id)) }
        when (p.stage) {
            NEW -> out.add(n("check_lead", "LEAD_ACCEPTED"))
            RETURNED -> out.add(n("fix_lead", "LEAD_RESUBMITTED"))
            ACCEPTED -> out.add(n("contact_customer", "CONTACT_LOGGED"))
            CONTACTED -> out.add(n("lift_needs", "REQ_SAVED"))
            REQUIREMENTS -> out.add(n("make_quote", "QUOTE_CREATED"))
            QUOTE_APPROVAL -> out.add(n("approve_quote", "QUOTE_DECISION"))
            QUOTE_READY -> out.add(n("send_quote", "QUOTE_SENT"))
            QUOTED -> out.add(n("get_decision", "DEAL_WON"))
            WON -> out.add(n("prepare_agreement", "AGREEMENT_PREPARED"))
            AGREEMENT -> out.add(n("sign_agreement", "AGREEMENT_SIGNED"))
            SIGNED -> if (!p.payPending("token")) out.add(n("record_payment", "PAYMENT_RECORDED", "token"))
            SITE_PREP -> if (PREP_ITEMS.all { it.first in p.prep }) out.add(n("request_qc", "QC_REQUESTED")) else out.add(n("prepare_site", "PREP_ITEM_DONE"))
            QC_SITE -> out.add(n("inspect_site", "QC_SITE_RESULT"))
            SITE_FIX -> if (p.openCorrections("site").isEmpty()) out.add(n("request_qc", "QC_REQUESTED")) else out.add(n("fix_site", "CORRECTION_DONE"))
            SITE_OK -> out.add(n("release_material", "RELEASED"))
            MATERIAL_ORDER -> out.add(n("dispatch_material", "DISPATCHED"))
            DISPATCHED -> out.add(n("confirm_arrival", "ARRIVED"))
            ARRIVED -> {
                p.incidents.filter { it.open }.forEach { out.add(n("resolve_incident", "INCIDENT_RESOLVED", it.id)) }
                if (!p.payConfirmed("arrival") && !p.payPending("arrival")) out.add(n("record_payment", "PAYMENT_RECORDED", "arrival"))
                if (p.payConfirmed("arrival") && p.incidents.none { it.open }) out.add(n("hand_material", "MATERIAL_HANDED"))
            }
            INSTALLING -> if (p.sopNext() < SOP_STEPS.size) out.add(n("do_step", "SOP_STEP_DONE", p.sopNext().toString())) else out.add(n("request_inspection", "INSPECTION_REQUESTED"))
            QC_FINAL -> out.add(n("final_inspection", "QC_FINAL_RESULT"))
            INSTALL_FIX -> if (p.openCorrections("install").isEmpty()) out.add(n("request_inspection", "INSPECTION_REQUESTED")) else out.add(n("fix_install", "CORRECTION_DONE"))
            FINAL_PAY -> if (!p.payPending("final")) out.add(n("record_payment", "PAYMENT_RECORDED", "final"))
            HANDOVER -> {
                listOf("customer", "tech", "qc").filter { it !in p.hand }.forEach { out.add(n("sign_handover", "HANDOVER_SIGNED", it)) }
                if (p.hand.size == 3) out.add(n("complete_handover", "HANDOVER_COMPLETED"))
            }
            AMC -> p.tickets.filter { it.status != "closed" }.forEach { t ->
                when (t.status) {
                    "open" -> out.add(n("assign_ticket", "TICKET_ASSIGNED", t.tid))
                    "assigned", "reopened" -> out.add(n("fix_ticket", "TICKET_UPDATED", t.tid))
                    "fixed" -> out.add(n("close_ticket", "TICKET_CLOSED", t.tid))
                }
            }
            LOST -> {}
        }
        return out
    }

    /** May this person act on this need? Same engine as the buttons, plus who-exactly checks the rules apply. */
    fun isMine(s: State, u: User, need: Need): Boolean {
        val p = s.projects[need.pid] ?: return false
        if (!Rules.allowed(s, u.uid, need.action, need.pid, need.extra)) return false
        return when (need.key) {
            "verify_payment" -> p.payments.firstOrNull { it.id == need.extra }?.recordedBy != u.uid
            "approve_quote" -> p.lastQuote()?.let { q -> q.by != u.uid && (u.role == OWNER || ("below_floor" !in q.why && "over_admin" !in q.why)) } ?: false
            "fix_lead" -> p.createdBy == u.uid || (u.role.office && s.users[p.createdBy]?.active != true)
            "sign_handover" -> when (need.extra) {
                "customer" -> u.role == CUSTOMER || (u.role == TECH && p.tech == u.uid && s.users[p.customer]?.active != true)
                "tech" -> u.role == TECH && p.tech == u.uid
                else -> u.role == QC || (u.role.office && Rules.covers(s, Rules.RULES.getValue("HANDOVER_SIGNED"), u))
            }
            "fix_site" -> u.role != TECH
            "fix_install" -> u.role == TECH
            "fix_ticket" -> u.role == TECH && p.tickets.firstOrNull { it.tid == need.extra }?.assignee == u.uid
            // never give the recording to the only person who could then confirm it (that would be a dead end)
            "record_payment" -> (u.role != FINANCE || s.activeUsers(SALES).isEmpty()) && Rules.someoneElseCanConfirm(s, u.uid)
            "check_lead", "contact_customer", "lift_needs", "make_quote", "send_quote", "get_decision", "prepare_agreement" ->
                u.role == SALES || Rules.covers(s, Rules.RULES.getValue(need.action), u)
            "inspect_site", "final_inspection" -> u.role == QC || Rules.covers(s, Rules.RULES.getValue(need.action), u)
            "complete_handover" -> u.role.office
            "release_material", "resolve_incident", "assign_ticket" -> u.role.office || (need.key == "assign_ticket" && u.role == QC)
            else -> true
        }
    }

    /** Working-day limit for the current step and the escalation level (original escalation ladder 1 → Admin 3 → HQ 7). */
    /** A service ticket's clock starts when the ticket was opened, not when the job reached its stage. */
    fun sinceOf(p: Project, n: Need): String = p.tickets.firstOrNull { it.tid == n.extra && n.action.startsWith("TICKET_") }?.history?.lastOrNull()?.split("|")?.getOrNull(1) ?: p.since

    fun escalation(s: State, p: Project, now: String, since: String = p.since): Pair<Int, Int> {
        val days = Time.workingDays(since, now, s.config.sundayWork)
        val c = s.config
        val level = when {
            days >= c.escOwnerDays -> 3
            days >= c.escalateDays -> 2
            days >= c.remindDays -> 1
            else -> 0
        }
        return days to level
    }

    fun tasksFor(s: State, u: User, now: String): List<Task> =
        s.projects.values.asSequence()
            .filter { it.stage != LOST }
            .flatMap { p -> needs(s, p).filter { isMine(s, u, it) }.map { p to it } }
            .map { (p, n) -> val (days, level) = escalation(s, p, now, sinceOf(p, n)); Task(n, p, level, days, s.config.escalateDays) }
            .sortedWith(compareByDescending<Task> { it.level }.thenBy { it.project.since })
            .toList()

    /** Escalated work the office must see (level 2 for Admin, level 3 for Head Quarter), like Me → Escalations. */
    fun escalations(s: State, now: String): List<Task> = s.projects.values.filter { it.stage != LOST && it.stage != AMC }.flatMap { p ->
        val (days, level) = escalation(s, p, now)
        if (level >= 2) needs(s, p).take(1).map { Task(it, p, level, days, s.config.escalateDays) } else emptyList()
    }.sortedByDescending { it.daysWaiting }

    /** Bar 1: work done on the job (0..1) and a short label key. */
    fun progress(p: Project): Pair<Float, String> = when (p.stage) {
        INSTALLING -> p.sop.size.toFloat() / SOP_STEPS.size to "sop"
        AMC -> 1f to "done"
        LOST -> 0f to "lost"
        else -> {
            val order = listOf(NEW, ACCEPTED, CONTACTED, REQUIREMENTS, QUOTE_READY, QUOTED, WON, AGREEMENT, SIGNED, SITE_PREP, QC_SITE, SITE_OK,
                MATERIAL_ORDER, DISPATCHED, ARRIVED, INSTALLING, QC_FINAL, FINAL_PAY, HANDOVER, AMC)
            val idx = order.indexOf(p.stage).let { if (it >= 0) it else order.indexOf(nearest(p.stage)) }
            idx.toFloat() / (order.size - 1) to "stage"
        }
    }

    private fun nearest(s: Stage) = when (s) {
        RETURNED -> NEW; QUOTE_APPROVAL -> REQUIREMENTS; SITE_FIX -> QC_SITE; INSTALL_FIX -> QC_FINAL; else -> NEW
    }

    /** Who acts next, as roles, for "Next: Sales — check new lead" lines. */
    fun nextRoles(s: State, p: Project): List<Role> = needs(s, p).firstOrNull()?.let { need ->
        val r = Rules.RULES[need.action] ?: return@let emptyList()
        r.roles.filter { it != OWNER || r.roles.size == 1 }.ifEmpty { listOf(ADMIN) }
    } ?: emptyList()

}
