package com.aiec.elevatoros

import com.aiec.elevatoros.domain.Rules
import com.aiec.elevatoros.domain.Stage
import com.aiec.elevatoros.domain.Role
import com.aiec.elevatoros.i18n.I18n
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Words are checked mechanically, like the original's `npm run check`: three languages, same {placeholders}, nothing missing. */
class I18nTest {
    private val dict = I18n.parse(File("src/main/assets/i18n.json").readText())
    private val ph = Regex("\\{(\\w+)}")

    @Test fun everyEntryHasThreeLanguagesAndSamePlaceholders() {
        val bad = dict.filter { (_, v) -> v.size != 3 || v.any { it.isBlank() } || v.map { s -> ph.findAll(s).map { it.value }.toSortedSet() }.toSet().size != 1 }
        assertTrue("bad entries: ${bad.keys}", bad.isEmpty())
        // Hindi and Marathi entries are really written in Devanagari (not copies of the English)
        val notDevanagari = dict.filter { (_, v) -> v[0].any { it.isLetter() } && (v[1] == v[0] || v[2] == v[0]) && v[0].any { c -> c in 'a'..'z' } && v[0].length > 4 && !v[0].matches(Regex("[A-Z0-9 ()/.·-]+")) }
        assertTrue("untranslated: ${notDevanagari.keys.take(20)}", notDevanagari.size <= 5)
    }

    @Test fun everyKeyTheCodeAsksForExists() {
        val code = File("src/main/java").walk().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val used = Regex("(?:\\b(?:t|tr)\\(|I18n\\.t\\([A-Za-z.]+,)\\s*\"([a-z][a-z0-9_]*)\"").findAll(code).map { it.groupValues[1] }.toSet() +
            Regex("\"(err_[a-z0-9_]+)\"").findAll(code).map { it.groupValues[1] }.toSet()
        val missing = used.filter { it !in dict && !it.endsWith("_") }
        assertTrue("missing keys: $missing", missing.isEmpty())
        val families = Stage.entries.map { "st_" + it.key } + Role.entries.map { "r_" + it.key } + Rules.RULES.keys.map { "ev_$it" }
        val missingFam = families.filter { it !in dict }
        assertTrue("missing family keys: $missingFam", missingFam.isEmpty())
    }

    @Test fun placeholdersAreFilled() {
        I18n.loadText(File("src/main/assets/i18n.json").readText())
        assertEquals(true, I18n.t("mr", "bar_day_of", mapOf("n" to "2", "limit" to "3")).contains("2"))
        assertTrue(!I18n.t("hi", "next_line", mapOf("who" to "A", "what" to "B")).contains("{"))
    }

    @Test fun fillReplacesPlaceholdersWithoutRegex() {
        assertEquals("Namaste, Anil", I18n.fill("Namaste, {n}", mapOf("n" to "Anil")))
        assertEquals("Day 2 of 3", I18n.fill("Day {n} of {limit}", mapOf("n" to "2", "limit" to "3")))
        assertEquals("{ not a name } and ", I18n.fill("{ not a name } and {x}", emptyMap()))
        assertEquals("a{", I18n.fill("a{", mapOf("x" to "1")))
    }

    /**
     * Android's ICU regex is stricter than desktop Java: an unescaped "}" or "]" that is not part of a quantifier or
     * class throws PatternSyntaxException on the phone only. This scans every Regex literal in the app for that mistake.
     */
    @Test fun regexLiteralsAreValidOnAndroid() {
        val code = File("src/main/java").walk().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        val bad = Regex("Regex\\(\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(code).map { it.groupValues[1] }.filter { lit ->
            // remove escaped chars and valid {m,n} quantifiers, then any "}" left over is a bare brace
            val stripped = lit.replace(Regex("\\\\\\\\."), "").replace(Regex("\\{\\d+(,\\d*)?\\}"), "")
            stripped.contains('}')
        }.toList()
        assertTrue("regex with a bare } (crashes on Android): $bad", bad.isEmpty())
    }
}
