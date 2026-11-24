package com.aiec.elevatoros

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiec.elevatoros.domain.Role
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.ui.AppRoot
import com.aiec.elevatoros.ui.AppViewModel
import com.aiec.elevatoros.ui.Screen
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real company: setup with a PIN typed twice, add a person with a PIN, log out, log in as that person, restart. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-port-xhdpi")
class CompanySetupFlowTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var vm: AppViewModel
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        app.deleteDatabase("aiec_practice.db"); app.deleteDatabase("aiec_live.db")
        app.getSharedPreferences("aiec_prefs", 0).edit().clear().commit()
        I18n.load(app)
        vm = AppViewModel(app)
        rule.setContent { AppRoot(vm) { _, _ -> } }
        vm.setLanguage("en")
    }

    private fun tr(k: String) = I18n.t("en", k)
    private fun waitFor(ms: Long = 20_000, c: () -> Boolean) = rule.waitUntil(ms) { idleMain(); c() }
    private fun field(i: Int) = rule.onAllNodes(hasSetTextAction())[i]
    private fun shot(n: String) { rule.waitForIdle(); rule.onRoot().captureRoboImage("../android-release/screenshots/$n.png") }

    @Test fun setupCompanyAddPersonAndLogin() {
        rule.onNodeWithText(tr("new_company")).performClick()
        waitFor { vm.screen == Screen.Setup }
        field(0).performTextInput("Shree Lifts")
        field(1).performTextInput("Prashant")
        field(2).performTextInput("9822012345")
        field(3).performTextInput("4831")
        field(4).performTextInput("4831")
        shot("24_setup_company_en")
        rule.onNodeWithText(tr("create_company")).performScrollTo().performClick()
        waitFor { vm.screen == Screen.Home }
        assertEquals(Role.OWNER, vm.me?.role)
        assertEquals(true, vm.state.keys.getValue(vm.me!!.uid).single().ok)

        // add a person with a PIN through the real form
        vm.resetTo(Screen.People)
        rule.onNodeWithText(tr("add_person")).performClick()
        waitFor { vm.screen is Screen.Action }
        field(0).performTextInput("Priya")
        field(1).performTextInput("9822000111")
        rule.onAllNodesWithText(tr("r_sales"), substring = true)[0].performScrollTo().performClick()
        field(2).performTextInput("5927")
        shot("25_add_person_en")
        rule.onNodeWithText(tr("save")).performScrollTo().performClick()
        waitFor { vm.screen is Screen.Saved }
        val priya = vm.state.users.values.single { it.name == "Priya" }
        assertEquals(Role.SALES, priya.role)
        shot("26_person_saved_en")

        // log out, log in as the new person with their PIN
        vm.logout()
        waitFor { vm.screen == Screen.Login }
        rule.onNodeWithText("Priya").performScrollTo().performClick()
        waitFor { vm.screen is Screen.PinEntry }
        "5927".forEach { d -> rule.onAllNodesWithText(d.toString())[0].performClick() }
        waitFor { vm.screen == Screen.Home }
        assertEquals("Priya", vm.me?.name)
        // her first login registered this phone for her; the office phone is already trusted, so it is approved at once
        assertEquals(true, vm.state.keys.getValue(priya.uid).single().ok)

        // restart: a new ViewModel replays and verifies every signed record from the database
        val vm2 = AppViewModel(app)
        rule.waitUntil(20_000) { idleMain(); vm2.screen == Screen.Login }
        assertNotNull(vm2.state.users[priya.uid])
        assertEquals(2, vm2.state.users.size)
        assertEquals(0, vm2.repo.rejectedCount())
    }
}
