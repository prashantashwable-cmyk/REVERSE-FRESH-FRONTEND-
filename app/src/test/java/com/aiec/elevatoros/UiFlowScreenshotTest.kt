package com.aiec.elevatoros

import android.app.Application
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiec.elevatoros.domain.Stage
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.ui.AppRoot
import com.aiec.elevatoros.ui.AppViewModel
import com.aiec.elevatoros.ui.Screen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real Compose UI (AppRoot + AppViewModel + SQLite + rules engine) on the JVM and saves screenshots to
 * android-release/screenshots/. Every navigation step is asserted, so a screenshot is never of a broken state.
 */
/** Coroutines resumed on Dispatchers.Main wait in Robolectric's paused main looper until it is idled. */
fun idleMain() { Thread.sleep(20); org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle() }

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-port-xhdpi")
class UiFlowScreenshotTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var vm: AppViewModel
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        app.deleteDatabase("aiec_practice.db"); app.deleteDatabase("aiec_live.db")
        app.getSharedPreferences("aiec_prefs", 0).edit().clear().commit()
        I18n.load(app)
        vm = AppViewModel(app)
        rule.setContent { AppRoot(vm) { _, _ -> } }
    }

    private fun shot(name: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("../android-release/screenshots/$name.png") }
    private fun text(s: String): SemanticsNodeInteraction = rule.onAllNodesWithText(s, substring = true)[0]
    private fun tr(k: String) = I18n.t(vm.lang, k)
    private fun waitFor(timeoutMs: Long = 20_000, cond: () -> Boolean) = rule.waitUntil(timeoutMs) { idleMain(); cond() }

    private fun enterPractice() {
        vm.setLanguage("en")
        rule.onNodeWithText(tr("practice_mode")).performClick()
        waitFor { vm.screen == Screen.Login }
    }

    private fun loginAs(name: String) {
        rule.onNodeWithText(name).performScrollTo().performClick()
        waitFor { vm.screen is Screen.PinEntry }
        repeat(4) { rule.onAllNodesWithText("1")[0].performClick() }
        waitFor { vm.screen == Screen.Home }
    }

    @Test fun frontDoorInAllThreeLanguages() {
        for (l in listOf("mr", "hi", "en")) {
            vm.setLanguage(l)
            rule.onNodeWithText(I18n.t(l, "staff_login"), substring = true).assertIsDisplayed()
            shot("01_welcome_$l")
        }
    }

    @Test fun fieldWorkerHomeAndNewSite() {
        enterPractice()
        shot("02_login_list_en")
        loginAs("Ramesh Jadhav")
        assertEquals("Ramesh Jadhav", vm.me?.name)
        shot("03_home_field_en")
        vm.setLanguage("mr"); shot("03_home_field_mr")
        vm.setLanguage("en")
        rule.onNodeWithText(tr("add_site")).performScrollTo().performClick()
        waitFor { vm.screen is Screen.Action }
        shot("04_add_site_en")
        vm.setLanguage("hi"); shot("04_add_site_hi")
    }

    @Test fun salesWorkflowAndRefusals() {
        enterPractice()
        loginAs("Priya Kulkarni")
        shot("05_home_sales_en")
        // the first task for Sales is the new lead from the field worker
        rule.onNodeWithText(tr("start_next_task")).performClick()
        waitFor { vm.screen is Screen.Action }
        val a = vm.screen as Screen.Action
        assertEquals("LEAD_ACCEPTED", a.type)
        shot("06_check_lead_en")
        rule.onNodeWithText(tr("save")).performScrollTo().performClick()
        waitFor { vm.screen is Screen.Saved }
        assertEquals(Stage.ACCEPTED, vm.state.projects.getValue(a.pid!!).stage)
        shot("07_saved_en")
        vm.setLanguage("mr"); shot("07_saved_mr")
        vm.setLanguage("en")
        // a form refuses to save without its required field
        vm.go(Screen.Action("CONTACT_LOGGED", a.pid))
        rule.onNodeWithText(tr("save")).performScrollTo().performClick()
        text(tr("err_fill_field").substringBefore("{")).assertIsDisplayed()
        shot("08_contact_refused_en")
    }

    @Test fun adminApprovesQuoteButNotAsAuthorAndSeesEscalations() {
        enterPractice()
        loginAs("Sunita Patil")
        shot("09_home_admin_en")
        val pid = vm.state.projects.values.first { it.stage == Stage.QUOTE_APPROVAL }.id
        vm.go(Screen.ProjectDetail(pid))
        shot("10_project_detail_en")
        vm.setLanguage("hi"); shot("10_project_detail_hi"); vm.setLanguage("en")
        vm.go(Screen.Action("QUOTE_DECISION", pid))
        shot("11_approve_quote_en")
        rule.onNodeWithText(tr("dec_approve")).performScrollTo().performClick()
        rule.onNodeWithText(tr("save")).performScrollTo().performClick()
        waitFor { vm.screen is Screen.Saved }
        assertEquals(Stage.QUOTE_READY, vm.state.projects.getValue(pid).stage)
        vm.resetTo(Screen.Escalations)
        shot("12_escalations_en")
        vm.resetTo(Screen.People)
        shot("13_people_en")
    }

    @Test fun financeConfirmsTokenAndTechnicianInstalls() {
        enterPractice()
        loginAs("Meena Joshi")
        rule.onNodeWithText(tr("start_next_task")).performClick()
        waitFor { vm.screen is Screen.Action }
        assertEquals("PAYMENT_STATUS", (vm.screen as Screen.Action).type)
        shot("14_verify_payment_en")
        vm.logout()
        loginAs("Raju Pawar")
        shot("15_home_technician_en")
        vm.setLanguage("mr"); shot("15_home_technician_mr"); vm.setLanguage("en")
        rule.onNodeWithText(tr("start_next_task")).performClick()
        waitFor { vm.screen is Screen.Action }
        val a = vm.screen as Screen.Action
        assertTrue(a.type == "SOP_STEP_DONE" || a.type == "TICKET_UPDATED")
        shot("16_install_step_en")
        // technicians never see customer names or money (data protection)
        val p = vm.state.projects.getValue("DEMO-2")
        vm.go(Screen.ProjectDetail(p.id))
        rule.onAllNodesWithText(p.contactName).fetchSemanticsNodes().let { assertTrue("customer name visible to technician", it.isEmpty()) }
        rule.onAllNodes(hasText("₹", substring = true)).fetchSemanticsNodes().let { assertTrue("money visible to technician", it.isEmpty()) }
        shot("17_project_technician_en")
    }

    @Test fun meScreenThemesAndRules() {
        enterPractice()
        loginAs("Anil Deshmukh")
        shot("18_home_hq_en")
        vm.resetTo(Screen.Me); shot("19_me_en")
        vm.setThemeMode("dark"); shot("20_me_dark_en")
        vm.setThemeMode("contrast"); vm.resetTo(Screen.Home); shot("21_home_contrast_en")
        vm.setThemeMode("light"); vm.resetTo(Screen.BusinessRules); shot("22_business_rules_en")
        vm.resetTo(Screen.Sync); shot("23_share_data_en")
        assertNotNull(vm.state.org)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w320dp-h640dp-port-mdpi")
class SmallScreenScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun lowEndPhoneWelcomeAndPin() {
        val app: Application = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("aiec_practice.db"); app.getSharedPreferences("aiec_prefs", 0).edit().clear().commit()
        I18n.load(app)
        val vm = AppViewModel(app)
        rule.setContent { AppRoot(vm) { _, _ -> } }
        vm.setLanguage("mr")
        rule.waitForIdle(); rule.onRoot().captureRoboImage("../android-release/screenshots/30_small_welcome_mr.png")
        rule.onNodeWithText(I18n.t("mr", "practice_mode")).performScrollTo().performClick()
        runCatching { rule.waitUntil(20_000) { idleMain(); vm.screen == Screen.Login } }.onFailure { throw AssertionError("screen=${vm.screen} busy=${vm.busy} failure=${vm.startupFailure}", it) }
        rule.onNodeWithText("Raju Pawar").performScrollTo().performClick()
        rule.waitUntil(5_000) { idleMain(); vm.screen is Screen.PinEntry }
        rule.waitForIdle(); rule.onRoot().captureRoboImage("../android-release/screenshots/31_small_pin_mr.png")
    }
}
