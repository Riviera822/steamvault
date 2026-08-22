package dev.steamvault.app.net.model

import kotlinx.serialization.Serializable

/**
 * `GET /v1/schedule` — mirrors `vault_api/routers/schedule.py::ScheduleOut`
 * field for field (WP AG-3), same "verbatim snake_case, no renaming layer"
 * convention `Games.kt`'s kdoc documents. Every field here is read STRAIGHT
 * off the wire and rendered as-is — in particular [sweep_cached_gc_risk] is
 * a server-computed pure predicate over `sweep_include_cached`/`auto_gc`
 * (see that file's own field docstring) that this client must never
 * recompute from the two settings itself (`docs/LEARNINGS.md`: "two call
 * sites computing the same domain predicate WILL diverge" — this is exactly
 * the field WP 4d-web's review round 1 got wrong the first time, see
 * `ui/settings/logic/SchedulePresentation.kt`'s kdoc for the port of that
 * fix).
 *
 * **S3, round 2 correction: this endpoint DOES have added-later fields —
 * the previous version of this sentence denied that.** `sweep_include_cached`/
 * `sweep_cached_gc_risk` arrived in WP 4d, after the original WP 3.5
 * `ScheduleOut` shape; a pre-4d vault-api's response omits both keys
 * entirely. [sweep_include_cached]/[sweep_cached_gc_risk] both default to
 * `false` here specifically so that omission decodes safely rather than
 * failing (`ignoreUnknownKeys`/missing-key-defaults, per `VaultJson`'s own
 * kdoc) — and `false`/`false` happens to be the SAFE reading in both
 * directions: "the sweep does not include cached games" and "no GC-risk
 * warning," never a false positive. Every OTHER nullable field below can
 * independently be `null` on a real (post-4d) vault with no sweep history
 * yet — see each field's own comment, copied from the Python source's own
 * docstring.
 */
@Serializable
data class ScheduleOut(
    val enabled: Boolean,
    val window: String? = null,
    val overnight: Boolean = false,
    val interval_minutes: Int,
    val client_stale_days: Int,
    val server_timezone: String,
    /** UTC. `null` if no sweep has ever run. */
    val last_sweep_at: String? = null,
    /** Size of the last sweep's target set. `null` while a sweep is in
     * flight, or if the process died during one (same statement that nulls
     * [last_sweep_enqueued]) — see `ui/settings/logic/SchedulePresentation.kt`. */
    val last_sweep_targets: Int? = null,
    val last_sweep_enqueued: Int? = null,
    val next_eligible_at: String? = null,
    /** Effective value of `sweep_include_cached` / `VAULT_SWEEP_INCLUDE_CACHED`. */
    val sweep_include_cached: Boolean = false,
    /** `true` iff `sweep_include_cached` is on while `auto_gc` is anything
     * other than `"execute"` — a CONFIGURATION statement, not an activity
     * claim (can be `true` with no schedule window at all). Never recompute
     * this client-side. */
    val sweep_cached_gc_risk: Boolean = false,
)
