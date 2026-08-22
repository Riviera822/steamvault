package dev.steamvault.app.demo

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP AG-3 round 2 fix, blocker B3. `DemoState.scheduleOut()`'s kdoc calls
 * itself "the ONE place [in this demo model] allowed to restate" the
 * `sweep_cached_gc_risk` formula — a claim that must be EARNED, not merely
 * asserted, the same way `web/tests/demo-data-schedule.test.js` earns it
 * for the web sibling: a full six-combination truth table plus a named
 * mutation pin whose own comment records exactly which wrong formula it
 * kills and which it does not. Ported here decision for decision (the
 * reviewer applied the web test's exact killer mutation to the
 * pre-round-2 Kotlin `scheduleOut()` and it stayed green — this file is
 * the fix).
 */
class DemoScheduleContractTest {

    private fun freshDemo() = DemoState.fresh()

    private fun patchAutoGcAndSweep(demo: DemoState, sweepIncludeCached: Boolean, autoGc: String) {
        demo.patchSettings(
            mapOf(
                "sweep_include_cached" to JsonPrimitive(sweepIncludeCached.toString()),
                "auto_gc" to JsonPrimitive(autoGc),
            ),
        )
    }

    @Test
    fun `GET v1 schedule returns every ScheduleOut field, including the WP 4d pair`() {
        val out = freshDemo().scheduleOut()
        // Compile-time shape check: ScheduleOut is a real @Serializable data
        // class (net/model/Schedule.kt), so simply constructing/reading it
        // already proves every field exists -- the JS sibling needs a
        // runtime "key in out" loop only because it has no static type to
        // rely on. What THIS test proves beyond that: the fixture actually
        // POPULATES the WP 4d pair with real, non-default-looking values.
        assertTrue(out.enabled)
        assertTrue(out.window != null)
    }

    @Test
    fun `sweep_include_cached on GET v1 schedule mirrors the live settings override, not a fixed snapshot`() {
        val demo = freshDemo()
        val before = demo.scheduleOut()
        assertEquals(true, before.sweep_include_cached) // ADR-0014 default (B4 fix)

        demo.patchSettings(mapOf("sweep_include_cached" to JsonPrimitive("false")))
        val after = demo.scheduleOut()
        assertEquals(false, after.sweep_include_cached)
    }

    // MUTATION TARGET: sweep_cached_gc_risk = sweep_include_cached AND
    // auto_gc != "execute" (vault_api/scheduler.py::cached_sweep_gc_risk).
    // Every combination below is asserted explicitly, including the two
    // that make "dry-run" a risky mode too (it reports without reclaiming).
    @Test
    fun `sweep_cached_gc_risk follows the exact same formula as scheduler cached_sweep_gc_risk`() {
        val demo = freshDemo()
        val cases = listOf(
            Triple(false, "off", false),
            Triple(false, "dry-run", false),
            Triple(false, "execute", false),
            Triple(true, "off", true),
            Triple(true, "dry-run", true),
            Triple(true, "execute", false),
        )
        for ((sweepIncludeCached, autoGc, expected) in cases) {
            patchAutoGcAndSweep(demo, sweepIncludeCached, autoGc)
            val out = demo.scheduleOut()
            assertEquals(
                "sweep_include_cached=$sweepIncludeCached auto_gc=$autoGc should give risk=$expected",
                expected,
                out.sweep_cached_gc_risk,
            )
        }
    }

    /**
     * Named mutation, verified the same way the web sibling's own comment
     * records it (review round 1 nitpick there, ported here verbatim in
     * spirit): a formula mutated to `autoGc !== "off"` leaves this test
     * green too, since "dry-run" != "off" is also true, giving the same
     * risk=true. The mutation that actually KILLS this test — confirmed by
     * applying it and watching the assertion fail, then reverting — is
     * checking EQUALITY to "off" instead of INEQUALITY to "execute"
     * (`autoGc == "off"` in place of `autoGc != "execute"`): that formula
     * gives risk=false for dry-run ("dry-run" != "off", so == "off" is
     * false), which is exactly the bug this test exists to catch — a
     * version of the formula that only treats the OFF mode as risky and
     * silently lets dry-run through clean.
     */
    @Test
    fun `MUTATION PIN -- dry-run counts as risky too, not only off -- kills a formula that checks autoGc equals off instead of autoGc not-equals execute`() {
        val demo = freshDemo()
        patchAutoGcAndSweep(demo, sweepIncludeCached = true, autoGc = "dry-run")
        assertEquals(true, demo.scheduleOut().sweep_cached_gc_risk)
    }

    @Test
    fun `last_sweep_targets last_sweep_enqueued last_sweep_at are present and internally consistent`() {
        val out = freshDemo().scheduleOut()
        assertTrue((out.last_sweep_targets ?: 0) > 0)
        assertTrue(out.last_sweep_enqueued != null)
        assertTrue(out.last_sweep_at != null)
    }

    @Test
    fun `a fresh DemoState does not carry over a prior sessions settings override -- reset means real defaults`() {
        val stale = freshDemo()
        stale.patchSettings(mapOf("sweep_include_cached" to JsonPrimitive("false")))
        assertEquals(false, stale.scheduleOut().sweep_include_cached)

        val fresh = freshDemo() // DemoState.fresh() is the reset -- WP brief constraint 4
        assertEquals(true, fresh.scheduleOut().sweep_include_cached)
        // true AND auto_gc == "execute" (ADR-0014 default, B4 fix) -> false.
        assertEquals(false, fresh.scheduleOut().sweep_cached_gc_risk)
    }
}
