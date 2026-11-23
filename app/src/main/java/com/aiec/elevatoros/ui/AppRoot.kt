package com.aiec.elevatoros.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.aiec.elevatoros.domain.Role
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.i18n.LocalLang
import com.aiec.elevatoros.i18n.t
import com.aiec.elevatoros.ui.components.AiecTopBar
import com.aiec.elevatoros.ui.screens.ActionScreen
import com.aiec.elevatoros.ui.screens.BusinessRulesScreen
import com.aiec.elevatoros.ui.screens.EscalationsScreen
import com.aiec.elevatoros.ui.screens.HomeScreen
import com.aiec.elevatoros.ui.screens.LoginScreen
import com.aiec.elevatoros.ui.screens.MeScreen
import com.aiec.elevatoros.ui.screens.PeopleScreen
import com.aiec.elevatoros.ui.screens.PinScreen
import com.aiec.elevatoros.ui.screens.ProjectDetailScreen
import com.aiec.elevatoros.ui.screens.ProjectsScreen
import com.aiec.elevatoros.ui.screens.SavedScreen
import com.aiec.elevatoros.ui.screens.SetupScreen
import com.aiec.elevatoros.ui.screens.SyncScreen
import com.aiec.elevatoros.ui.screens.WelcomeScreen
import com.aiec.elevatoros.ui.theme.AiecTheme

/** What the 🔊 button reads for the current screen; screens set it. */
class Speech { var text by mutableStateOf("") }

@Composable
fun AppRoot(vm: AppViewModel, onSpeak: (String, String) -> Unit) {
    val version by vm.repo.version.collectAsState()
    AiecTheme(vm.theme) {
        CompositionLocalProvider(LocalLang provides vm.lang) {
            val speech = remember { Speech() }
            var langDialog by remember { mutableStateOf(false) }
            BackHandler(enabled = vm.canGoBack) { vm.back() }
            val screen = vm.screen
            val loggedIn = vm.me != null && screen !is Screen.Welcome && screen !is Screen.Login && screen !is Screen.PinEntry && screen !is Screen.Setup
            val title = titleFor(screen, vm)
            Scaffold(
                topBar = {
                    AiecTopBar(
                        title = title,
                        onBack = if (vm.canGoBack) ({ vm.back() }) else null,
                        onListen = { onSpeak(listOf(title, speech.text).filter { it.isNotBlank() }.joinToString(". "), vm.lang) },
                        onLanguage = { langDialog = true },
                        practice = vm.practice && screen !is Screen.Welcome,
                    )
                },
                bottomBar = { if (loggedIn) BottomTabs(vm) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { pad ->
                Box(Modifier.fillMaxSize().padding(pad)) {
                    @Suppress("UNUSED_EXPRESSION") version // recompose on every saved change
                    when (screen) {
                        Screen.Welcome -> WelcomeScreen(vm, speech)
                        Screen.Setup -> SetupScreen(vm, speech)
                        Screen.Login -> LoginScreen(vm, speech)
                        is Screen.PinEntry -> PinScreen(vm, screen.uid, speech)
                        Screen.Home -> HomeScreen(vm, speech)
                        Screen.Projects -> ProjectsScreen(vm, speech)
                        is Screen.ProjectDetail -> ProjectDetailScreen(vm, screen.pid, speech)
                        is Screen.Action -> ActionScreen(vm, screen, speech)
                        is Screen.Saved -> SavedScreen(vm, screen, speech)
                        Screen.People -> PeopleScreen(vm, speech)
                        Screen.Me -> MeScreen(vm, speech)
                        Screen.BusinessRules -> BusinessRulesScreen(vm, speech)
                        Screen.Escalations -> EscalationsScreen(vm, speech)
                        Screen.Sync -> SyncScreen(vm, speech)
                    }
                    if (vm.busy) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            if (langDialog) AlertDialog(
                onDismissRequest = { langDialog = false },
                title = { Text(t("language")) },
                text = {
                    Column {
                        I18n.LANGS.forEach { l -> TextButton(onClick = { vm.setLanguage(l); langDialog = false }) { Text((if (l == vm.lang) "✓ " else "") + I18n.NAMES.getValue(l), fontSize = 20.sp) } }
                    }
                },
                confirmButton = { TextButton(onClick = { langDialog = false }) { Text(t("close")) } }
            )
        }
    }
}

@Composable
private fun titleFor(s: Screen, vm: AppViewModel): String = when (s) {
    Screen.Welcome -> t("company")
    Screen.Setup -> t("new_company")
    Screen.Login -> vm.state.org?.name ?: t("staff_login")
    is Screen.PinEntry -> t("enter_pin")
    Screen.Home -> t("home")
    Screen.Projects -> t("projects")
    is Screen.ProjectDetail -> vm.state.projects[s.pid]?.no ?: t("projects")
    is Screen.Action -> Actions.SPECS[s.type]?.let { t(it.title) } ?: s.type
    is Screen.Saved -> t("done")
    Screen.People -> t("people")
    Screen.Me -> t("me")
    Screen.BusinessRules -> t("business_rules")
    Screen.Escalations -> t("escalations")
    Screen.Sync -> t("share_data")
}

@Composable
private fun BottomTabs(vm: AppViewModel) {
    val role = vm.me?.role
    val tabs = buildList {
        add(Triple(Screen.Home, "🏠", "home"))
        if (role != Role.CUSTOMER) add(Triple(Screen.Projects, "📋", "projects"))
        if (role == Role.OWNER || role == Role.ADMIN) add(Triple(Screen.People, "👥", "people"))
        add(Triple(Screen.Me, "👤", "me"))
    }
    val current = vm.screen
    NavigationBar {
        tabs.forEach { (s, icon, label) ->
            NavigationBarItem(
                selected = current == s || (s == Screen.Projects && current is Screen.ProjectDetail),
                onClick = { vm.tab(s) },
                icon = { Text(icon, fontSize = 22.sp) },
                label = { Text(t(label)) }
            )
        }
    }
}
