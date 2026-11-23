package com.aiec.elevatoros.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiec.elevatoros.device.GpsResult
import com.aiec.elevatoros.domain.Ids
import com.aiec.elevatoros.domain.Role
import com.aiec.elevatoros.domain.Rules
import com.aiec.elevatoros.domain.SOP_STEPS
import com.aiec.elevatoros.domain.Stage
import com.aiec.elevatoros.domain.Task
import com.aiec.elevatoros.domain.Tasks
import com.aiec.elevatoros.domain.Time
import com.aiec.elevatoros.domain.jsonOf
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.i18n.LocalLang
import com.aiec.elevatoros.i18n.t
import com.aiec.elevatoros.ui.AppViewModel
import com.aiec.elevatoros.ui.Screen
import com.aiec.elevatoros.ui.Speech
import com.aiec.elevatoros.ui.components.AiecCard
import com.aiec.elevatoros.ui.components.Avatar
import com.aiec.elevatoros.ui.components.BigButton
import com.aiec.elevatoros.ui.components.InfoBox
import com.aiec.elevatoros.ui.components.PhaseStepper
import com.aiec.elevatoros.ui.components.Pill
import com.aiec.elevatoros.ui.components.SecondaryButton
import com.aiec.elevatoros.ui.components.SectionTitle
import com.aiec.elevatoros.ui.components.TwoBars
import com.aiec.elevatoros.ui.theme.Aiec
import kotlinx.coroutines.launch

fun taskScreen(task: Task) = Screen.Action(task.need.action, task.need.pid, task.need.extra)

@Composable
fun HomeScreen(vm: AppViewModel, speech: Speech) {
    val me = vm.me ?: return
    val s = vm.state
    val lang = LocalLang.current
    val now = Time.now()
    val tasks = Tasks.tasksFor(s, me, now)
    speech.text = if (tasks.isEmpty()) I18n.t(lang, "no_work") else I18n.t(lang, "you_have_tasks", mapOf("n" to tasks.size.toString())) + ". " + I18n.t(lang, "t_" + tasks.first().need.key)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        // greeting
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Aiec.Navy).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(me.role.icon, fontSize = 34.sp, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text(t("namaste", "n" to me.name), color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(t("r_" + me.role.key) + " · " + (s.org?.name ?: ""), color = Aiec.Gold, style = MaterialTheme.typography.bodyMedium)
            }
        }
        // open SOS alarms are shown to everyone who can act, on top of everything
        s.sos.values.filter { it.open }.forEach { a ->
            Spacer(Modifier.height(10.dp))
            InfoBox(t("sos_active", "n" to (s.user(a.by)?.name ?: "")) + (a.gps?.let { " · %.5f, %.5f".format(it.lat, it.lon) } ?: ""), Color(0xFFFFDAD6), "🆘")
            if (me.role.office) SecondaryButton(t("sos_close"), "✅") { vm.go(Screen.Action("SOS_CLOSED", null, a.sid)) }
        }
        Spacer(Modifier.height(12.dp))
        if (me.role == Role.CUSTOMER) CustomerLift(vm)
        if (tasks.isNotEmpty()) {
            BigButton(t("start_next_task"), "▶") { vm.go(taskScreen(tasks.first())) }
            Text(t("t_" + tasks.first().need.key) + " · " + tasks.first().project.no, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, start = 4.dp))
        } else {
            AiecCard { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("☕", fontSize = 40.sp); Text(t("no_work"), style = MaterialTheme.typography.titleMedium); Text(t("no_work_hint"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
        if (Rules.allowed(s, me.uid, "LEAD_CREATED", null)) { Spacer(Modifier.height(10.dp)); SecondaryButton(t("add_site"), "➕") { vm.go(Screen.Action("LEAD_CREATED", null)) } }
        if (me.role.office) OfficeStrip(vm)
        if (tasks.isNotEmpty()) {
            SectionTitle(t("my_tasks", "n" to tasks.size.toString()))
            tasks.forEach { TaskCard(vm, it) }
        }
        if (me.role != Role.CUSTOMER && me.role != Role.OWNER && me.role != Role.ADMIN && me.role != Role.FINANCE && me.role != Role.SALES) { Spacer(Modifier.height(16.dp)); SosButton(vm) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TaskCard(vm: AppViewModel, task: Task) {
    val p = task.project
    val (prog, kind) = Tasks.progress(p)
    val label = when (kind) {
        "sop" -> t("bar_steps", "n" to p.sop.size.toString(), "m" to SOP_STEPS.size.toString())
        else -> t("bar_job", "pct" to (prog * 100).toInt().toString())
    }
    AiecCard(onClick = { vm.go(taskScreen(task)) }, highlight = task.level >= 2) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.stage.icon)
                Column(Modifier.weight(1f)) {
                    Text(t("t_" + task.need.key), style = MaterialTheme.typography.titleMedium)
                    Text(p.no + " · " + p.contactName.ifBlank { p.address }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                when (task.level) {
                    3 -> Pill("🔴 " + t("lvl_hq"), Aiec.Late)
                    2 -> Pill("⚠️ " + t("lvl_admin"), Aiec.Warn)
                    1 -> Pill("⏰ " + t("lvl_remind"), Aiec.GoldDeep)
                }
            }
            TwoBars(prog, label, task.daysWaiting, task.limit)
        }
    }
}

@Composable
private fun OfficeStrip(vm: AppViewModel) {
    val s = vm.state
    val esc = Tasks.escalations(s, Time.now())
    val payments = s.projects.values.sumOf { p -> p.payments.count { it.status == "pending" } }
    val open = s.projects.values.count { it.stage != Stage.LOST && it.stage != Stage.AMC }
    SectionTitle(t("needs_attention"))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("📋", t("open_jobs"), open.toString(), Modifier.weight(1f)) { vm.tab(Screen.Projects) }
        StatTile("🏦", t("payments_to_check"), payments.toString(), Modifier.weight(1f)) { vm.tab(Screen.Projects) }
        StatTile("⏫", t("escalations"), esc.size.toString(), Modifier.weight(1f), alert = esc.isNotEmpty()) { vm.go(Screen.Escalations) }
    }
}

@Composable
fun StatTile(icon: String, label: String, value: String, modifier: Modifier, alert: Boolean = false, onClick: () -> Unit) {
    AiecCard(modifier, onClick = onClick, highlight = alert) {
        Column(Modifier.fillMaxWidth().heightIn(min = 84.dp)) {
            Text(icon, fontSize = 20.sp)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = if (alert) Aiec.Late else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2)
        }
    }
}

@Composable
private fun CustomerLift(vm: AppViewModel) {
    val me = vm.me ?: return
    val p = vm.state.projects.values.firstOrNull { it.customer == me.uid } ?: return
    AiecCard(onClick = { vm.go(Screen.ProjectDetail(p.id)) }) {
        Column {
            Text(p.no + " · " + p.address, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp)); PhaseStepper(p.stage)
            Pill(p.stage.icon + " " + t("st_" + p.stage.key), MaterialTheme.colorScheme.primary, Modifier.padding(top = 8.dp))
        }
    }
    Spacer(Modifier.height(10.dp))
}

/** SOS: confirm, record GPS (or none) and the job, then offer the rescue number and 112. Calling comes first. */
@Composable
fun SosButton(vm: AppViewModel) {
    var ask by remember { mutableStateOf(false) }
    var raised by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    Button(onClick = { ask = true }, colors = ButtonDefaults.buttonColors(containerColor = Aiec.Late), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)) {
        Text("🆘 " + t("sos"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
    if (ask) AlertDialog(
        onDismissRequest = { ask = false },
        title = { Text("🆘 " + t("sos")) },
        text = { Text(if (raised) t("sos_sent") else t("sos_confirm")) },
        confirmButton = {
            Column {
                if (!raised) TextButton(onClick = {
                    scope.launch {
                        val g = (vm.gps.current(10_000) as? GpsResult.Fix)?.point
                        val pid = vm.state.projects.values.firstOrNull { it.tech == vm.sessionUid && it.stage == Stage.INSTALLING }?.id
                        vm.perform("SOS_RAISED", null, jsonOf("sid" to "SOS" + Ids.rid(6), "gps" to g, "pid" to pid))
                        raised = true
                    }
                }) { Text(t("sos_send"), color = Aiec.Late, fontWeight = FontWeight.Bold) }
                val rescue = vm.state.config.sosNumber
                if (rescue.isNotBlank()) TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$rescue"))) }) { Text("📞 " + t("call_rescue") + " $rescue") }
                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112"))) }) { Text("📞 112") }
            }
        },
        dismissButton = { TextButton(onClick = { ask = false; raised = false }) { Text(t("close")) } }
    )
}
