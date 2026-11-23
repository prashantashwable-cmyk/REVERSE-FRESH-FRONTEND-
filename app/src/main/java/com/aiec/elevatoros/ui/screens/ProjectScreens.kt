package com.aiec.elevatoros.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aiec.elevatoros.domain.Pricing
import com.aiec.elevatoros.domain.Project
import com.aiec.elevatoros.domain.Role
import com.aiec.elevatoros.domain.Rules
import com.aiec.elevatoros.domain.SOP_STEPS
import com.aiec.elevatoros.domain.Stage
import com.aiec.elevatoros.domain.Tasks
import com.aiec.elevatoros.domain.Time
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.i18n.LocalLang
import com.aiec.elevatoros.i18n.t
import com.aiec.elevatoros.ui.AppViewModel
import com.aiec.elevatoros.ui.Screen
import com.aiec.elevatoros.ui.Speech
import com.aiec.elevatoros.ui.components.AiecCard
import com.aiec.elevatoros.ui.components.Avatar
import com.aiec.elevatoros.ui.components.ChoiceChips
import com.aiec.elevatoros.ui.components.InfoBox
import com.aiec.elevatoros.ui.components.KeyValue
import com.aiec.elevatoros.ui.components.PhaseStepper
import com.aiec.elevatoros.ui.components.Pill
import com.aiec.elevatoros.ui.components.SecondaryButton
import com.aiec.elevatoros.ui.components.SectionTitle
import com.aiec.elevatoros.ui.components.TwoBars
import java.time.Instant
import java.time.format.DateTimeFormatter

private val DT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm").withZone(Time.IST)
fun fmt(iso: String?): String = iso?.let { runCatching { DT.format(Instant.parse(it)) }.getOrDefault(it) } ?: ""

@Composable
fun ProjectsScreen(vm: AppViewModel, speech: Speech) {
    val me = vm.me ?: return
    val s = vm.state
    speech.text = I18n.t(LocalLang.current, "projects_say")
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("open") }
    val visible = s.projects.values.filter { Rules.canSee(s, me, it) }
        .filter { when (filter) { "open" -> it.stage != Stage.LOST && it.stage != Stage.AMC; "done" -> it.stage == Stage.AMC; "lost" -> it.stage == Stage.LOST; else -> true } }
        .filter { q.isBlank() || it.no.contains(q, true) || it.contactName.contains(q, true) || it.contactPhone.contains(q) || it.address.contains(q, true) }
        .sortedByDescending { it.since }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(q, { q = it }, label = { Text("🔎 " + t("search_jobs")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
        Spacer(Modifier.height(8.dp))
        ChoiceChips(listOf("open" to t("f_open"), "done" to t("f_handed"), "lost" to t("f_lost"), "all" to t("f_all")), filter) { filter = it }
        if (visible.isEmpty()) InfoBox(t("no_jobs"))
        LazyColumn { items(visible, key = { it.id }) { p -> ProjectRow(vm, p) } }
    }
}

@Composable
fun ProjectRow(vm: AppViewModel, p: Project) {
    val outsider = vm.me?.role?.outsider == true
    val (prog, _) = Tasks.progress(p)
    AiecCard(onClick = { vm.go(Screen.ProjectDetail(p.id)) }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.stage.icon)
                Column(Modifier.weight(1f)) {
                    Text(p.no, style = MaterialTheme.typography.titleMedium)
                    // technicians and suppliers never see customer names (original data-protection rule)
                    Text(if (outsider) p.address.substringAfterLast(",").trim().ifBlank { "—" } else p.contactName.ifBlank { p.address }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Pill(t("st_" + p.stage.key), MaterialTheme.colorScheme.primary)
            }
            val (days, _) = Tasks.escalation(vm.state, p, Time.now())
            TwoBars(prog, t("bar_job", "pct" to (prog * 100).toInt().toString()), days, vm.state.config.escalateDays, paused = p.stage == Stage.LOST, done = p.stage == Stage.AMC)
        }
    }
}

@Composable
fun ProjectDetailScreen(vm: AppViewModel, pid: String, speech: Speech) {
    val me = vm.me ?: return
    val s = vm.state
    val p = s.projects[pid] ?: return
    val lang = LocalLang.current
    if (!Rules.canSee(s, me, p)) { InfoBox(t("err_access"), MaterialTheme.colorScheme.errorContainer, "⛔"); return }
    val outsider = me.role.outsider
    val money = me.role in setOf(Role.OWNER, Role.ADMIN, Role.SALES, Role.FINANCE, Role.CUSTOMER)
    val needs = Tasks.needs(s, p)
    val nextRoles = Tasks.nextRoles(s, p)
    speech.text = I18n.t(lang, "st_" + p.stage.key) + ". " + (needs.firstOrNull()?.let { I18n.t(lang, "t_" + it.key) } ?: "")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        AiecCard {
            Column {
                Text(p.no, style = MaterialTheme.typography.titleLarge)
                if (!outsider) Text(p.contactName + (if (p.contactPhone.isNotBlank() && me.role != Role.CUSTOMER) " · " + p.contactPhone else ""), style = MaterialTheme.typography.bodyLarge)
                Text("📍 " + p.address.ifBlank { p.gps?.let { "%.5f, %.5f".format(it.lat, it.lon) } ?: "—" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp)); PhaseStepper(p.stage)
                Spacer(Modifier.height(8.dp))
                Pill(p.stage.icon + " " + t("st_" + p.stage.key), MaterialTheme.colorScheme.primary)
                needs.firstOrNull()?.let { n ->
                    Text("👉 " + t("next_line", "who" to nextRoles.joinToString(" / ") { I18n.t(lang, "r_" + it.key) }, "what" to t("t_" + n.key)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                }
                val (prog, kind) = Tasks.progress(p)
                val (days, _) = Tasks.escalation(s, p, Time.now())
                TwoBars(prog, if (kind == "sop") t("bar_steps", "n" to p.sop.size.toString(), "m" to "12") else t("bar_job", "pct" to (prog * 100).toInt().toString()), days, s.config.escalateDays, done = p.stage == Stage.AMC, paused = p.stage == Stage.LOST)
            }
        }
        // actions this person may do now: the same engine decides buttons and rules
        val mine = needs.filter { Tasks.isMine(s, me, it) }
        if (mine.isNotEmpty()) {
            SectionTitle(t("your_actions"))
            mine.forEach { n -> SecondaryButton(t("t_" + n.key) + extraLabel(n.key, n.extra, p), "▶") { vm.go(Screen.Action(n.action, p.id, n.extra)) }; Spacer(Modifier.height(6.dp)) }
        }
        val side = buildList {
            if (Rules.allowed(s, me.uid, "LEAD_RETURNED", p.id)) add("LEAD_RETURNED")
            if (Rules.allowed(s, me.uid, "DEAL_LOST", p.id)) add("DEAL_LOST")
            if (Rules.allowed(s, me.uid, "CONTACT_LOGGED", p.id) && mine.none { it.action == "CONTACT_LOGGED" }) add("CONTACT_LOGGED")
            if (Rules.allowed(s, me.uid, "REQ_SAVED", p.id) && mine.none { it.action == "REQ_SAVED" }) add("REQ_SAVED")
            if (Rules.allowed(s, me.uid, "QUOTE_CREATED", p.id) && mine.none { it.action == "QUOTE_CREATED" }) add("QUOTE_CREATED")
            if (Rules.allowed(s, me.uid, "TICKET_OPENED", p.id)) add("TICKET_OPENED")
        }
        if (side.isNotEmpty()) {
            SectionTitle(t("other_actions"))
            side.forEach { a -> SecondaryButton(t(com.aiec.elevatoros.ui.Actions.SPECS.getValue(a).title), com.aiec.elevatoros.ui.Actions.SPECS.getValue(a).icon) { vm.go(Screen.Action(a, p.id)) }; Spacer(Modifier.height(6.dp)) }
        }
        SectionTitle(t("details"))
        AiecCard {
            Column {
                p.floors?.let { KeyValue(t("f_floors"), it.toString()) }
                p.req?.let { r -> KeyValue(t("lift_needs"), "${t("lt_" + r.type)} · ${r.stops} ${t("stops")} · ${t("persons").replace("{n}", r.capacity.toString())} · ${t("fin_" + r.finish)}") }
                if (money) p.lastQuote()?.let { q -> KeyValue(t("total"), Pricing.money(q.total) + " (v${q.v})") }
                if (money && p.schedule.isNotEmpty()) p.schedule.forEach { (k, v) ->
                    val st = when { p.payConfirmed(k) -> "✅"; p.payPending(k) -> "⏳"; else -> "○" }
                    KeyValue(t("pay_$k"), "$st ${Pricing.money(v)}")
                }
                s.user(p.sales)?.let { KeyValue(t("r_sales"), it.name) }
                s.user(p.tech)?.let { KeyValue(t("r_tech"), it.name) }
                s.user(p.supplier)?.let { KeyValue(t("r_supplier"), it.name) }
                s.user(p.qc)?.let { KeyValue(t("r_qc"), it.name) }
                if (p.drawings.isNotBlank()) KeyValue(t("f_drawings"), p.drawings)
                if (p.stage == Stage.INSTALLING || p.sop.isNotEmpty()) KeyValue(t("installation"), "${p.sop.size}/${SOP_STEPS.size}")
                p.warrantyEnd?.let { KeyValue(t("warranty_until"), it) }
                p.liftCode?.let { KeyValue(t("lift_code"), it) }
                if (p.amcVisits.isNotEmpty()) KeyValue(t("amc_visits"), p.amcVisits.joinToString(", "))
                KeyValue(t("photos"), p.evidence.count { it.kind == "photo" }.toString() + " · ✍️ " + p.evidence.count { it.kind == "sign" })
            }
        }
        p.incidents.filter { it.open }.forEach { InfoBox(t("inc_" + it.type) + " · " + it.note, MaterialTheme.colorScheme.errorContainer, "🚨") }
        p.corrections.filter { it.done == null }.forEach { InfoBox(it.text, icon = "🛠️") }
        p.tickets.forEach { tk -> InfoBox("${t("urg_" + tk.urgency)} · ${tk.note} · ${t("tk_" + tk.status)}", icon = "🎫") }
        SectionTitle(t("history"))
        AiecCard {
            Column {
                p.log.asReversed().mapNotNull { s.byId[it] }.forEach { ev ->
                    // customers see only customer-facing steps; nobody outside the office sees internal reasons
                    if (me.role == Role.CUSTOMER && ev.type in setOf("QUOTE_DECISION", "LEAD_RETURNED", "LEAD_ACCEPTED", "RELEASED", "INCIDENT_RESOLVED")) return@forEach
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(t("ev_" + ev.type), style = MaterialTheme.typography.bodyMedium)
                            Text(fmt(ev.t) + " · " + (s.user(ev.by)?.let { if (outsider && it.role == Role.CUSTOMER) t("r_customer") else it.name } ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun extraLabel(key: String, extra: String, p: Project): String = when (key) {
    "do_step" -> extra.toIntOrNull()?.let { " · ${it + 1}. " + t("sop_" + SOP_STEPS[it].first) } ?: ""
    "record_payment" -> " · " + t("pay_$extra")
    "sign_handover" -> " · " + t("slot_$extra")
    "verify_payment" -> p.payments.firstOrNull { it.id == extra }?.let { " · " + t("pay_" + it.purpose) } ?: ""
    else -> ""
}
