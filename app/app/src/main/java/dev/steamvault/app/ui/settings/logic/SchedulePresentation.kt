package dev.steamvault.app.ui.settings.logic

import dev.steamvault.app.net.model.ScheduleOut
import dev.steamvault.app.ui.downloads.logic.formatTimestamp

/**
 * Pure presentation logic for `GET /v1/schedule` (WP AG-3) — a direct Kotlin
 * port of `web/js/lib/schedule-presentation.js`'s two functions, decision for
 * decision, not merely wording for wording.
 *
 * `sweep_cached_gc_risk` is computed server-side, from the EFFECTIVE
 * (override-resolved) `sweep_include_cached`/`auto_gc` settings —
 * `api/vault_api/routers/schedule.py`'s own field docstring says a UI can
 * render it "without re-deriving the two settings' interaction itself".
 * Neither function below recomputes that condition from the two settings
 * again. Two call sites computing the same domain predicate WILL diverge
 * (`docs/LEARNINGS.md`) — the fix is not "get the client copy right", it is
 * "have only one copy", and that copy is [ScheduleOut.sweep_cached_gc_risk]
 * itself.
 *
 * The web port's two review-round-1 blockers, both preserved as decisions
 * here (see `schedule-presentation.js`'s own header for the full story):
 *  1. `last_sweep_targets == null` has THREE causes tangled in ONE nullable
 *     field — distinguished only by whether [ScheduleOut.last_sweep_at] is
 *     also null (`api/vault_api/scheduler.py::claim_sweep` stamps
 *     `last_sweep_at` and nulls both counters in ONE statement;
 *     `finish_sweep` fills them in only once the sweep completes). Never
 *     collapse "never ran" and "started, no result yet" into one message.
 *  2. `sweep_cached_gc_risk` is a CONFIGURATION statement, not an activity
 *     claim — it can be `true` with no schedule window configured at all
 *     (nothing currently running). Every clause in [cachedSweepGcRiskWarning]
 *     is phrased "is set to"/"would", never "is"/"will".
 *
 * **Type-level note on the web port's "MUTATION PIN: only a literal boolean
 * true triggers the warning" test — corrected after actually measuring it,
 * not assumed.** The first version of this note claimed [VaultJson]'s
 * `isLenient = false` closes this hole entirely at the model boundary. It
 * does not, fully: `SerializationRoundTripTest` measured that a JSON
 * NUMBER (`1`) for [ScheduleOut.sweep_cached_gc_risk] does fail decoding
 * outright, but a JSON STRING that happens to spell `"true"`/`"false"`
 * decodes successfully to that boolean anyway — kotlinx.serialization's
 * boolean decoding reads the `JsonPrimitive`'s string content regardless of
 * whether it arrived quoted, and `isLenient` does not gate that coercion.
 * So the closed half of the wire-level hole is the NUMERIC one; the
 * string-spelled-as-a-boolean half is a documented residual (see that
 * test's "MEASURED" cases), not a guarantee. What IS still true, and is
 * this function's own contribution regardless of which wire shapes survive
 * decoding: [cachedSweepGcRiskWarning] negates exactly one value —
 * `schedule.sweep_cached_gc_risk`, already forced by the Kotlin type
 * system into an actual non-null `Boolean` by the time this function
 * runs — never a truthiness-style check over some OTHER expression that
 * could later be "simplified" into accepting something else. (N1, round 2:
 * the code is `!schedule.sweep_cached_gc_risk`, a negation, not a `==
 * true` comparison — equivalent in effect, but this sentence used to claim
 * the wrong mechanism, which is exactly the kind of overclaim this file's
 * other corrections above exist to stop making.)
 *
 * **String-resource exception invoked, same as `CachedPrefillOutcome.kt`/
 * `BulkPlan.kt`/`LibraryFilters.kt` (`app/README.md` "String resources" —
 * "Narrow exception: a verbatim, diffable port of a web module's own
 * literal").** Every message [sweepTargetsMessage]/[cachedSweepGcRiskWarning]
 * build stays a Kotlin string literal in this file, not a `strings.xml`
 * resource: (1) the wording is "whatever `web/js/lib/schedule-
 * presentation.js` already decided" (this file's own header above quotes
 * that module's review-round-1 decisions, not an independent Android
 * copy choice), and (2) `SchedulePresentationWordingContractTest` pins
 * every one of them by hand-transcribed STRING EQUALITY, the condition
 * the exception requires (the one carve-out: a message embedding
 * `formatTimestamp`'s locale-dependent output splices in that
 * SEPARATELY-tested function rather than hand-transcribing its result,
 * exactly as `ui/downloads/logic/FormatTest.kt` already established for
 * that function elsewhere — see the contract test's own kdoc).
 */

/**
 * The "did the last scheduled sweep actually do anything" line.
 *
 * @return `null` when there is nothing honest to print (no schedule
 *   snapshot yet, i.e. `schedule == null` — no fetch, or a failed one).
 */
fun sweepTargetsMessage(schedule: ScheduleOut?): String? {
    if (schedule == null) return null
    val targets = schedule.last_sweep_targets
    val hasTimestamp = !schedule.last_sweep_at.isNullOrEmpty()

    if (targets == null) {
        if (!hasTimestamp) {
            return "The scheduled sweep has not run yet."
        }
        // claim_sweep stamps last_sweep_at and NULLs both counters in one
        // statement; finish_sweep fills them in only once the sweep
        // completes. A stamped timestamp with no counters therefore means a
        // sweep STARTED and has not (yet, or ever) recorded a result —
        // state both remaining possibilities rather than choosing one.
        val whenText = formatTimestamp(schedule.last_sweep_at)
        return "A sweep started ($whenText) but has not recorded a result yet — it may still be " +
            "running, or it may have stopped before finishing."
    }

    val whenText = formatTimestamp(if (hasTimestamp) schedule.last_sweep_at else null)

    if (targets == 0) {
        return "The last run ($whenText) found no games to check. If that is unexpected, check whether any " +
            "PC agent has reported installed games, and whether the “Include cached games” setting " +
            "covers what you expect it to."
    }

    val enqueued = schedule.last_sweep_enqueued ?: 0
    val gameWord = if (targets == 1) "game" else "games"
    val jobWord = if (enqueued == 1) "job" else "jobs"
    return "The last run ($whenText) checked $targets $gameWord and started $enqueued new $jobWord."
}

/**
 * The "keeping the cache current without collecting" warning (never a
 * block, never an auto-fix — the operator decides).
 *
 * @return the warning text, or `null` — including when [schedule] is `null`
 *   and when [ScheduleOut.sweep_cached_gc_risk] is `false`.
 */
fun cachedSweepGcRiskWarning(schedule: ScheduleOut?): String? {
    if (schedule == null || !schedule.sweep_cached_gc_risk) return null
    return "The sweep is set to include cached games while garbage collection is not set to execute. " +
        "Any game this configuration refreshes would leave its previous chunks on disk instead of " +
        "freeing them — if the sweep runs, disk usage would grow over time. The sweep is never " +
        "refused and GC is never turned on automatically — turn off “Include cached games”, or set " +
        "Auto-GC to Execute, if you want to avoid this."
}
