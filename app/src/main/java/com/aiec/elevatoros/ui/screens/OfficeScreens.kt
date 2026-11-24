package com.aiec.elevatoros.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.aiec.elevatoros.BuildConfig
import com.aiec.elevatoros.data.EventDb
import com.aiec.elevatoros.domain.Config
import com.aiec.elevatoros.domain.Role
import com.aiec.elevatoros.domain.Rules
import com.aiec.elevatoros.domain.Tasks
import com.aiec.elevatoros.domain.Time
import com.aiec.elevatoros.domain.jsonOf
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.i18n.LocalLang
import com.aiec.elevatoros.i18n.errText
import com.aiec.elevatoros.i18n.t
import com.aiec.elevatoros.ui.AppViewModel
import com.aiec.elevatoros.ui.Screen
import com.aiec.elevatoros.ui.Speech
import com.aiec.elevatoros.ui.components.AiecCard
import com.aiec.elevatoros.ui.components.Avatar
import com.aiec.elevatoros.ui.components.BigButton
import com.aiec.elevatoros.ui.components.ChoiceChips
import com.aiec.elevatoros.ui.components.InfoBox
import com.aiec.elevatoros.ui.components.KeyValue
import com.aiec.elevatoros.ui.components.SecondaryButton
import com.aiec.elevatoros.ui.components.SectionTitle
import com.aiec.elevatoros.ui.theme.Aiec
import java.io.File

@Composable
fun SavedScreen(vm: AppViewModel, screen: Screen.Saved, speech: Speech) {
    val lang = LocalLang.current
    val p = screen.pid?.let { vm.state.projects[it] }
    val next = p?.let { Tasks.needs(vm.state, it).firstOrNull() }
    val nextRoles = p?.let { Tasks.nextRoles(vm.state, it) }.orEmpty()
    speech.text = I18n.t(lang, screen.messageKey)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("✅", fontSize = 64.sp)
        Text(t("done"), style = MaterialTheme.typography.headlineMedium)
        AiecCard {
            Column {
                Text("✔️ " + t("what_happened"), style = MaterialTheme.typography.titleMedium)
                Text(t(screen.messageKey) + (p?.let { " · " + it.no } ?: ""), style = MaterialTheme.typography.bodyLarge)
                if (next != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("👉 " + t("what_next"), style = MaterialTheme.typography.titleMedium)
                    Text(t("next_line", "who" to nextRoles.joinToString(" / ") { I18n.t(lang, "r_" + it.key) }, "what" to t("t_" + next.key)), style = MaterialTheme.typography.bodyLarge)
                }
                Text("💾 " + t("sync_pending_note"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (p != null) SecondaryButton(t("open_project"), "📋") { vm.resetTo(Screen.Home); vm.go(Screen.ProjectDetail(p.id)) }
        Spacer(Modifier.height(8.dp))
        BigButton(t("home"), "🏠") { vm.resetTo(Screen.Home) }
    }
}

@Composable
fun PeopleScreen(vm: AppViewModel, speech: Speech) {
    val me = vm.me ?: return
    speech.text = I18n.t(LocalLang.current, "people_say")
    var confirm by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (Rules.allowed(vm.state, me.uid, "USER_ADDED", null)) BigButton(t("add_person"), "➕") { vm.go(Screen.Action("USER_ADDED", null)) }
        // phones waiting for approval: their records are accepted by other phones only after this
        val pending = vm.state.keys.flatMap { (uid, l) -> l.filter { !it.ok }.map { uid to it } }
        if (pending.isNotEmpty() && Rules.allowed(vm.state, me.uid, "KEY_APPROVED", null)) {
            SectionTitle("📱 " + t("phones_waiting", "n" to pending.size.toString()))
            InfoBox(t("phones_waiting_info"), icon = "🔐")
            pending.forEach { (uid, k) ->
                AiecCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(vm.state.users[uid]?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                            Text(k.devName.ifBlank { "—" } + " · " + t("phone_code") + " " + k.kid.take(8).uppercase() + " · " + fmt(k.t), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.perform("KEY_APPROVED", null, jsonOf("uid" to uid, "kid" to k.kid)) }) { Text("✅ " + t("approve_phone")) }
                    }
                }
            }
        }
        Role.entries.forEach { role ->
            val list = vm.state.users.values.filter { it.role == role }
            if (list.isEmpty()) return@forEach
            SectionTitle(role.icon + " " + t("r_" + role.key) + " (${list.count { it.active }})")
            list.forEach { u ->
                AiecCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.role.icon)
                        Column(Modifier.weight(1f)) {
                            Text(u.name, style = MaterialTheme.typography.titleMedium)
                            Text((if (u.active) "🟢 " + t("active") else "⛔ " + t("stopped")) + (if (u.phone.isNotBlank()) " · " + u.phone else ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (u.active && u.uid != me.uid && Rules.allowed(vm.state, me.uid, "USER_UPDATED", null) && (u.role != Role.OWNER || me.role == Role.OWNER))
                            TextButton(onClick = { confirm = u.uid }) { Text(t("stop_access"), color = Aiec.Late) }
                    }
                }
            }
        }
    }
    confirm?.let { uid ->
        val name = vm.state.users[uid]?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(t("stop_access")) },
            text = { Text(t("stop_access_confirm", "n" to name)) },
            confirmButton = { TextButton(onClick = { vm.perform("USER_UPDATED", null, jsonOf("uid" to uid, "active" to false)); confirm = null }) { Text(t("yes"), color = Aiec.Late) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("no")) } }
        )
    }
}

@Composable
fun MeScreen(vm: AppViewModel, speech: Speech) {
    val me = vm.me ?: return
    speech.text = I18n.t(LocalLang.current, "me_say")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        AiecCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(me.role.icon)
                Column { Text(me.name, style = MaterialTheme.typography.titleLarge); Text(t("r_" + me.role.key) + " · " + (vm.state.org?.name ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        SectionTitle("🌐 " + t("language"))
        ChoiceChips(I18n.LANGS.map { it to I18n.NAMES.getValue(it) }, vm.lang) { vm.setLanguage(it) }
        SectionTitle("🎨 " + t("colours"))
        ChoiceChips(listOf("system" to t("theme_system"), "light" to t("theme_light"), "dark" to t("theme_dark"), "contrast" to t("theme_contrast")), vm.theme) { vm.setThemeMode(it) }
        SectionTitle("⚙️ " + t("more"))
        if (me.role.office) {
            SecondaryButton(t("escalations"), "⏫") { vm.go(Screen.Escalations) }; Spacer(Modifier.height(8.dp))
        }
        if (me.role == Role.OWNER) { SecondaryButton(t("business_rules"), "📐") { vm.go(Screen.BusinessRules) }; Spacer(Modifier.height(8.dp)) }
        if (me.role != Role.CUSTOMER) { SecondaryButton(t("share_data"), "🔄") { vm.go(Screen.Sync) }; Spacer(Modifier.height(8.dp)) }
        SecondaryButton(t("logout"), "🚪") { vm.logout() }
        SectionTitle("ℹ️ " + t("about"))
        Text("AIEC Elevator OS ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodyMedium)
        Text(t("privacy_short"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun BusinessRulesScreen(vm: AppViewModel, speech: Speech) {
    speech.text = I18n.t(LocalLang.current, "business_rules")
    val cfg = vm.state.config
    var editing by remember { mutableStateOf<String?>(null) }
    var value by remember { mutableStateOf("") }
    val keys = Config.LIMITS.keys.toList() + Config.TEXT_KEYS.toList()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        InfoBox(t("rules_info"))
        keys.forEach { k ->
            val cur = cfg.number(k)?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: when (k) { "officeNumber" -> cfg.officeNumber; "sosNumber" -> cfg.sosNumber; "cityCode" -> cfg.cityCode; else -> "" }
            AiecCard(onClick = { editing = k; value = cur }) {
                Column {
                    Text(t("cfg_$k"), style = MaterialTheme.typography.titleMedium)
                    Text(cur.ifBlank { "—" } + (Config.LIMITS[k]?.let { "   (" + it.start.toLong() + "–" + it.endInclusive.toLong() + ")" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    editing?.let { k ->
        var err by remember(k) { mutableStateOf<com.aiec.elevatoros.domain.Err?>(null) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(t("cfg_$k")) },
            text = {
                Column {
                    OutlinedTextField(value, { value = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = if (k in Config.TEXT_KEYS && k == "cityCode") KeyboardType.Text else if (k in Config.TEXT_KEYS) KeyboardType.Phone else KeyboardType.Decimal))
                    err?.let { Text(errText(it), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { val e = vm.perform("CONFIG_SET", null, jsonOf("key" to k, "value" to value.trim())); if (e == null) editing = null else err = e }) { Text(t("save")) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(t("close")) } }
        )
    }
}

@Composable
fun EscalationsScreen(vm: AppViewModel, speech: Speech) {
    val list = Tasks.escalations(vm.state, Time.now())
    speech.text = I18n.t(LocalLang.current, "escalations_say", mapOf("n" to list.size.toString()))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        InfoBox(t("escalation_info", "a" to vm.state.config.remindDays.toString(), "b" to vm.state.config.escalateDays.toString(), "c" to vm.state.config.escOwnerDays.toString()))
        if (list.isEmpty()) InfoBox(t("no_escalations"), icon = "✅")
        list.forEach { TaskCard(vm, it) }
    }
}

/** File sync between phones (the original's offline file flow): share all records, load another phone's file. */
@Composable
fun SyncScreen(vm: AppViewModel, speech: Speech) {
    val context = LocalContext.current
    speech.text = I18n.t(LocalLang.current, "share_data_say")
    var result by remember { mutableStateOf<String?>(null) }
    var counts by remember { mutableStateOf(vm.repo.syncCounts()) }
    val lang = LocalLang.current
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching { context.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() } }.getOrNull()
            val r = text?.let { vm.repo.importJson(it, vm.me?.role) }
            result = when {
                r == null || r.wrongCompany -> I18n.t(lang, "import_wrong")
                else -> I18n.t(lang, "import_result", mapOf("a" to r.added.toString(), "d" to r.duplicates.toString(), "r" to r.rejected.toString()))
            }
            counts = vm.repo.syncCounts()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoBox(t("sync_info"))
        vm.me?.let { m ->
            val mine = vm.state.keys[m.uid]?.firstOrNull { it.kid == vm.repo.key.kid }
            if (mine != null && !mine.ok) InfoBox(t("phone_not_approved", "c" to mine.kid.take(8).uppercase()), MaterialTheme.colorScheme.errorContainer, "🔐")
            else if (mine != null) InfoBox(t("phone_approved", "c" to mine.kid.take(8).uppercase()), icon = "🔐")
        }
        AiecCard {
            Column {
                KeyValue(t("sync_waiting"), ((counts[EventDb.PENDING] ?: 0) + (counts[EventDb.LOCAL] ?: 0)).toString())
                KeyValue(t("sync_shared"), (counts[EventDb.SYNCED] ?: 0).toString())
                KeyValue(t("sync_loaded"), (counts[EventDb.IMPORTED] ?: 0).toString())
                KeyValue(t("sync_refused"), vm.repo.rejectedCount().toString())
                KeyValue(t("sync_last_share"), fmt(vm.prefs.lastExport).ifBlank { "—" })
                KeyValue(t("company_server"), t("server_not_in_version"))
            }
        }
        val me = vm.me
        val office = me?.role?.office == true
        var target by remember { mutableStateOf<String?>(null) }
        if (office) {
            Text(t("share_for_whom"), style = MaterialTheme.typography.labelLarge)
            ChoiceChips(vm.state.activeUsers().filter { it.role != Role.CUSTOMER && !it.role.outsider }.map { it.uid to (it.role.icon + " " + it.name) }, target) { target = it }
        } else InfoBox(t("share_own_only"), icon = "📤")
        BigButton(t("share_records"), "📤", enabled = me != null && (!office || target != null)) {
            val dir = File(context.cacheDir, "export").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val f = File(dir, "AIEC-records-" + Time.day(Time.now()) + ".json")
            f.writeText(if (office) vm.repo.exportJson(target!!) else vm.repo.exportJson("", onlyBy = me!!.uid))
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/json"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, I18n.t(lang, "share_records")))
            counts = vm.repo.syncCounts()
        }
        SecondaryButton(t("load_records"), "📥") { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        result?.let { InfoBox(it, icon = "📥") }
        Text(t("file_privacy"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
    }
}
