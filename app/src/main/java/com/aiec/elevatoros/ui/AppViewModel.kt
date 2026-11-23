package com.aiec.elevatoros.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aiec.elevatoros.data.EvidenceStore
import com.aiec.elevatoros.data.Mode
import com.aiec.elevatoros.data.Prefs
import com.aiec.elevatoros.data.Repository
import com.aiec.elevatoros.device.Gps
import com.aiec.elevatoros.domain.Err
import com.aiec.elevatoros.domain.Ids
import com.aiec.elevatoros.domain.Pin
import com.aiec.elevatoros.domain.Practice
import com.aiec.elevatoros.domain.Role
import com.aiec.elevatoros.domain.State
import com.aiec.elevatoros.domain.User
import com.aiec.elevatoros.domain.jsonOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

sealed class Screen {
    data object Welcome : Screen()
    data object Setup : Screen()
    data object Login : Screen()
    data class PinEntry(val uid: String) : Screen()
    data object Home : Screen()
    data object Projects : Screen()
    data class ProjectDetail(val pid: String) : Screen()
    data class Action(val type: String, val pid: String?, val extra: String = "") : Screen()
    data class Saved(val messageKey: String, val pid: String?) : Screen()
    data object People : Screen()
    data object Me : Screen()
    data object BusinessRules : Screen()
    data object Escalations : Screen()
    data object Sync : Screen()
}

/**
 * Navigation, session and the bridge from screens to the Repository. Business decisions stay in the domain engine;
 * this class only routes them.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val repo = Repository(app, prefs)
    val evidence = EvidenceStore(app)
    val gps = Gps(app)

    var lang by mutableStateOf(prefs.lang); private set
    var theme by mutableStateOf(prefs.theme); private set
    var busy by mutableStateOf(false); private set
    var sessionUid by mutableStateOf<String?>(null); private set
    var lastError by mutableStateOf<Err?>(null)
    private val stack = mutableStateListOf<Screen>(Screen.Welcome)
    val screen: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    init {
        // opening the company replays the whole history: do it off the main thread, with the spinner showing
        prefs.mode?.let { m ->
            busy = true
            viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) { runCatching { if (repo.hasCompany(m)) { repo.open(m); true } else false }.getOrDefault(false) }
                busy = false
                if (ok && stack.size == 1 && stack[0] == Screen.Welcome) stack[0] = Screen.Login
            }
        }
        viewModelScope.launch(Dispatchers.IO) { evidence.cleanup() }
    }

    val state: State get() = repo.state.value
    val me: User? get() = sessionUid?.let { state.users[it] }
    val practice: Boolean get() = repo.mode == Mode.PRACTICE

    fun setLanguage(l: String) { lang = l; prefs.lang = l }
    fun setThemeMode(t: String) { theme = t; prefs.theme = t }

    fun go(s: Screen) { lastError = null; stack.add(s) }
    fun replace(s: Screen) { lastError = null; stack[stack.size - 1] = s }
    fun resetTo(s: Screen) { lastError = null; stack.clear(); stack.add(s) }
    fun back(): Boolean { if (stack.size <= 1) return false; lastError = null; stack.removeAt(stack.size - 1); return true }

    fun tab(s: Screen) { resetTo(Screen.Home); if (s != Screen.Home) stack.add(s) }

    /** Start the practice company (built through the real rules engine, PIN 1111 for everyone). */
    fun startPractice() = viewModelScope.launch {
        busy = true
        val ok = runCatching {
            withContext(Dispatchers.Default) {
                if (!repo.hasCompany(Mode.PRACTICE)) {
                    val salt = Ids.rid(10)
                    val events = Practice.build(Pin.hash(Practice.PIN, salt), salt, prefs.deviceId)
                    repo.wipe(Mode.PRACTICE)
                    com.aiec.elevatoros.data.EventDb(getApplication(), Mode.PRACTICE.dbName).use { it.insertAll(events, com.aiec.elevatoros.data.EventDb.SYNCED) }
                }
                repo.open(Mode.PRACTICE)
            }
        }
        busy = false
        ok.exceptionOrNull()?.let { lastError = Err("err_unknown"); startupFailure = it.toString(); return@launch }
        prefs.mode = Mode.PRACTICE
        resetTo(Screen.Login)
    }

    /** last unexpected failure, shown in tests and logs (never contains personal data) */
    var startupFailure: String? = null; private set

    fun openLive() = viewModelScope.launch {
        busy = true
        val has = withContext(Dispatchers.IO) { repo.hasCompany(Mode.LIVE).also { if (it) repo.open(Mode.LIVE) } }
        busy = false
        if (has) { prefs.mode = Mode.LIVE; resetTo(Screen.Login) } else go(Screen.Setup)
    }

    /** New company: the Head Quarter creates the company and themself, with their own (non-trivial) PIN. */
    fun createCompany(company: String, hqName: String, phone: String, pin: String, onErr: (Err) -> Unit) = viewModelScope.launch {
        if (company.isBlank() || hqName.isBlank()) return@launch onErr(Err("err_fill"))
        if (!Pin.valid(pin)) return@launch onErr(Err("err_pin_weak"))
        busy = true
        val uid = Ids.uid()
        val err = withContext(Dispatchers.Default) {
            repo.open(Mode.LIVE)
            val salt = Ids.rid(10); val hash = Pin.hash(pin, salt)
            repo.perform(uid, "ORG_CREATED", null, jsonOf("orgId" to "ORG" + Ids.rid(8), "name" to company.trim()))
                ?: repo.perform(uid, "USER_ADDED", null, jsonOf("uid" to uid, "name" to hqName.trim(), "role" to "owner", "pin" to hash, "salt" to salt, "pinLen" to pin.length, "phone" to phone))
        }
        busy = false
        if (err != null) { repo.wipe(Mode.LIVE); onErr(err); return@launch }
        prefs.mode = Mode.LIVE; prefs.lastUser = uid; sessionUid = uid
        resetTo(Screen.Home)
    }

    /** PIN check with lock-out: 5 wrong tries lock that person on this phone for 15 minutes. */
    fun login(uid: String, pin: String, onResult: (String?) -> Unit) = viewModelScope.launch {
        val u = state.users[uid] ?: return@launch onResult("err_no_user")
        val now = System.currentTimeMillis()
        if (prefs.lockedUntil(uid) > now) return@launch onResult("err_locked")
        busy = true
        val ok = withContext(Dispatchers.Default) { Pin.matches(pin, u) }
        busy = false
        if (ok && u.active) {
            prefs.setFailedPins(uid, 0); prefs.lastUser = uid; sessionUid = uid; resetTo(Screen.Home); onResult(null)
        } else {
            val n = prefs.failedPins(uid) + 1
            prefs.setFailedPins(uid, n)
            if (n >= 5) { prefs.setLockedUntil(uid, now + 15 * 60_000L); prefs.setFailedPins(uid, 0); onResult("err_locked") } else onResult(if (!u.active) "err_no_user" else "err_pin_wrong")
        }
    }

    fun logout() { sessionUid = null; resetTo(Screen.Login) }
    fun lock() { if (sessionUid != null) { sessionUid = null; resetTo(Screen.Login) } }

    fun leaveCompany() { sessionUid = null; repo.close(); prefs.mode = null; resetTo(Screen.Welcome) }

    /** The single write path from the UI. */
    fun perform(type: String, pid: String?, data: JsonObject): Err? {
        val uid = sessionUid ?: return Err("err_no_user")
        val e = repo.perform(uid, type, pid, data)
        lastError = e
        return e
    }

    fun roleOf(uid: String?): Role? = state.roleOf(uid)
}
