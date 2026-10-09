package com.crazyfluff.shellfstudy.shared.notifications

import com.crazyfluff.shellfstudy.shared.data.AssignmentStatsRepository
import com.crazyfluff.shellfstudy.shared.data.NotificationSettings
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpStep
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

    /** Schedules a wakeup for when the next review session that decides the fastest level-up comes
     *  due. Never posts. */
    suspend fun rescheduleLevelUpReminder()

    /** Posts. Background-only callers (workers). */
    suspend fun evaluateReviewsAndBacklog()

    /** Posts. Background-only callers (workers). */
    suspend fun evaluateStudyReminder()

    /** Posts. Background-only callers (workers). */
    suspend fun evaluateLevelUpReminder()
}

class DefaultNotificationCoordinator(
    private val assignmentStatsRepository: AssignmentStatsRepository,
    private val statsRepository: StatsRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationStateRepository: NotificationStateRepository,
    private val notificationScheduler: NotificationScheduler,
    private val notificationPoster: NotificationPoster
) : NotificationCoordinator {

    override suspend fun onLogin() {
        rescheduleNextReviewCheck()
        rescheduleLevelUpReminder()
        rescheduleDailyReminder()
    }

    override suspend fun onLogout() {
        notificationScheduler.cancelAll()
        listOf(
            NotificationIds.REVIEWS_AVAILABLE,
            NotificationIds.REVIEWS_BACKLOG,
            NotificationIds.LEVEL_UP_REVIEWS,
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
        val forecast = assignmentStatsRepository.observeReviewForecast().first()
        val nextBucket = forecast.buckets.firstOrNull { it.newlyAvailableCount > 0 }
        notificationScheduler.scheduleNextReviewCheck(nextBucket?.availableAt)
    }

    /**
     * Rides the deferred-notification path rather than the review check: it fires at a different
     * time (the next session for the level's radicals and kanji, not the next batch of any
     * reviews) and the review check's single unique wakeup can't hold both. There's no cancel for
     * a deferred category, so turning the setting off leaves any pending wakeup in place —
     * [evaluateLevelUpReminder] re-checks the setting when it fires and posts nothing.
     */
    override suspend fun rescheduleLevelUpReminder() {
        val settings = settingsRepository.notificationSettings.first()
        val step = if (settings.notificationsEnabled && settings.levelUpRemindersEnabled) currentLevelUpStep() else null
        // An already-announced session is still "next" until the user works through it. Scheduling
        // it again (at now, since it's due) would only wake evaluate to find it announced — and on
        // every sync after. The next sync after those reviews brings a new step to schedule.
        if (step == null || notificationStateRepository.state.first().lastLevelUpNotifiedStepAt == step.at) return
        notificationScheduler.scheduleDeferredNotification(
            DeferredNotificationCategory.LEVEL_UP,
            maxOf(step.at, Clock.System.now())
        )
    }

    override suspend fun evaluateLevelUpReminder() {
        val settings = settingsRepository.notificationSettings.first()
        if (!settings.notificationsEnabled || !settings.levelUpRemindersEnabled) return
        val step = currentLevelUpStep() ?: return
        val now = Clock.System.now()
        val alreadyNotified = notificationStateRepository.state.first().lastLevelUpNotifiedStepAt == step.at
        when {
            alreadyNotified -> Unit
            // Woken before the session is due (the path moved since this was scheduled): move the
            // wakeup to the real time.
            step.at > now -> rescheduleLevelUpReminder()
            isQuiet(settings, now) -> notificationScheduler.scheduleDeferredNotification(
                DeferredNotificationCategory.LEVEL_UP,
                quietHoursEnd(settings, now)
            )
            else -> {
                notificationPoster.post(NotificationBuilder.levelUpReviewsReady(step))
                notificationStateRepository.recordLevelUpNotified(step.at)
            }
        }
    }

    /** The next session for the current level's radicals and kanji below Guru, or null when there's
     *  no level yet, the level is ready, or nothing is known. */
    private suspend fun currentLevelUpStep(): LevelUpStep? {
        val level = statsRepository.observeCurrentLevel().first() ?: return null
        return assignmentStatsRepository.observeLevelUpPath(level).first().nextStep
    }

    override suspend fun evaluateReviewsAndBacklog() {
        val settings = settingsRepository.notificationSettings.first()
        if (!settings.notificationsEnabled) return
        val forecast = assignmentStatsRepository.observeReviewForecast().first()
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
                    notificationPoster.post(NotificationBuilder.reviewsBacklog(forecast.reviewsAvailableNow))
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
        if (isQuiet(settings, now)) {
            // The fixed local reminder hour can itself fall inside quiet hours (e.g. an evening
            // reminder hour with quiet hours starting earlier that evening), which would otherwise
            // silently skip every day forever since tomorrow's wakeup lands at that same hour.
            notificationScheduler.scheduleDeferredNotification(
                DeferredNotificationCategory.STUDY_REMINDER,
                quietHoursEnd(settings, now)
            )
            return
        }
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
