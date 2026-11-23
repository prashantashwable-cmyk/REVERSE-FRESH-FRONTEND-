package com.aiec.elevatoros.ui

import com.aiec.elevatoros.domain.EvidenceRef
import com.aiec.elevatoros.domain.GeoPoint
import com.aiec.elevatoros.domain.Ids
import com.aiec.elevatoros.domain.LOST_REASONS
import com.aiec.elevatoros.domain.PREP_ITEMS
import com.aiec.elevatoros.domain.Pin
import com.aiec.elevatoros.domain.Pricing
import com.aiec.elevatoros.domain.Project
import com.aiec.elevatoros.domain.QC_FINAL_CHECKS
import com.aiec.elevatoros.domain.QC_SITE_CHECKS
import com.aiec.elevatoros.domain.RETURN_REASONS
import com.aiec.elevatoros.domain.Requirements
import com.aiec.elevatoros.domain.Role
import com.aiec.elevatoros.domain.SOP_STEPS
import com.aiec.elevatoros.domain.State
import com.aiec.elevatoros.domain.Time
import com.aiec.elevatoros.domain.User
import com.aiec.elevatoros.domain.haversine
import com.aiec.elevatoros.domain.jsonOf
import com.aiec.elevatoros.domain.phone10
import kotlinx.serialization.json.JsonObject

/** One input on an action form. Labels are dictionary keys. */
sealed class F(val key: String, val label: String) {
    class Text(key: String, label: String, val required: Boolean = true, val multiline: Boolean = false, val kind: Kind = Kind.TEXT, val initial: String = "") : F(key, label)
    class Choice(key: String, label: String, val options: List<Pair<String, String>>, val required: Boolean = true, val initial: String? = null) : F(key, label)
    class Checks(key: String, label: String, val items: List<String>, val prefix: String) : F(key, label)
    class Photos(label: String, val tag: String, val min: Int) : F("ev_photo_$tag", label)
    class Sign(key: String, label: String) : F(key, label)
    class Gps(label: String) : F("gps", label)
    class Person(key: String, label: String, val role: Role) : F(key, label)
    class Lines(key: String, label: String) : F(key, label)
    class Info(val text: (Values) -> String?, val warn: Boolean = false) : F("info_${Ids.rid(4)}", "")
    enum class Kind { TEXT, NUMBER, PHONE, PIN, DATE }
}

typealias Values = Map<String, Any?>

fun Values.s(k: String) = (this[k] as? String).orEmpty().trim()
fun Values.n(k: String) = s(k).toDoubleOrNull()
@Suppress("UNCHECKED_CAST") fun Values.refs(k: String) = (this[k] as? List<EvidenceRef>).orEmpty()
fun Values.allEvidence(): List<EvidenceRef> = keys.filter { it.startsWith("ev_") || it.startsWith("sig") }.flatMap { k ->
    @Suppress("UNCHECKED_CAST") when (val v = this[k]) { is List<*> -> v as List<EvidenceRef>; is EvidenceRef -> listOf(v); else -> emptyList() }
}

class Ctx(val s: State, val p: Project?, val extra: String, val me: User, val tr: (String) -> String)

class ActionSpec(
    val type: String, val title: String, val icon: String,
    val fields: (Ctx) -> List<F>,
    /** builds the event data; may return a new project id via "_pid" */
    val build: (Values, Ctx) -> JsonObject,
    val savedKey: String = "saved_ok",
)

object Actions {
    private fun yesNo(c: Ctx) = listOf("yes" to c.tr("yes"), "no" to c.tr("no"))
    private fun opts(c: Ctx, prefix: String, vararg values: String) = values.map { it to c.tr(prefix + it) }

    fun newPid(): String = "J" + Time.day(Time.now()).toString().replace("-", "").drop(2).take(4) + "-" + Ids.rid(4)

    val SPECS: Map<String, ActionSpec> = listOf(
        ActionSpec("LEAD_CREATED", "add_site", "➕", { c ->
            listOf(
                F.Photos("ph_building", "building", if (c.me.role == Role.FIELD) 1 else 0),
                F.Gps("f_gps"),
                F.Text("address", "f_address", required = false, multiline = true),
                F.Text("landmark", "f_landmark", required = false),
                F.Text("name", "f_contact_name", required = false),
                F.Text("phone", "f_phone", required = false, kind = F.Kind.PHONE),
                F.Choice("noPhone", "f_no_phone", yesNo(c), required = false, initial = "no"),
                F.Text("floors", "f_floors", required = false, kind = F.Kind.NUMBER),
                F.Choice("bType", "f_btype", opts(c, "bt_", "residential", "commercial", "hospital", "other"), required = false),
                F.Text("note", "f_note", required = false, multiline = true),
                F.Info({ v -> dupText(c, v) }, warn = true),
            )
        }, { v, c ->
            val gps = v["gps"] as? GeoPoint
            jsonOf(
                "_pid" to newPid(), "address" to v.s("address"), "landmark" to v.s("landmark"), "name" to v.s("name"), "phone" to v.s("phone"),
                "noPhone" to (v.s("noPhone") == "yes"), "floors" to v.n("floors")?.toInt(), "bType" to v.s("bType"), "note" to v.s("note"), "gps" to gps,
                "dupOf" to dups(c.s, v).map { it.id }, "ev" to v.allEvidence()
            )
        }, "saved_site"),
        ActionSpec("LEAD_ACCEPTED", "check_lead", "✅", { c -> listOf(F.Info({ leadSummary(c) })) }, { _, _ -> jsonOf() }),
        ActionSpec("LEAD_RETURNED", "return_lead", "↩️", { c ->
            listOf(F.Choice("reason", "f_reason", RETURN_REASONS.map { it to c.tr("rr_$it") }), F.Text("note", "f_note", required = false, multiline = true))
        }, { v, _ -> jsonOf("reason" to v.s("reason"), "note" to v.s("note")) }),
        ActionSpec("LEAD_RESUBMITTED", "fix_lead", "🔁", { c -> listOf(F.Info({ c.p?.returnedReason?.let { r -> c.tr("returned_because") + ": " + c.tr("rr_$r") } }, warn = true), F.Text("note", "f_what_fixed", multiline = true)) },
            { v, _ -> jsonOf("note" to v.s("note")) }),
        ActionSpec("CONTACT_LOGGED", "contact_customer", "📞", { c ->
            listOf(
                F.Info({ c.p?.let { p -> if (p.contactPhone.isNotEmpty()) "📞 ${p.contactName} · ${p.contactPhone}" else null } }),
                F.Choice("channel", "f_channel", opts(c, "ch_", "call", "whatsapp", "visit")),
                F.Choice("outcome", "f_outcome", opts(c, "oc_", "interested", "no_answer", "call_later", "not_interested")),
                F.Text("next", "f_next_date", required = false, kind = F.Kind.DATE),
                F.Choice("reason", "f_lost_reason", LOST_REASONS.map { it to c.tr("lr_$it") }, required = false),
                F.Text("note", "f_note", required = false, multiline = true),
            )
        }, { v, _ -> jsonOf("channel" to v.s("channel"), "outcome" to v.s("outcome"), "next" to v.s("next"), "reason" to v.s("reason"), "note" to v.s("note")) }),
        ActionSpec("REQ_SAVED", "lift_needs", "📝", { c ->
            val r = c.p?.req ?: Requirements(stops = maxOf(2, (c.p?.floors ?: 1) + 1).coerceAtMost(40))
            listOf(
                F.Choice("type", "f_lift_type", opts(c, "lt_", "auto", "manual"), initial = r.type),
                F.Text("stops", "f_stops", kind = F.Kind.NUMBER, initial = r.stops.toString()),
                F.Choice("capacity", "f_capacity", listOf(4, 6, 8, 10, 13).map { it.toString() to c.tr("persons").replace("{n}", it.toString()) }, initial = r.capacity.toString()),
                F.Choice("finish", "f_finish", opts(c, "fin_", "ms", "ss"), initial = r.finish),
                F.Choice("door", "f_door", opts(c, "door_", "auto", "manual"), initial = r.door),
                F.Choice("power3", "f_power3", yesNo(c), initial = if (r.power3) "yes" else "no"),
                F.Choice("job", "f_job", opts(c, "job_", "new", "replace"), initial = r.job),
            )
        }, { v, _ ->
            jsonOf("req" to mapOf("type" to v.s("type"), "stops" to (v.n("stops")?.toInt() ?: 0), "capacity" to (v.n("capacity")?.toInt() ?: 0), "finish" to v.s("finish"),
                "door" to v.s("door"), "power3" to (v.s("power3") == "yes"), "job" to v.s("job")))
        }),
        ActionSpec("QUOTE_CREATED", "make_quote", "📄", { c ->
            listOf(
                F.Text("discountPct", "f_discount", required = false, kind = F.Kind.NUMBER, initial = "0"),
                F.Text("reason", "f_discount_reason", required = false),
                F.Info({ v -> quotePreview(c, v.n("discountPct") ?: 0.0) }),
            )
        }, { v, _ -> jsonOf("discountPct" to (v.n("discountPct") ?: 0.0), "reason" to v.s("reason")) }),
        ActionSpec("QUOTE_DECISION", "approve_quote", "⏳", { c ->
            listOf(
                F.Info({ c.p?.lastQuote()?.let { q -> quoteText(c, q.list, q.discountPct, q.price, q.gst, q.total, q.margin, q.why) + "\n" + c.tr("f_discount_reason") + ": " + q.reason } }),
                F.Choice("decision", "f_decision", opts(c, "dec_", "approve", "reject")),
                F.Text("reason", "f_reason", required = false),
            )
        }, { v, _ -> jsonOf("decision" to v.s("decision"), "reason" to v.s("reason")) }),
        ActionSpec("QUOTE_SENT", "send_quote", "📤", { c ->
            listOf(F.Info({ c.p?.lastQuote()?.let { q -> c.tr("total") + ": " + Pricing.money(q.total) + " · " + c.tr("valid_days").replace("{n}", q.validity.toString()) } }),
                F.Choice("channel", "f_channel", opts(c, "ch_", "whatsapp", "print", "in_person", "email")))
        }, { v, _ -> jsonOf("channel" to v.s("channel")) }),
        ActionSpec("DEAL_WON", "deal_won", "🎉", { c -> listOf(F.Info({ c.p?.lastQuote()?.let { q -> c.tr("total") + ": " + Pricing.money(q.total) } })) }, { _, _ -> jsonOf() }),
        ActionSpec("DEAL_LOST", "deal_lost", "❌", { c ->
            listOf(F.Choice("reason", "f_lost_reason", LOST_REASONS.map { it to c.tr("lr_$it") }), F.Text("note", "f_note", required = false, multiline = true))
        }, { v, _ -> jsonOf("reason" to v.s("reason"), "note" to v.s("note")) }),
        ActionSpec("AGREEMENT_PREPARED", "prepare_agreement", "✍️", { c -> listOf(F.Info({ scheduleText(c) })) }, { _, _ -> jsonOf() }),
        ActionSpec("AGREEMENT_SIGNED", "sign_agreement", "✍️", { c ->
            listOf(F.Info({ scheduleText(c) }), F.Text("signer", "f_signer", initial = c.p?.contactName.orEmpty()), F.Sign("sig1", "customer_signature"))
        }, { v, _ -> jsonOf("signer" to v.s("signer"), "ev" to v.allEvidence()) }),
        ActionSpec("PAYMENT_RECORDED", "record_payment", "💳", { c ->
            val due = c.p?.schedule?.get(c.extra) ?: 0L
            listOf(
                F.Info({ c.tr("pay_" + c.extra) + " · " + c.tr("amount_due") + ": " + Pricing.money(due) }),
                F.Text("amount", "f_amount", kind = F.Kind.NUMBER, initial = if (due > 0) due.toString() else ""),
                F.Choice("method", "f_method", opts(c, "pm_", "upi", "bank", "cheque", "cash")),
                F.Text("ref", "f_ref", required = false),
            )
        }, { v, c -> jsonOf("payId" to "PAY" + Ids.rid(8), "purpose" to c.extra, "amount" to (v.n("amount") ?: 0.0), "method" to v.s("method"), "ref" to v.s("ref")) }),
        ActionSpec("PAYMENT_STATUS", "verify_payment", "🏦", { c ->
            val pay = c.p?.payments?.firstOrNull { it.id == c.extra }
            listOf(
                F.Info({ pay?.let { "${c.tr("pay_" + it.purpose)} · ${Pricing.money(it.amount)} · ${c.tr("pm_" + it.method)} · ${c.tr("f_ref")}: ${it.ref.ifEmpty { "—" }}\n${c.tr("recorded_by")}: ${c.s.user(it.recordedBy)?.name.orEmpty()}" } }),
                F.Choice("status", "f_bank_check", opts(c, "ps_", "confirmed", "not_found")),
                F.Text("ref", "f_bank_ref", required = false),
                F.Text("reason", "f_reason", required = false),
            )
        }, { v, c -> jsonOf("payId" to c.extra, "status" to v.s("status"), "ref" to v.s("ref"), "reason" to v.s("reason")) }),
        ActionSpec("PREP_ITEM_DONE", "prepare_site", "🧱", { c ->
            val open = PREP_ITEMS.filter { it.first !in (c.p?.prep?.keys ?: emptySet()) }
            listOf(
                F.Choice("item", "f_prep_item", open.map { it.first to (it.second + " " + c.tr("prep_" + it.first)) }, initial = open.firstOrNull()?.first),
                F.Info({ v -> v.s("item").takeIf { it.isNotEmpty() }?.let { c.tr("prep_hint_$it") } }),
                F.Photos("photo_proof", "prep", 1), F.Text("note", "f_note", required = false),
            )
        }, { v, _ -> jsonOf("item" to v.s("item"), "note" to v.s("note"), "ev" to v.allEvidence()) }),
        ActionSpec("QC_REQUESTED", "request_qc", "🔍", { c -> listOf(F.Info({ c.tr("request_qc_info") })) }, { _, _ -> jsonOf() }),
        ActionSpec("QC_SITE_RESULT", "inspect_site", "🔍", { c ->
            listOf(
                F.Text("width", "meas_width", kind = F.Kind.NUMBER), F.Text("depth", "meas_depth", kind = F.Kind.NUMBER),
                F.Text("pit", "meas_pit", kind = F.Kind.NUMBER), F.Text("head", "meas_head", kind = F.Kind.NUMBER),
                F.Info({ c.tr("meas_min").replace("{w}", c.s.config.tolWidth.toString()).replace("{d}", c.s.config.tolDepth.toString()).replace("{p}", c.s.config.tolPit.toString()).replace("{h}", c.s.config.tolHead.toString()) }),
                F.Checks("checks", "f_checks", QC_SITE_CHECKS, "qcs_"),
                F.Choice("outcome", "f_outcome", opts(c, "qso_", "approved", "corrections")),
                F.Lines("items", "f_corrections"),
                F.Photos("photo_proof", "qc_site", 2),
            )
        }, { v, _ ->
            jsonOf("outcome" to v.s("outcome"), "meas" to mapOf("width" to v.n("width")?.toInt(), "depth" to v.n("depth")?.toInt(), "pit" to v.n("pit")?.toInt(), "head" to v.n("head")?.toInt()),
                "checks" to (v["checks"] as? Map<*, *> ?: emptyMap<String, String>()), "items" to lines(v.s("items")), "ev" to v.allEvidence())
        }),
        ActionSpec("CORRECTION_DONE", "fix_corrections", "🛠️", { c ->
            val scope = if (c.me.role == Role.TECH) "install" else "site"
            val open = c.p?.openCorrections(scope).orEmpty()
            listOf(F.Choice("cid", "f_correction", open.map { it.id to it.text }, initial = open.firstOrNull()?.id), F.Photos("photo_proof", "fix", 1), F.Text("note", "f_note", required = false))
        }, { v, _ -> jsonOf("cid" to v.s("cid"), "note" to v.s("note"), "ev" to v.allEvidence()) }),
        ActionSpec("RELEASED", "release_material", "📦", { c ->
            listOf(F.Person("supplier", "f_supplier", Role.SUPPLIER), F.Person("tech", "f_technician", Role.TECH), F.Text("drawings", "f_drawings"),
                F.Info({ c.p?.req?.let { r -> Pricing.materials(r).joinToString("\n") { (k, q) -> "• " + c.tr(k) + " × " + q } } }))
        }, { v, _ -> jsonOf("supplier" to v.s("supplier"), "tech" to v.s("tech"), "drawings" to v.s("drawings")) }),
        ActionSpec("DISPATCHED", "dispatch_material", "🚚", { _ ->
            listOf(F.Text("seal", "f_seal"), F.Text("vehicle", "f_vehicle", required = false), F.Text("serials", "f_serials", required = false, multiline = true), F.Photos("photo_packed", "dispatch", 1))
        }, { v, _ -> jsonOf("seal" to v.s("seal"), "vehicle" to v.s("vehicle"), "serials" to v.s("serials"), "ev" to v.allEvidence()) }),
        ActionSpec("ARRIVED", "confirm_arrival", "📍", { c ->
            listOf(
                F.Gps("f_gps"), F.Text("sealSeen", "f_seal_seen"),
                F.Choice("sealIntact", "f_seal_intact", yesNo(c)), F.Choice("condition", "f_condition", opts(c, "cond_", "ok", "damaged")),
                F.Text("note", "f_note", required = false, multiline = true), F.Photos("photo_arrival", "arrival", 1),
            )
        }, { v, _ -> jsonOf("sealSeen" to v.s("sealSeen"), "sealIntact" to v.s("sealIntact"), "condition" to v.s("condition"), "note" to v.s("note"), "gps" to v["gps"] as? GeoPoint, "ev" to v.allEvidence()) }),
        ActionSpec("INCIDENT_RESOLVED", "resolve_incident", "🚨", { c ->
            listOf(F.Info({ c.p?.incidents?.firstOrNull { it.id == c.extra }?.let { c.tr("inc_" + it.type) + " · " + it.note } }, warn = true), F.Text("note", "f_resolution", multiline = true))
        }, { v, c -> jsonOf("iid" to c.extra, "note" to v.s("note")) }),
        ActionSpec("MATERIAL_HANDED", "hand_material", "🤝", { c ->
            listOf(
                F.Info({ c.tr("mh_info") + "\n" + c.p?.materials.orEmpty().joinToString("\n") { (k, q) -> "☑ " + c.tr(k) + " × " + q } }),
                F.Text("custName", "f_received_by", initial = c.p?.contactName.orEmpty()), F.Photos("photo_storage", "storage", 1),
                F.Sign("sig1", "customer_signature"), F.Sign("sig2", "tech_signature"),
            )
        }, { v, _ -> jsonOf("custName" to v.s("custName"), "ev" to v.allEvidence()) }),
        ActionSpec("SOP_STEP_DONE", "do_step", "🔧", { c ->
            val i = c.extra.toIntOrNull() ?: 0
            listOf(F.Info({ "${i + 1}/12 · ${SOP_STEPS.getOrNull(i)?.second.orEmpty()} ${c.tr("sop_" + SOP_STEPS.getOrNull(i)?.first)}\n${c.tr("sop_hint_" + SOP_STEPS.getOrNull(i)?.first)}" }),
                F.Photos("photo_work", "sop$i", 1), F.Text("note", "f_note", required = false))
        }, { v, c -> jsonOf("step" to (c.extra.toIntOrNull() ?: 0), "note" to v.s("note"), "ev" to v.allEvidence()) }),
        ActionSpec("INSPECTION_REQUESTED", "request_inspection", "🔍", { c -> listOf(F.Info({ c.tr("request_inspection_info") })) }, { _, _ -> jsonOf() }),
        ActionSpec("QC_FINAL_RESULT", "final_inspection", "🔍", { c ->
            listOf(F.Checks("checks", "f_checks", QC_FINAL_CHECKS, "qcf_"), F.Choice("outcome", "f_outcome", opts(c, "qfo_", "pass", "fail")), F.Lines("items", "f_corrections"), F.Photos("photo_proof", "qc_final", 1))
        }, { v, _ -> jsonOf("outcome" to v.s("outcome"), "checks" to (v["checks"] as? Map<*, *> ?: emptyMap<String, String>()), "items" to lines(v.s("items")), "ev" to v.allEvidence()) }),
        ActionSpec("HANDOVER_SIGNED", "sign_handover", "✍️", { c ->
            val slot = c.extra
            buildList {
                add(F.Info({ c.tr("slot_" + slot) }))
                add(F.Text("signer", "f_signer", initial = if (slot == "customer") c.p?.contactName.orEmpty() else c.me.name))
                if (slot == "customer" && c.me.role == Role.TECH) add(F.Choice("why", "f_why_here", opts(c, "why_", "no_app", "phone_broken", "prefers_paper")))
                add(F.Sign("sig1", "signature"))
            }
        }, { v, c -> jsonOf("slot" to c.extra, "signer" to v.s("signer"), "why" to v.s("why"), "ev" to v.allEvidence()) }),
        ActionSpec("HANDOVER_COMPLETED", "complete_handover", "🔑", { c -> listOf(F.Info({ c.tr("handover_info").replace("{m}", c.s.config.warrantyMonths.toString()).replace("{n}", c.s.config.amcVisits.toString()) })) }, { _, _ -> jsonOf() }),
        ActionSpec("TICKET_OPENED", "raise_ticket", "🎫", { c ->
            listOf(F.Choice("urgency", "f_urgency", opts(c, "urg_", "trapped", "unsafe", "stopped", "ordinary")), F.Info({ v -> if (v.s("urgency") == "trapped") c.tr("trapped_call_first") else null }, warn = true), F.Text("note", "f_problem", multiline = true))
        }, { v, _ -> jsonOf("tid" to "TK" + Ids.rid(6), "urgency" to v.s("urgency"), "kind" to "breakdown", "note" to v.s("note")) }),
        ActionSpec("TICKET_ASSIGNED", "assign_ticket", "👷", { c ->
            listOf(F.Info({ c.p?.tickets?.firstOrNull { it.tid == c.extra }?.let { c.tr("urg_" + it.urgency) + " · " + it.note } }), F.Person("uid", "f_technician", Role.TECH))
        }, { v, c -> jsonOf("tid" to c.extra, "uid" to v.s("uid")) }),
        ActionSpec("TICKET_UPDATED", "fix_ticket", "🛠️", { c ->
            listOf(F.Info({ c.p?.tickets?.firstOrNull { it.tid == c.extra }?.let { c.tr("urg_" + it.urgency) + " · " + it.note } }), F.Text("note", "f_what_fixed", multiline = true), F.Photos("photo_proof", "ticket", 1))
        }, { v, c -> jsonOf("tid" to c.extra, "note" to v.s("note"), "ev" to v.allEvidence()) }),
        ActionSpec("TICKET_CLOSED", "close_ticket", "✅", { c ->
            listOf(F.Info({ c.p?.tickets?.firstOrNull { it.tid == c.extra }?.let { c.tr("fixed_note") + ": " + it.fixNote } }), F.Choice("ok", "f_working_now", yesNo(c)), F.Text("note", "f_note", required = false))
        }, { v, c -> jsonOf("tid" to c.extra, "ok" to (v.s("ok") == "yes"), "note" to v.s("note")) }),
        ActionSpec("SOS_CLOSED", "sos_close", "🆘", { c ->
            listOf(F.Info({ c.s.sos[c.extra]?.let { a -> c.tr("sos_active").replace("{n}", c.s.user(a.by)?.name.orEmpty()) } }, warn = true), F.Text("note", "f_person_safe_note", multiline = true))
        }, { v, c -> jsonOf("sid" to c.extra, "note" to v.s("note")) }),
        ActionSpec("USER_ADDED", "add_person", "👤", { c ->
            val roles = when (c.me.role) {
                Role.OWNER -> listOf(Role.ADMIN, Role.FIELD, Role.SALES, Role.FINANCE, Role.QC, Role.SUPPLIER, Role.TECH, Role.CUSTOMER)
                Role.ADMIN -> listOf(Role.FIELD, Role.SALES, Role.FINANCE, Role.QC, Role.SUPPLIER, Role.TECH, Role.CUSTOMER)
                else -> listOf(Role.CUSTOMER)
            }
            listOf(
                F.Text("name", "f_name"), F.Text("phone", "f_phone", required = false, kind = F.Kind.PHONE),
                F.Choice("role", "f_role", roles.map { it.key to (it.icon + " " + c.tr("r_" + it.key)) }),
                F.Choice("projectId", "f_customer_job", c.s.projects.values.filter { it.customer == null && it.stage.ordinal >= com.aiec.elevatoros.domain.Stage.WON.ordinal && it.stage != com.aiec.elevatoros.domain.Stage.LOST }.map { it.id to (it.no + " · " + it.contactName) }, required = false),
                F.Text("pin", "f_new_pin", kind = F.Kind.PIN),
                F.Info({ c.tr("pin_tell_person") }),
            )
        }, { v, _ ->
            val salt = Ids.rid(10); val uid = Ids.uid()
            jsonOf("uid" to uid, "name" to v.s("name"), "phone" to v.s("phone"), "role" to v.s("role"), "projectId" to v.s("projectId").ifEmpty { null },
                "pin" to if (Pin.valid(v.s("pin"))) Pin.hash(v.s("pin"), salt) else "", "salt" to salt, "pinLen" to v.s("pin").length)
        }, "saved_person"),
    ).associateBy { it.type }

    private fun lines(s: String) = s.lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun dups(s: State, v: Values): List<Project> {
        val ph = phone10(v.s("phone")); val gps = v["gps"] as? GeoPoint
        return s.projects.values.filter { p ->
            p.stage != com.aiec.elevatoros.domain.Stage.LOST && ((ph.isNotEmpty() && p.contactPhone == ph) || (gps != null && p.gps != null && haversine(gps, p.gps!!) < 80))
        }
    }

    private fun dupText(c: Ctx, v: Values): String? = dups(c.s, v).takeIf { it.isNotEmpty() }?.let { l -> c.tr("dup_warning") + "\n" + l.joinToString("\n") { "• " + it.no + " · " + it.contactName + " · " + c.tr("st_" + it.stage.key) } }

    private fun leadSummary(c: Ctx): String? = c.p?.let { p ->
        listOfNotNull(
            p.contactName.takeIf { it.isNotBlank() }?.let { "👤 $it" }, p.contactPhone.takeIf { it.isNotBlank() }?.let { "📞 $it" },
            p.address.takeIf { it.isNotBlank() }?.let { "📍 $it" }, p.gps?.let { "🛰️ %.5f, %.5f ±%dm".format(it.lat, it.lon, (it.acc ?: 0.0).toInt()) },
            p.floors?.let { c.tr("f_floors") + ": $it" }, "📷 " + p.evidence.size,
            p.dupOf.takeIf { it.isNotEmpty() }?.let { "⚠️ " + c.tr("dup_warning") }
        ).joinToString("\n")
    }

    fun quotePreview(c: Ctx, disc: Double): String? {
        val req = c.p?.req ?: return null
        val q = Pricing.quote(c.s.config, req, disc)
        return quoteText(c, q.list, q.discountPct, q.price, q.gst, q.total, q.margin, q.why)
    }

    private fun quoteText(c: Ctx, list: Long, d: Double, price: Long, gst: Long, total: Long, margin: Double, why: List<String>): String {
        val office = c.me.role.office
        return buildString {
            append(c.tr("list_price")).append(": ").append(Pricing.money(list)).append('\n')
            append(c.tr("discount")).append(": ").append(d.toString().removeSuffix(".0")).append("%\n")
            append(c.tr("price_before_tax")).append(": ").append(Pricing.money(price)).append('\n')
            append("GST: ").append(Pricing.money(gst)).append('\n')
            append(c.tr("total")).append(": ").append(Pricing.money(total))
            if (office) append('\n').append(c.tr("margin")).append(": ").append(margin).append('%')
            if (why.isNotEmpty()) append("\n⏳ ").append(c.tr("needs_approval")).append(": ").append(why.joinToString(", ") { c.tr("why_$it") })
        }
    }

    private fun scheduleText(c: Ctx): String? = c.p?.schedule?.takeIf { it.isNotEmpty() }?.entries?.joinToString("\n") { (k, v) -> "• " + c.tr("pay_$k") + ": " + Pricing.money(v) + " (" + c.tr("pw_$k") + ")" }
}
