package com.aiec.elevatoros

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.ui.AppViewModel
import com.aiec.elevatoros.ui.Screen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Launches the real MainActivity (splash, edge-to-edge, lifecycle observer, FLAG_SECURE) and logs in with a PIN. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-port-xhdpi")
class MainActivityLaunchTest {
    init {
        val app: Application = ApplicationProvider.getApplicationContext()
        app.deleteDatabase("aiec_practice.db"); app.deleteDatabase("aiec_live.db")
        app.getSharedPreferences("aiec_prefs", 0).edit().clear().putString("lang", "en").commit()
    }
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun vm(): AppViewModel = androidx.lifecycle.ViewModelProvider(rule.activity)[AppViewModel::class.java]
    private fun tr(k: String) = I18n.t("en", k)

    @Test fun practiceLoginThroughRealActivity() {
        rule.onNodeWithText(tr("practice_mode")).performClick()
        rule.waitUntil(20_000) { idleMain(); vm().screen == Screen.Login }
        for (name in listOf("Raju Pawar", "Anil Deshmukh")) {   // an outsider (FLAG_SECURE) and the Head Quarter
            rule.onNodeWithText(name).performScrollTo().performClick()
            rule.waitUntil(5_000) { idleMain(); vm().screen is Screen.PinEntry }
            repeat(4) { rule.onAllNodesWithText("1")[0].performClick() }
            rule.waitUntil(20_000) { idleMain(); vm().screen == Screen.Home }
            assertEquals(name, vm().me?.name)
            rule.waitForIdle(); idleMain()
            vm().logout()
            rule.waitUntil(5_000) { idleMain(); vm().screen == Screen.Login }
        }
    }
}
