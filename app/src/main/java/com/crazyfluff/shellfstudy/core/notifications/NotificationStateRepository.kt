package com.crazyfluff.shellfstudy.core.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationState(
    val lastNotifiedReviewCount: Int = 0,
    val lastNotifiedLessonCount: Int = 0,
    val lastBacklogNotifiedAt: Instant? = null,
    val lastNotifiedLevel: Int? = null,
    val lastNotifiedBurnedCount: Int = 0,
    /** False until [NotificationStateRepository.updateMilestoneWatermark] has run once — suppresses a spurious
     * "level up"/"burned N items" message on the very first evaluation, before there's a real baseline to compare against. */
    val milestonesInitialized: Boolean = false,
    val lastStreakReminderSentDate: LocalDate? = null
)

/**
 * Dedupe/watermark bookkeeping for the notification system — separate from [com.crazyfluff.shellfstudy.core.data.SettingsRepository]
 * (which is user-facing preferences) since this is internal state the user never edits directly.
 * Shares the same DataStore<Preferences> instance via [com.crazyfluff.shellfstudy.core.data.DataStoreModule].
 */
@Singleton
class NotificationStateRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val lastNotifiedReviewCountKey = intPreferencesKey("notif_last_notified_review_count")
    private val lastNotifiedLessonCountKey = intPreferencesKey("notif_last_notified_lesson_count")
    private val lastBacklogNotifiedAtKey = stringPreferencesKey("notif_last_backlog_notified_at")
    private val lastNotifiedLevelKey = intPreferencesKey("notif_last_notified_level")
    private val lastNotifiedBurnedCountKey = intPreferencesKey("notif_last_notified_burned_count")
    private val milestonesInitializedKey = booleanPreferencesKey("notif_milestones_initialized")
    private val lastStreakReminderSentDateKey = stringPreferencesKey("notif_last_streak_reminder_sent_date")

    val state: Flow<NotificationState> = dataStore.data.map { prefs ->
        NotificationState(
            lastNotifiedReviewCount = prefs[lastNotifiedReviewCountKey] ?: 0,
            lastNotifiedLessonCount = prefs[lastNotifiedLessonCountKey] ?: 0,
            lastBacklogNotifiedAt = prefs[lastBacklogNotifiedAtKey]?.let { runCatching { Instant.parse(it) }.getOrNull() },
            lastNotifiedLevel = prefs[lastNotifiedLevelKey],
            lastNotifiedBurnedCount = prefs[lastNotifiedBurnedCountKey] ?: 0,
            milestonesInitialized = prefs[milestonesInitializedKey] ?: false,
            lastStreakReminderSentDate = prefs[lastStreakReminderSentDateKey]?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }
        )
    }

    suspend fun updateReviewWatermark(count: Int) {
        dataStore.edit { it[lastNotifiedReviewCountKey] = count }
    }

    suspend fun updateLessonWatermark(count: Int) {
        dataStore.edit { it[lastNotifiedLessonCountKey] = count }
    }

    suspend fun recordBacklogNotified(at: Instant) {
        dataStore.edit { it[lastBacklogNotifiedAtKey] = at.toString() }
    }

    suspend fun updateMilestoneWatermark(level: Int?, burnedCount: Int) {
        dataStore.edit { prefs ->
            if (level != null) prefs[lastNotifiedLevelKey] = level else prefs.remove(lastNotifiedLevelKey)
            prefs[lastNotifiedBurnedCountKey] = burnedCount
            prefs[milestonesInitializedKey] = true
        }
    }

    suspend fun recordStreakReminderSent(date: LocalDate) {
        dataStore.edit { it[lastStreakReminderSentDateKey] = date.toString() }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(lastNotifiedReviewCountKey)
            prefs.remove(lastNotifiedLessonCountKey)
            prefs.remove(lastBacklogNotifiedAtKey)
            prefs.remove(lastNotifiedLevelKey)
            prefs.remove(lastNotifiedBurnedCountKey)
            prefs.remove(milestonesInitializedKey)
            prefs.remove(lastStreakReminderSentDateKey)
        }
    }
}
