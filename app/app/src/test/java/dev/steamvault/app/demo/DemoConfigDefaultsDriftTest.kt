package dev.steamvault.app.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cross-language drift guard (WP AG-3 round 2 fix, blocker B4) — the Kotlin
 * twin of `web/tests/demo-data-config-defaults.test.js`, ported test for
 * test, not merely in spirit. This module cannot import `api/vault_api/
 * config.py`'s Python constants (no cross-language plumbing exists, and
 * this file deliberately does not invent any), but it CAN read that
 * module's source as plain text — the same "structural static analysis of
 * source text" technique this app's other structural pins
 * (`DemoModeImportAllowlistTest`, `DemoModeUiWiringTest`, `Ag3WiringTest`)
 * already use.
 *
 * Why this test exists: `DemoState.kt`'s `SETTINGS_SPECS["auto_gc"]`/
 * `["sweep_include_cached"]` fixture rows shipped in THIS work package with
 * `"off"`/(the pre-fix value) — correct only until someone checks it
 * against `config.py`'s real `DEFAULT_AUTO_GC`/`DEFAULT_SWEEP_INCLUDE_CACHED`
 * (ADR-0014: `"execute"`/`true`) and finds them silently disagreeing on
 * exactly the surface used for demo screenshots (`docs/LEARNINGS.md`: "demo
 * fixtures are a shipped surface" / "twin config files need twin pins").
 * This test makes that class of drift loud on every `testDebugUnitTest`
 * run instead of quiet until the next person happens to compare the two
 * files by hand.
 *
 * **Two DIFFERENT failure modes, two DIFFERENT correct fixes — do not
 * conflate them (same distinction the web original draws):**
 *
 *   1. **Value drift**: the regex below still matches config.py just fine,
 *      but resolves to a DIFFERENT value than [CONFIG_DEFAULT_AUTO_GC]/
 *      [CONFIG_DEFAULT_SWEEP_INCLUDE_CACHED]. The fix belongs in
 *      `DemoState.kt`'s exported constants, never in this file's regexes.
 *   2. **Grammar drift**: the regex below does NOT match config.py at all
 *      — an innocent reformatting changed the SHAPE of the assignment
 *      without changing its meaning. This is NOT a case where `DemoState.kt`
 *      is wrong — the fix is to update THIS file's regex/extractor, never
 *      `DemoState.kt`'s constants.
 * Each assertion below reports which of the two it hit, and only failure
 * mode 1 says "edit DemoState.kt"; failure mode 2 says to update the
 * regex/extractor in THIS file instead.
 */
class DemoConfigDefaultsDriftTest {

    private val configPy: String by lazy {
        val file = File("../../api/vault_api/config.py")
        check(file.exists()) { "expected config.py at ${file.absolutePath}" }
        file.readText(Charsets.UTF_8)
    }

    /** Tolerates the reformattings most likely to happen without changing
     * meaning: an optional type annotation and either quote style. Still a
     * real grammar (not "anything goes") — a genuine rename or a
     * multi-line assignment still fails to match, which is intentional. */
    private fun extractPythonStringConstants(source: String, namePattern: String): Map<String, String> {
        val re = Regex("""^($namePattern)\s*(?::\s*\w+\s*)?=\s*["']([^"']*)["']""", RegexOption.MULTILINE)
        return re.findAll(source).associate { it.groupValues[1] to it.groupValues[2] }
    }

    @Test
    fun `CONFIG_DEFAULT_AUTO_GC matches config py DEFAULT_AUTO_GC, whatever it currently is`() {
        // config.py assigns DEFAULT_AUTO_GC through indirection (an
        // AUTO_GC_* identifier), never a literal string -- but tolerate
        // the direct-literal shape too, since that is also
        // meaning-preserving grammar drift, not a rename.
        val pointerMatch = Regex("""^DEFAULT_AUTO_GC\s*(?::\s*\w+\s*)?=\s*(.+?)\s*(?:#.*)?$""", RegexOption.MULTILINE)
            .find(configPy)
        assertTrue(
            "GRAMMAR DRIFT, not value drift: could not find any 'DEFAULT_AUTO_GC = ...' assignment in " +
                "config.py at all (not even a one-line one this regex could see). If config.py still " +
                "defines this constant, update THIS FILE's regex to match its new shape -- do not touch " +
                "DemoState.kt for this failure.",
            pointerMatch != null,
        )
        val rhs = pointerMatch!!.groupValues[1].trim()
        val literalMatch = Regex("""^["']([^"']*)["']$""").find(rhs)
        val realDefault: String
        if (literalMatch != null) {
            realDefault = literalMatch.groupValues[1]
        } else {
            val autoGcConstants = extractPythonStringConstants(configPy, """AUTO_GC_\w+""")
            val resolved = autoGcConstants[rhs]
            assertTrue(
                "GRAMMAR DRIFT, not value drift: config.py's DEFAULT_AUTO_GC points at '$rhs', which this " +
                    "file's AUTO_GC_* extractor could not resolve to a string constant. Update THIS FILE's " +
                    "extractor (the assignment shape it expects has changed) -- do not touch DemoState.kt " +
                    "for this failure.",
                resolved != null,
            )
            realDefault = resolved!!
        }
        assertEquals(
            "VALUE DRIFT: api/vault_api/config.py's DEFAULT_AUTO_GC is now '$realDefault' but " +
                "DemoState.kt's CONFIG_DEFAULT_AUTO_GC is still '$CONFIG_DEFAULT_AUTO_GC' -- update the " +
                "constant in DemoState.kt to match. Do not edit this file's regex for this failure.",
            realDefault,
            CONFIG_DEFAULT_AUTO_GC,
        )
    }

    @Test
    fun `CONFIG_DEFAULT_SWEEP_INCLUDE_CACHED matches config py DEFAULT_SWEEP_INCLUDE_CACHED, whatever it currently is`() {
        val pointerMatch = Regex("""^DEFAULT_SWEEP_INCLUDE_CACHED\s*(?::\s*\w+\s*)?=\s*(True|False)\b""", RegexOption.MULTILINE)
            .find(configPy)
        assertTrue(
            "GRAMMAR DRIFT, not value drift: could not find a 'DEFAULT_SWEEP_INCLUDE_CACHED = True|False' " +
                "assignment in config.py at all. If config.py still defines this constant, update THIS " +
                "FILE's regex to match its new shape -- do not touch DemoState.kt for this failure.",
            pointerMatch != null,
        )
        val realDefault = pointerMatch!!.groupValues[1] == "True"
        assertEquals(
            "VALUE DRIFT: api/vault_api/config.py's DEFAULT_SWEEP_INCLUDE_CACHED is now $realDefault but " +
                "DemoState.kt's CONFIG_DEFAULT_SWEEP_INCLUDE_CACHED is still $CONFIG_DEFAULT_SWEEP_INCLUDE_CACHED " +
                "-- update the constant in DemoState.kt to match. Do not edit this file's regex for this failure.",
            realDefault,
            CONFIG_DEFAULT_SWEEP_INCLUDE_CACHED,
        )
    }

    @Test
    fun `MUTATION PIN -- this test actually reads config py's real, current text, not a cached or hardcoded copy`() {
        // If the two regexes above were ever replaced with hardcoded
        // expected strings instead of parsing configPy, this file's own
        // source-reading machinery would go unexercised and the two tests
        // above would degrade into "DemoState.kt agrees with itself" --
        // worthless. Assert the raw source actually contains the constant
        // NAMES at all, so a typo'd path (silently reading an empty/wrong
        // file) fails loudly here instead of both tests above vacuously
        // passing.
        assertTrue(Regex("""DEFAULT_AUTO_GC\s*(?::\s*\w+\s*)?=""").containsMatchIn(configPy))
        assertTrue(Regex("""DEFAULT_SWEEP_INCLUDE_CACHED\s*(?::\s*\w+\s*)?=""").containsMatchIn(configPy))
    }
}
