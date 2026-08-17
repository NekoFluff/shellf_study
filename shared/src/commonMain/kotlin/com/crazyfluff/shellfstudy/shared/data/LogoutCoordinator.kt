package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.sync.PitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.shared.sync.SyncScheduler

/** The single place that sequences a full logout — clearing the token, cancelling background
 *  sync/scrape work, and resetting notification state — so every caller stays in agreement. */
class LogoutCoordinator(
    private val tokenRepository: TokenRepository,
    private val syncScheduler: SyncScheduler,
    private val pitchAccentScrapeScheduler: PitchAccentScrapeScheduler,
    private val notificationCoordinator: NotificationCoordinator
) {
    suspend fun logout() {
        tokenRepository.clearToken()
        syncScheduler.cancelPeriodicSync()
        pitchAccentScrapeScheduler.cancelPeriodicScrape()
        notificationCoordinator.onLogout()
    }
}
