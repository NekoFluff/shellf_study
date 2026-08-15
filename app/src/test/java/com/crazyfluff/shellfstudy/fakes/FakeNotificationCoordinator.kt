package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator

/** Call-count spy stand-in for [NotificationCoordinator], used by ViewModel tests that only care whether the right lifecycle hook fired. */
class FakeNotificationCoordinator : NotificationCoordinator {
    var onLoginCallCount = 0
        private set
    var onLogoutCallCount = 0
        private set
    var rescheduleDailyReminderCallCount = 0
        private set
    var rescheduleNextReviewCheckCallCount = 0
        private set

    override suspend fun onLogin() {
        onLoginCallCount++
    }

    override suspend fun onLogout() {
        onLogoutCallCount++
    }

    override suspend fun rescheduleDailyReminder() {
        rescheduleDailyReminderCallCount++
    }

    override suspend fun rescheduleNextReviewCheck() {
        rescheduleNextReviewCheckCallCount++
    }

    var evaluateReviewsAndBacklogCallCount = 0
        private set

    override suspend fun evaluateReviewsAndBacklog() {
        evaluateReviewsAndBacklogCallCount++
    }

    override suspend fun evaluateStudyReminder() = Unit
}
