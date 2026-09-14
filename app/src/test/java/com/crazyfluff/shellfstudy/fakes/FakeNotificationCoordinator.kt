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
        throwOnRescheduleDailyReminder?.let { throw it }
    }

    override suspend fun rescheduleNextReviewCheck() {
        rescheduleNextReviewCheckCallCount++
        throwOnRescheduleNextReviewCheck?.let { throw it }
    }

    var evaluateReviewsAndBacklogCallCount = 0
        private set
    var evaluateStudyReminderCallCount = 0
        private set

    /** Set to make the next call to the matching method throw, to exercise worker retry paths. */
    var throwOnEvaluateReviewsAndBacklog: Throwable? = null
    var throwOnEvaluateStudyReminder: Throwable? = null
    var throwOnRescheduleNextReviewCheck: Throwable? = null
    var throwOnRescheduleDailyReminder: Throwable? = null

    override suspend fun evaluateReviewsAndBacklog() {
        evaluateReviewsAndBacklogCallCount++
        throwOnEvaluateReviewsAndBacklog?.let { throw it }
    }

    override suspend fun evaluateStudyReminder() {
        evaluateStudyReminderCallCount++
        throwOnEvaluateStudyReminder?.let { throw it }
    }
}
