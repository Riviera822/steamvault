package dev.steamvault.app.ui.settings.logic

import dev.steamvault.app.net.model.ScheduleOut
import dev.steamvault.app.ui.downloads.logic.formatTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Literal wording contract for `SchedulePresentation.kt` (round 2 fix,
 * should-fix S1). **String-resource exception invoked, same as
 * `CachedPrefillOutcome.kt`/`BulkPlan.kt`/`LibraryFilters.kt`
 * (`app/README.md` "String resources" -- "Narrow exception: a verbatim,
 * diffable port of a web module's own literal").** Both of that rule's
 * conditions hold here: (1) every string `sweepTargetsMessage`/
 * `cachedSweepGcRiskWarning` build is "whatever `web/js/lib/schedule-
 * presentation.js` already decided" -- ported for the review-round-1
 * DECISIONS behind the wording, not an independent Android copy choice
 * (see that file's own kdoc); (2) THIS file is condition (2)'s test --
 * every expected value below is hand-transcribed directly from
 * `schedule-presentation.js`'s own literals, never built from the same
 * string-template pieces `SchedulePresentation.kt` itself uses, so a
 * wording drift in either module fails a literal mismatch here rather
 * than two derivations happening to agree (same technique
 * `CachedPrefillOutcomeWordingContractTest`/
 * `StatusIconCrossFrontendContractTest` already apply).
 *
 * **The one legitimate exception to "no derived pieces," carried over from
 * `ui/downloads/logic/FormatTest.kt`'s own precedent:** `formatTimestamp`
 * is locale/JVM-default-dependent (`DateFormat.getDateTimeInstance()`), so
 * no literal hand-transcription of ITS output is portable across machines
 * -- `FormatTest.kt` itself only ever asserts "not the placeholder" for a
 * real timestamp, never an exact rendered string. Every message below that
 * embeds a timestamp therefore splices in `formatTimestamp(...)` -- a
 * SEPARATELY, already-pinned pure function, not a piece of the STATIC
 * template text under test here -- while the surrounding template text
 * (the actual thing being pinned) is hand-transcribed byte for byte. Two
 * messages below embed NO dynamic value at all
 * (`cachedSweepGcRiskWarning`'s warning text, and the "never run" branch of
 * `sweepTargetsMessage`) and are asserted as a single, complete,
 * hand-transcribed literal with nothing spliced in -- the strongest form
 * of this pin, and the one S1 specifically asked for.
 */
class SchedulePresentationWordingContractTest {

    private fun schedule(
        lastSweepAt: String? = null,
        lastSweepTargets: Int? = null,
        lastSweepEnqueued: Int? = null,
        sweepCachedGcRisk: Boolean = false,
    ) = ScheduleOut(
        enabled = true,
        window = "22:00-06:00",
        interval_minutes = 180,
        client_stale_days = 7,
        server_timezone = "UTC+00:00",
        last_sweep_at = lastSweepAt,
        last_sweep_targets = lastSweepTargets,
        last_sweep_enqueued = lastSweepEnqueued,
        sweep_cached_gc_risk = sweepCachedGcRisk,
    )

    // -----------------------------------------------------------------
    // cachedSweepGcRiskWarning -- fully static, zero dynamic content.
    // -----------------------------------------------------------------

    @Test
    fun `wording -- cachedSweepGcRiskWarning, hand-transcribed byte for byte from schedule-presentation js`() {
        assertEquals(
            "The sweep is set to include cached games while garbage collection is not set to execute. " +
                "Any game this configuration refreshes would leave its previous chunks on disk instead of " +
                "freeing them — if the sweep runs, disk usage would grow over time. The sweep is never " +
                "refused and GC is never turned on automatically — turn off “Include cached games”, or set " +
                "Auto-GC to Execute, if you want to avoid this.",
            cachedSweepGcRiskWarning(schedule(sweepCachedGcRisk = true)),
        )
    }

    // -----------------------------------------------------------------
    // sweepTargetsMessage -- one fully-static branch, three with a
    // formatTimestamp-supplied dynamic segment (see class kdoc).
    // -----------------------------------------------------------------

    @Test
    fun `wording -- never run, fully static`() {
        assertEquals(
            "The scheduled sweep has not run yet.",
            sweepTargetsMessage(schedule(lastSweepAt = null, lastSweepTargets = null)),
        )
    }

    @Test
    fun `wording -- started, no result yet`() {
        val whenText = formatTimestamp("2026-08-22T10:00:00Z")
        assertEquals(
            "A sweep started ($whenText) but has not recorded a result yet — it may still be " +
                "running, or it may have stopped before finishing.",
            sweepTargetsMessage(schedule(lastSweepAt = "2026-08-22T10:00:00Z", lastSweepTargets = null)),
        )
    }

    @Test
    fun `wording -- zero targets`() {
        val whenText = formatTimestamp("2026-08-22T10:00:00Z")
        assertEquals(
            "The last run ($whenText) found no games to check. If that is unexpected, check whether any " +
                "PC agent has reported installed games, and whether the “Include cached games” setting " +
                "covers what you expect it to.",
            sweepTargetsMessage(schedule(lastSweepAt = "2026-08-22T10:00:00Z", lastSweepTargets = 0, lastSweepEnqueued = 0)),
        )
    }

    @Test
    fun `wording -- positive targets, plural`() {
        val whenText = formatTimestamp("2026-08-22T10:00:00Z")
        assertEquals(
            "The last run ($whenText) checked 7 games and started 2 new jobs.",
            sweepTargetsMessage(schedule(lastSweepAt = "2026-08-22T10:00:00Z", lastSweepTargets = 7, lastSweepEnqueued = 2)),
        )
    }

    @Test
    fun `wording -- positive targets, singular game and job`() {
        val whenText = formatTimestamp("2026-08-22T10:00:00Z")
        assertEquals(
            "The last run ($whenText) checked 1 game and started 1 new job.",
            sweepTargetsMessage(schedule(lastSweepAt = "2026-08-22T10:00:00Z", lastSweepTargets = 1, lastSweepEnqueued = 1)),
        )
    }
}
