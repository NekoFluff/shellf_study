package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.sync.SyncScheduler

/** The single place that sequences a full logout — clearing the token, cancelling background
 *  sync work, resetting notification state, and wiping the previous account's cached
 *  data — so every caller stays in agreement.
 *
 *  Returns what the cache wipe managed to do. The logout itself is unconditional — once the token is
 *  gone the user is logged out whatever the wipe reports — so a caller that wants to record an
 *  [AccountCleanupOutcome.Partial] can, rather than having the cleaner's failures stop here. */
class LogoutCoordinator(
    private val tokenRepository: TokenRepository,
    private val syncScheduler: SyncScheduler,
    private val notificationCoordinator: NotificationCoordinator,
    private val accountDataCleaner: AccountDataCleaner
) {
    suspend fun logout(): AccountCleanupOutcome {
        tokenRepository.clearToken()
        syncScheduler.cancelPeriodicSync()
        notificationCoordinator.onLogout()
        return accountDataCleaner.clearAll()
    }
}
