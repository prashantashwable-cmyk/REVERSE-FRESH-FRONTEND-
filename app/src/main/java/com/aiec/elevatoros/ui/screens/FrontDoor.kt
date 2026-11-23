package com.aiec.elevatoros.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aiec.elevatoros.domain.Err
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
import com.aiec.elevatoros.ui.components.PinPad
import com.aiec.elevatoros.ui.components.SecondaryButton
import com.aiec.elevatoros.ui.theme.Aiec

@Composable
fun Logo() {
    Box(Modifier.size(96.dp).clip(RoundedCornerShape(24.dp)).background(Aiec.Navy), contentAlignment = Alignment.Center) {
        Box(Modifier.size(56.dp, 64.dp).clip(RoundedCornerShape(8.dp)).background(Aiec.Gold), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(Modifier.size(20.dp, 44.dp).background(Aiec.Navy)); Box(Modifier.size(20.dp, 44.dp).background(Aiec.Navy))
            }
        }
    }
}

@Composable
fun WelcomeScreen(vm: AppViewModel, speech: Speech) {
    val lang = LocalLang.current
    speech.text = I18n.t(lang, "welcome_say")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp)); Logo(); Spacer(Modifier.height(12.dp))
        Text("AIEC Elevator OS", style = MaterialTheme.typography.headlineMedium)
        Text(t("tagline"), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text("🌐 " + t("choose_language"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
        ChoiceChips(I18n.LANGS.map { it to I18n.NAMES.getValue(it) }, vm.lang) { vm.setLanguage(it) }
        Spacer(Modifier.height(12.dp))
        BigButton(t("staff_login"), "👷") { vm.openLive() }
        Spacer(Modifier.height(10.dp))
        SecondaryButton(t("practice_mode"), "🎓") { vm.startPractice() }
        Text(t("practice_hint"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        SecondaryButton(t("new_company"), "🏢") { vm.go(Screen.Setup) }
        Spacer(Modifier.height(16.dp))
        InfoBox(t("offline_note"), icon = "💾")
    }
}

@Composable
fun SetupScreen(vm: AppViewModel, speech: Speech) {
    speech.text = I18n.t(LocalLang.current, "setup_say")
    var company by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<Err?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InfoBox(t("setup_info"), icon = "🏛️")
        OutlinedTextField(company, { company = it }, label = { Text(t("f_company")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text(t("f_hq_name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' } }, label = { Text(t("f_phone")) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pin, { if (it.length <= 6) pin = it.filter(Char::isDigit) }, label = { Text(t("f_new_pin")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(pin2, { if (it.length <= 6) pin2 = it.filter(Char::isDigit) }, label = { Text(t("f_pin_again")) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        Text(t("pin_rules"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        err?.let { InfoBox(errText(it), MaterialTheme.colorScheme.errorContainer, "⛔") }
        BigButton(t("create_company"), "✅", enabled = !vm.busy) {
            err = if (pin != pin2) Err("err_pin_mismatch") else null
            if (err == null) vm.createCompany(company, name, phone, pin) { err = it }
        }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, speech: Speech) {
    speech.text = I18n.t(LocalLang.current, "who_are_you")
    var q by remember { mutableStateOf("") }
    val users = vm.state.users.values.filter { it.active }.sortedBy { it.role.ordinal }
    val last = vm.prefs.lastUser?.let { vm.state.users[it] }?.takeIf { it.active }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("👤 " + t("who_are_you"), style = MaterialTheme.typography.titleLarge)
        if (last != null) {
            Spacer(Modifier.height(8.dp))
            BigButton(t("continue_as", "n" to last.name), last.role.icon) { vm.go(Screen.PinEntry(last.uid)) }
        }
        if (users.size > 6) OutlinedTextField(q, { q = it }, label = { Text(t("search")) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        users.filter { q.isBlank() || it.name.contains(q, true) }.forEach { u ->
            AiecCard(onClick = { vm.go(Screen.PinEntry(u.uid)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(u.role.icon)
                    Column { Text(u.name, style = MaterialTheme.typography.titleMedium); Text(t("r_" + u.role.key), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        if (vm.practice) InfoBox(t("practice_pin_hint"), icon = "🎓")
        TextButton(onClick = { vm.leaveCompany() }, modifier = Modifier.padding(top = 8.dp)) { Text("⬅ " + t("other_company")) }
    }
}

@Composable
fun PinScreen(vm: AppViewModel, uid: String, speech: Speech) {
    val u = vm.state.users[uid] ?: return
    speech.text = I18n.t(LocalLang.current, "enter_pin")
    var pin by remember(uid) { mutableStateOf("") }
    var err by remember(uid) { mutableStateOf<String?>(null) }
    LaunchedEffect(pin) {
        if (pin.length == u.pinLen) vm.login(uid, pin) { e -> if (e != null) { err = e; pin = "" } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(u.role.icon, fontSize = 44.sp)
        Text(u.name, style = MaterialTheme.typography.headlineSmall)
        Text(t("r_" + u.role.key), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(t("enter_pin"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
        err?.let { Text(t(it), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp)) }
        PinPad(u.pinLen, pin, onDigit = { if (pin.length < u.pinLen) { err = null; pin += it } }, onDelete = { pin = pin.dropLast(1) }, enabled = !vm.busy)
    }
}
