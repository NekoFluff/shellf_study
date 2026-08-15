package com.crazyfluff.shellfstudy.shared.notifications

import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.NotificationSettings
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * All real notification decision-making lives here — the workers that call this are thin
 * delegates (mirroring [com.crazyfluff.shellfstudy.core.sync.SyncWorker]'s existing thinness), and
 * ViewModels depend on this interface so tests can substitute a call-count fake instead of
 * exercising real repositories/WorkManager.
 */
interface NotificationCoordinator {
    /** Schedules future wakeups only — never posts. Safe to call from any context, including foreground. */
    suspend fun onLogin()

    /** Cancels every scheduled wakeup, clears the notification tray, and resets dedupe state. */
    suspend fun onLogout()

    suspend fun rescheduleDailyReminder()
    suspend fun rescheduleNextReviewCheck()

    /** Posts. Background-only callers (workers). */
    suspend fun evaluateReviewsAndBacklog()

    /** Posts. Background-only callers (workers). */
    suspend fun evaluateStudyReminder()
}

class DefaultNotificationCoordinator(
    private val assignmentRepository: AssignmentRepository,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationStateRepository: NotificationStateRepository,
    private val notificationScheduler: NotificationScheduler,
    private val notificationPoster: NotificationPoster
) : NotificationCoordinator {

    override suspend fun onLogin() {
        rescheduleNextReviewCheck()
        rescheduleDailyReminder()
    }

    override suspend fun onLogout() {
        notificationScheduler.cancelAll()
        listOf(
            NotificationIds.REVIEWS_AVAILABLE,
            NotificationIds.REVIEWS_BACKLOG,
            NotificationIds.STUDY_REMINDER
        ).forEach(notificationPoster::cancel)
        notificationStateRepository.clear()
    }

    override suspend fun rescheduleDailyReminder() {
        val settings = settingsRepository.notificationSettings.first()
        if (!settings.notificationsEnabled || !settings.dailyReminderEnabled) {
            notificationScheduler.cancelDailyStreakReminder()
            return
        }
        notificationScheduler.scheduleDailyStreakReminder(settings.dailyReminderHour)
    }

    override suspend fun rescheduleNextReviewCheck() {
        val settings = settingsRepository.notificationSettings.first()
        if (!settings.notificationsEnabled || !settings.reviewsAvailableEnabled) {
            notificationScheduler.cancelNextReviewCheck()
            return
        }
        val forecast = assignmentRepository.observeReviewForecast().first()
        val nextBucket = forecast.buckets.firstOrNull { it.newlyAvailableCount > 0 }
        notificationScheduler.scheduleNextReviewCheck(nextBucket?.availableAt)
    }

    override suspend fun evaluateReviewsAndBacklog() {
        val settings = settingsRepository.notificationSettings.first()
        if (!settings.notificationsEnabled) return
        val forecast = assignmentRepository.observeReviewForecast().first()
        val state = notificationStateRepository.state.first()
        val now = Clock.System.now()

        if (settings.reviewsAvailableEnabled) {
            when (val decision = WatermarkPolicy.decide(forecast.reviewsAvailableNow, state.lastNotifiedReviewCount)) {
                is WatermarkDecision.Notify -> {
                    if (isQuiet(settings, now)) {
                        notificationScheduler.scheduleNextReviewCheck(quietHoursEnd(settings, now))
                    } else {
                        notificationPoster.post(NotificationBuilder.reviewsAvailable(forecast))
                        notificationStateRepository.updateReviewWatermark(decision.newWatermark)
                    }
                }
                is WatermarkDecision.ResetWatermark -> notificationStateRepository.updateReviewWatermark(decision.newWatermark)
                WatermarkDecision.NoChange -> Unit
            }
        }

        if (settings.reviewsBacklogEnabled) {
            val shouldNotify = BacklogPolicy.shouldNotify(
                currentCount = forecast.reviewsAvailableNow,
                threshold = settings.backlogThreshold,
                lastNotifiedAt = state.lastBacklogNotifiedAt,
                now = now,
                cooldown = BACKLOG_COOLDOWN
            )
            if (shouldNotify) {
                if (isQuiet(settings, now)) {
                    notificationScheduler.scheduleDeferredNotification(DeferredNotificationCategory.BACKLOG, quietHoursEnd(settings, now))
                } else {
                    notificationPoster.post(NotificationBuilder.reviewsBacklog(forecast.reviewsAvailableNow, settings.backlogThreshold))
                    notificationStateRepository.recordBacklogNotified(now)
                }
            }
        }
    }

    override suspend fun evaluateStudyReminder() {
        val settings = settingsRepository.notificationSettings.first()
        if (!settings.notificationsEnabled || !settings.dailyReminderEnabled) return
        val streak = statsRepository.observeStudyStreak().first()
        if (streak.isActiveToday) return

        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(zone)
        val state = notificationStateRepository.state.first()
        if (state.lastStreakReminderSentDate == today) return

        val now = Clock.System.now()
        if (isQuiet(settings, now)) return // the next daily-reminder wakeup already lands at a fixed local hour
        notificationPoster.post(NotificationBuilder.studyReminder(streak.currentStreakDays))
        notificationStateRepository.recordStreakReminderSent(today)
    }

    private fun isQuiet(settings: NotificationSettings, now: Instant): Boolean {
        if (!settings.quietHoursEnabled) return false
        val zone = TimeZone.currentSystemDefault()
        val nowHour = now.toLocalDateTime(zone).hour
        return QuietHours.isQuietNow(nowHour, settings.quietHoursStartHour, settings.quietHoursEndHour)
    }

    private fun quietHoursEnd(settings: NotificationSettings, now: Instant): Instant {
        val zone = TimeZone.currentSystemDefault()
        return QuietHours.nextEndInstant(
            now.toLocalDateTime(zone),
            zone,
            settings.quietHoursStartHour,
            settings.quietHoursEndHour
        )
    }

    private companion object {
        val BACKLOG_COOLDOWN = 6.hours
    }
}
