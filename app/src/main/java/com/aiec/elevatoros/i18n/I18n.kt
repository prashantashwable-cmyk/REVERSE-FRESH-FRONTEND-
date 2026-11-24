package com.aiec.elevatoros.i18n

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.aiec.elevatoros.domain.Err
import com.aiec.elevatoros.domain.JSON
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Marathi / Hindi / English, chosen inside the app (like the original front door), independent of the phone's language.
 * Words come from assets/i18n.json: { key: [english, hindi, marathi] } — the same layout and, where the meaning is the
 * same, the same wording as the original dictionary (const L). A unit test checks every key used by the code exists
 * in all three languages with the same {placeholders}.
 */
object I18n {
    val LANGS = listOf("mr", "hi", "en")
    val NAMES = mapOf("mr" to "मराठी", "hi" to "हिंदी", "en" to "English")
    private var dict: Map<String, List<String>> = emptyMap()

    fun load(context: Context) {
        if (dict.isNotEmpty()) return
        dict = parse(context.assets.open("i18n.json").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    fun parse(text: String): Map<String, List<String>> = JSON.parseToJsonElement(text).jsonObject.mapValues { (_, v) ->
        (v as JsonArray).map { it.jsonPrimitive.content }
    }

    fun loadText(text: String) { dict = parse(text) }

    fun t(lang: String, key: String, vars: Map<String, String> = emptyMap()): String {
        val e = dict[key]
        var s = if (e == null) key else e[when (lang) { "hi" -> 1; "mr" -> 2; else -> 0 }].ifEmpty { e[0] }
        return if (vars.isEmpty()) s else fill(s, vars)
    }

    /**
     * Replaces {name} with its value. Plain text scanning on purpose: the regex "\\{(\\w+)}" works on desktop Java but
     * Android's ICU regex engine rejects the unescaped "}" and threw at the first screen with a name in it (field crash
     * in 1.0.0–1.1.1, right after the PIN). Never use a regex here.
     */
    fun fill(s: String, vars: Map<String, String>): String {
        val out = StringBuilder(s.length + 16)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '{') {
                val end = s.indexOf('}', i + 1)
                val name = if (end > i) s.substring(i + 1, end) else ""
                if (end > i && name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '_' }) {
                    out.append(vars[name] ?: ""); i = end + 1; continue
                }
            }
            out.append(c); i++
        }
        return out.toString()
    }

    fun has(key: String) = dict.containsKey(key)
}

/** Current language for the UI tree. */
val LocalLang = staticCompositionLocalOf { "mr" }

@Composable
fun t(key: String, vararg vars: Pair<String, String>): String = I18n.t(LocalLang.current, key, vars.toMap())

@Composable
fun errText(e: Err): String {
    val lang = LocalLang.current
    val vars = e.vars.mapValues { (k, v) ->
        when (k) {
            "stage" -> I18n.t(lang, "st_$v")
            "roles" -> v.split(" / ").joinToString(" / ") { I18n.t(lang, "r_$it") }
            "list" -> v.split(", ").joinToString(", ") { I18n.t(lang, "meas_$it") }
            else -> v
        }
    }
    return I18n.t(lang, e.key, vars)
}
