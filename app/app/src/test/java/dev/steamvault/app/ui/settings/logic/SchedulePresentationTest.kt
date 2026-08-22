package dev.steamvault.app.ui.settings.logic

import dev.steamvault.app.net.model.ScheduleOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP AG-3: Kotlin port of `web/tests/schedule-presentation.test.js`'s cases,
 * decision for decision -- see `SchedulePresentation.kt`'s own kdoc for why
 * the "only a literal boolean true" mutation pin moves to the model
 * boundary in this client (see [ScheduleModelTest]).
 */
class SchedulePresentationTest {

    private fun schedule(
        lastSweepAt: String? = null,
        lastSweepTargets: Int? = null,
        lastSweepEnqueued: Int? = null,
        sweepCachedGcRisk: Boolean = false,
        enabled: Boolean = true,
    ) = ScheduleOut(
        enabled = enabled,
        window = if (enabled) "22:00-06:00" else null,
        interval_minutes = 180,
        client_stale_days = 7,
        server_timezone = "UTC+00:00",
        last_sweep_at = lastSweepAt,
        last_sweep_targets = lastSweepTargets,
        last_sweep_enqueued = lastSweepEnqueued,
        sweep_cached_gc_risk = sweepCachedGcRisk,
    )

    // -----------------------------------------------------------------
    // sweepTargetsMessage -- three/four distinct messages
    // -----------------------------------------------------------------

    @Test
    fun `no schedule snapshot yet -- null`() {
        assertNull(sweepTargetsMessage(null))
    }

    @Test
    fun `last_sweep_at AND last_sweep_targets both null -- the never-ran message`() {
        val msg = sweepTargetsMessage(schedule(lastSweepAt = null, lastSweepTargets = null))
        assertTrue(msg!!.contains("has not run yet"))
        assertFalse(msg.contains("found no games to check"))
        assertFalse(msg.contains("started"))
    }

    @Test
    fun `last_sweep_at STAMPED but last_sweep_targets still null -- a THIRD message, names both possibilities, picks neither`() {
        val neverRun = sweepTargetsMessage(schedule(lastSweepAt = null, lastSweepTargets = null))
        val startedNoResult = sweepTargetsMessage(schedule(lastSweepAt = "2026-08-22T10:00:00Z", lastSweepTargets = null))
        assertNotEquals(neverRun, startedNoResult)
        assertTrue(startedNoResult!!.contains("started"))
        assertTrue(startedNoResult.contains("still be running"))
        assertTrue(startedNoResult.contains("stopped before finishing"))
        assertFalse(startedNoResult.contains("has not run yet"))
        assertFalse(startedNoResult.contains("found no games to check"))
    }

    @Test
    fun `MUTATION PIN -- last_sweep_targets 0 is a DIFFERENT message from null-with-no-timestamp`() {
        val neverRun = sweepTargetsMessage(schedule(lastSweepAt = null, lastSweepTargets = null))
        val zeroTargets = sweepTargetsMessage(schedule(lastSweepAt = "2026-08-22T10:00:00Z", lastSweepTargets = 0, lastSweepEnqueued = 0))
        assertNotEquals(neverRun, zeroTargets)
        assertTrue(zeroTargets!!.contains("found no games to check"))
        assertFalse(zeroTargets.contains("has not run yet"))
    }

    @Test
    fun `zero targets -- offers possibilities to check, never a diagnosis or a named default`() {
        val msg = sweepTargetsMessage(schedule(lastSweepAt = null, lastSweepTargets = 0, lastSweepEnqueued = 0))!!
        assertTrue(msg.contains("check whether", ignoreCase = true))
        assertFalse(msg.contains("off by default", ignoreCase = true))
        assertFalse(msg.contains("no agent is installed", ignoreCase = true))
    }

    @Test
    fun `positive targets -- reports the count and enqueued count, a FOURTH distinct message`() {
        val neverRun = sweepTargetsMessage(schedule(lastSweepAt = null, lastSweepTargets = null))
        val startedNoResult = sweepTargetsMessage(schedule(lastSweepAt = "2026-08-22T09:00:00Z", lastSweepTargets = null))
        val zeroTargets = sweepTargetsMessage(schedule(lastSweepAt = null, lastSweepTargets = 0, lastSweepEnqueued = 0))
        val hasTargets = sweepTargetsMessage(schedule(lastSweepAt = "2026-08-22T10:00:00Z", lastSweepTargets = 7, lastSweepEnqueued = 2))
        assertTrue(hasTargets!!.contains("checked 7 games and started 2 new jobs"))
        assertNotEquals(neverRun, hasTargets)
        assertNotEquals(startedNoResult, hasTargets)
        assertNotEquals(zeroTargets, hasTargets)
    }

    @Test
    fun `singular wording for exactly one game and one job`() {
        val msg = sweepTargetsMessage(schedule(lastSweepAt = null, lastSweepTargets = 1, lastSweepEnqueued = 1))!!
        assertTrue(msg.contains("checked 1 game and started 1 new job."))
    }

    @Test
    fun `null last_sweep_enqueued alongside real targets defaults the enqueued count to zero, never crashes`() {
        val msg = sweepTargetsMessage(schedule(lastSweepAt = "2026-08-22T10:00:00Z", lastSweepTargets = 3, lastSweepEnqueued = null))!!
        assertTrue(msg.contains("started 0 new jobs"))
    }

    // -----------------------------------------------------------------
    // cachedSweepGcRiskWarning
    // -----------------------------------------------------------------

    @Test
    fun `no schedule snapshot yet -- no warning`() {
        assertNull(cachedSweepGcRiskWarning(null))
    }

    @Test
    fun `sweep_cached_gc_risk false -- no warning`() {
        assertNull(cachedSweepGcRiskWarning(schedule(sweepCachedGcRisk = false)))
    }

    @Test
    fun `sweep_cached_gc_risk true -- the warning appears, explains the mechanism, never claims a block or an auto-fix`() {
        val msg = cachedSweepGcRiskWarning(schedule(sweepCachedGcRisk = true))!!
        assertTrue(msg.contains("leave its previous chunks"))
        assertFalse(msg.contains("blocked", ignoreCase = true))
        assertFalse(Regex("automatically (turn|turns|enabl)", RegexOption.IGNORE_CASE).containsMatchIn(msg))
    }

    @Test
    fun `worded as configuration, never present-tense activity`() {
        val msg = cachedSweepGcRiskWarning(schedule(sweepCachedGcRisk = true))!!
        assertTrue(msg.contains("is set to include cached games", ignoreCase = true))
        assertFalse(msg.contains("are being refreshed", ignoreCase = true))
        assertFalse(msg.contains("is being refreshed", ignoreCase = true))
        assertFalse(msg.contains("disk usage will grow", ignoreCase = true))
    }

    @Test
    fun `still configuration-only wording with no schedule window at all (enabled false)`() {
        val msg = cachedSweepGcRiskWarning(schedule(sweepCachedGcRisk = true, enabled = false))!!
        assertFalse(msg.contains("are being refreshed", ignoreCase = true))
        assertFalse(msg.contains("is being refreshed", ignoreCase = true))
        assertFalse(msg.contains("currently", ignoreCase = true))
    }
}
