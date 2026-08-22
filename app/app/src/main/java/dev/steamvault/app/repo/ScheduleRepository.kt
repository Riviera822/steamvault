package dev.steamvault.app.repo

import dev.steamvault.app.net.VaultApiClient
import dev.steamvault.app.net.model.ScheduleOut

/**
 * Suspend-based repository over `GET /v1/schedule` (WP AG-3). The seventh of
 * this app's repository seams — same thin "typed name for the client call"
 * shape as [GamesRepository]/[JobsRepository]/[CacheRepository]/
 * [ClientsRepository]/[MappingRepository]/[SettingsRepository] — so demo
 * mode (`dev.steamvault.app.demo.DemoScheduleRepository`) can hand
 * [dev.steamvault.app.ui.settings.SettingsController] fixture schedule state
 * without that class ever depending on [VaultApiClient] directly.
 */
interface ScheduleRepository {
    suspend fun get(): ScheduleOut
}

class VaultScheduleRepository(private val client: VaultApiClient) : ScheduleRepository {
    override suspend fun get(): ScheduleOut = client.schedule()
}
