package com.aiec.elevatoros

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aiec.elevatoros.device.Speaker
import com.aiec.elevatoros.i18n.I18n
import com.aiec.elevatoros.ui.AppRoot
import com.aiec.elevatoros.ui.AppViewModel

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var speaker: Speaker? = null
    private var backgroundAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        I18n.load(this)
        speaker = Speaker(this)
        // lock after 5 minutes in the background: the next person must type their own PIN
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) { backgroundAt = System.currentTimeMillis() }
            override fun onStart(owner: LifecycleOwner) { if (backgroundAt > 0 && System.currentTimeMillis() - backgroundAt > 5 * 60_000L) vm.lock() }
        })
        setContent {
            // outside companies (technicians, suppliers): no screenshots, blurred in the app switcher (original watermark/blur rule)
            LaunchedEffect(Unit) {
                snapshotFlow { vm.me?.role?.outsider == true }.collect { secure ->
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            AppRoot(vm) { text, lang -> speaker?.say(text, lang) }
        }
    }

    override fun onDestroy() { speaker?.shutdown(); super.onDestroy() }
}
