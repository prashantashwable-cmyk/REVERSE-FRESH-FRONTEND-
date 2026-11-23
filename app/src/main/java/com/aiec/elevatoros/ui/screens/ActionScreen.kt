package com.aiec.elevatoros.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.aiec.elevatoros.data.EvidenceStore
import com.aiec.elevatoros.device.GpsResult
import com.aiec.elevatoros.domain.Err
import com.aiec.elevatoros.domain.EvidenceRef
import com.aiec.elevatoros.domain.GeoPoint
import com.aiec.elevatoros.domain.Time
import com.aiec.elevatoros.domain.str
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.i18n.LocalLang
import com.aiec.elevatoros.i18n.errText
import com.aiec.elevatoros.i18n.t
import com.aiec.elevatoros.ui.ActionSpec
import com.aiec.elevatoros.ui.Actions
import com.aiec.elevatoros.ui.AppViewModel
import com.aiec.elevatoros.ui.Ctx
import com.aiec.elevatoros.ui.F
import com.aiec.elevatoros.ui.Screen
import com.aiec.elevatoros.ui.Speech
import com.aiec.elevatoros.ui.components.AiecCard
import com.aiec.elevatoros.ui.components.BigButton
import com.aiec.elevatoros.ui.components.ChoiceChips
import com.aiec.elevatoros.ui.components.InfoBox
import com.aiec.elevatoros.ui.components.SecondaryButton
import com.aiec.elevatoros.ui.components.SignaturePad
import com.aiec.elevatoros.ui.theme.Aiec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

@Composable
fun ActionScreen(vm: AppViewModel, screen: Screen.Action, speech: Speech) {
    val me = vm.me ?: return
    val spec: ActionSpec = Actions.SPECS[screen.type] ?: return InfoBox("?", icon = "⛔")
    val lang = LocalLang.current
    val ctx = Ctx(vm.state, screen.pid?.let { vm.state.projects[it] }, screen.extra, me) { k -> I18n.t(lang, k) }
    val values = remember(screen) { mutableStateMapOf<String, Any?>() }
    val fields = spec.fields(ctx)
    remember(screen) { fields.forEach { f -> when (f) { is F.Text -> if (f.initial.isNotEmpty()) values[f.key] = f.initial; is F.Choice -> f.initial?.let { values[f.key] = it }; else -> {} } }; true }
    var err by remember(screen) { mutableStateOf<Err?>(null) }
    var saving by remember(screen) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    speech.text = I18n.t(lang, spec.title) + ". " + fields.filterIsInstance<F.Info>().mapNotNull { it.text(values) }.firstOrNull().orEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ctx.p?.let { p -> Text("${spec.icon} ${p.no}" + if (!me.role.outsider && p.contactName.isNotBlank()) " · ${p.contactName}" else "", style = MaterialTheme.typography.titleMedium) }
        fields.forEach { f -> Field(vm, f, values, ctx) }
        err?.let { InfoBox(errText(it), MaterialTheme.colorScheme.errorContainer, "⛔") }
        BigButton(t("save"), "💾", enabled = !vm.busy && !saving) {
            if (saving) return@BigButton
            val missing = fields.firstOrNull { f ->
                when (f) {
                    is F.Text -> f.required && (values[f.key] as? String).isNullOrBlank()
                    is F.Choice -> f.required && f.options.isNotEmpty() && values[f.key] == null
                    is F.Photos -> f.min > 0 && (values[f.key] as? List<*>).orEmpty().size < f.min
                    is F.Sign -> values[f.key] == null
                    is F.Person -> values[f.key] == null
                    is F.Checks -> (values[f.key] as? Map<*, *>)?.size != f.items.size
                    else -> false
                }
            }
            if (missing != null) {
                err = when (missing) { is F.Photos -> Err("err_photos", mapOf("n" to missing.min.toString())); is F.Sign -> Err("err_sign"); is F.Checks -> Err("err_checks"); else -> Err("err_fill_field", mapOf("f" to I18n.t(lang, missing.label))) }
                return@BigButton
            }
            saving = true
            scope.launch {
                val data: JsonObject = withContext(Dispatchers.Default) { spec.build(values.toMap(), ctx) }
                val newPid = data.str("_pid").ifEmpty { null }
                val clean = JsonObject(data.filterKeys { it != "_pid" })
                val pid = if (spec.type == "LEAD_CREATED") newPid else screen.pid
                val e = vm.perform(spec.type, pid, clean)
                saving = false
                if (e != null) err = e else vm.replace(Screen.Saved(spec.savedKey, pid))
            }
        }
        Text("💾 " + t("saved_on_phone"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Field(vm: AppViewModel, f: F, values: MutableMap<String, Any?>, ctx: Ctx) {
    when (f) {
        is F.Info -> f.text(values)?.let { InfoBox(it, if (f.warn) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer, if (f.warn) "⚠️" else "ℹ️") }
        is F.Text -> {
            val v = (values[f.key] as? String).orEmpty()
            Column {
                OutlinedTextField(
                    value = v, onValueChange = { nv -> values[f.key] = if (f.kind == F.Kind.PIN) nv.filter(Char::isDigit).take(6) else nv },
                    label = { Text(t(f.label) + if (!f.required) " (" + t("optional") + ")" else "") },
                    singleLine = !f.multiline, minLines = if (f.multiline) 2 else 1, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (f.kind == F.Kind.PIN) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = when (f.kind) { F.Kind.NUMBER -> KeyboardType.Decimal; F.Kind.PHONE -> KeyboardType.Phone; F.Kind.PIN -> KeyboardType.NumberPassword; else -> KeyboardType.Text })
                )
                if (f.kind == F.Kind.DATE) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1L, 3L, 7L).forEach { d -> TextButton(onClick = { values[f.key] = Time.addDaysIso(Time.now(), d) }) { Text("+$d " + t("days")) } }
                }
            }
        }
        is F.Choice -> if (f.options.isNotEmpty()) Column {
            Text(t(f.label) + if (!f.required) " (" + t("optional") + ")" else "", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            ChoiceChips(f.options, values[f.key] as? String) { values[f.key] = it }
        }
        is F.Person -> Column {
            Text(t(f.label), style = MaterialTheme.typography.labelLarge)
            val people = ctx.s.activeUsers(f.role)
            if (people.isEmpty()) InfoBox(t("nobody_with_role", "r" to t("r_" + f.role.key)), MaterialTheme.colorScheme.errorContainer, "⚠️")
            ChoiceChips(people.map { it.uid to it.name }, values[f.key] as? String) { values[f.key] = it }
        }
        is F.Checks -> Column {
            Text(t(f.label), style = MaterialTheme.typography.labelLarge)
            @Suppress("UNCHECKED_CAST") val m = (values[f.key] as? Map<String, String>).orEmpty()
            f.items.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(t(f.prefix + item), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Row { ChoiceChips(listOf("pass" to "✅", "fail" to "❌"), m[item]) { values[f.key] = m + (item to it) } }
                }
            }
        }
        is F.Lines -> OutlinedTextField((values[f.key] as? String).orEmpty(), { values[f.key] = it }, label = { Text(t(f.label) + " (" + t("one_per_line") + ")") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        is F.Gps -> GpsField(vm, values)
        is F.Photos -> PhotoField(vm, f, values, ctx)
        is F.Sign -> SignaturePad(t(f.label), values[f.key] != null, onDone = { bmp -> values[f.key] = vm.evidence.saveSignature(bmp) }, onClear = { values.remove(f.key) })
    }
}

@Composable
private fun GpsField(vm: AppViewModel, values: MutableMap<String, Any?>) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val fetch: () -> Unit = {
        loading = true
        scope.launch {
            when (val r = vm.gps.current()) {
                is GpsResult.Fix -> { values["gps"] = r.point; status = if (r.approximate) "gps_approx" else null }
                GpsResult.Denied -> status = "gps_denied"
                GpsResult.Disabled -> status = "gps_off"
                GpsResult.Timeout -> status = "gps_timeout"
            }
            loading = false
        }
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res -> if (res.values.any { it }) fetch() else status = "gps_denied" }
    AiecCard {
        Column {
            Text("📍 " + t("f_gps"), style = MaterialTheme.typography.labelLarge)
            val g = values["gps"] as? GeoPoint
            if (g != null) Text("%.5f, %.5f · ±%d m".format(g.lat, g.lon, (g.acc ?: 0.0).toInt()), style = MaterialTheme.typography.bodyLarge, color = if ((g.acc ?: 999.0) <= 50) Aiec.Ok else Aiec.Warn)
            if (g != null && (g.acc ?: 999.0) > 50) Text(t("gps_weak"), style = MaterialTheme.typography.labelMedium, color = Aiec.Warn)
            status?.let { Text(t(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
            TextButton(enabled = !loading, onClick = { if (vm.gps.hasPermission()) fetch() else perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) {
                Text(if (loading) "⏳ " + t("gps_finding") else "🛰️ " + t(if (g == null) "gps_get" else "gps_again"))
            }
        }
    }
}

/** Camera only (no gallery) for evidence; every photo is stamped and hashed before it is attached. */
@Composable
private fun PhotoField(vm: AppViewModel, f: F.Photos, values: MutableMap<String, Any?>, ctx: Ctx) {
    val context = LocalContext.current
    // survives the app being killed while the camera app is open
    var pendingPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var denied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    @Suppress("UNCHECKED_CAST") val list = (values[f.key] as? List<EvidenceRef>).orEmpty()
    val take = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let { File(it) }; pendingPath = null
        if (ok && file != null) scope.launch {
            val gps = (values["gps"] as? GeoPoint) ?: (vm.gps.current(8_000) as? GpsResult.Fix)?.point
            val me = ctx.me
            val lines = EvidenceStore.stampLines(me.name, I18n.t(vm.lang, "r_" + me.role.key), ctx.p?.no ?: "—", ctx.s.org?.name ?: "", gps, vm.prefs.deviceId)
            val ref = withContext(Dispatchers.IO) { vm.evidence.savePhoto(file, f.tag, gps, lines) }
            if (ref != null) values[f.key] = list + ref
        } else file?.delete()
    }
    fun launchCamera() {
        val file = vm.evidence.newCaptureFile(); pendingPath = file.path
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        runCatching { take.launch(uri) }.onFailure { pendingPath = null; denied = true }
    }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) launchCamera() else denied = true }
    AiecCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📷 " + t(f.label), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text("${list.size}" + if (f.min > 0) " / ${f.min}" else "", style = MaterialTheme.typography.labelLarge, color = if (list.size >= f.min) Aiec.Ok else MaterialTheme.colorScheme.error)
            }
            if (list.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                list.takeLast(4).forEach { ref ->
                    val bmp = remember(ref.id) { vm.evidence.file(ref.id)?.let { BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = 8 }) } }
                    if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = t(f.label), contentScale = ContentScale.Crop, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)))
                }
            }
            if (denied) Text(t("camera_denied"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            SecondaryButton(t(if (list.isEmpty()) "take_photo" else "take_another"), "📷") {
                denied = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera() else camPerm.launch(Manifest.permission.CAMERA)
            }
        }
    }
}
