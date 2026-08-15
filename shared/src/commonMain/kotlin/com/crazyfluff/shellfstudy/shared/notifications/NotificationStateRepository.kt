package com.crazyfluff.shellfstudy.shared.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

data class NotificationState(
    val lastNotifiedReviewCount: Int = 0,
    val lastBacklogNotifiedAt: Instant? = null,
    val lastStreakReminderSentDate: LocalDate? = null
)

/**
 * Dedupe/watermark bookkeeping for the notification system — separate from
 * [com.crazyfluff.shellfstudy.shared.data.SettingsRepository] (which is user-facing preferences)
 * since this is internal state the user never edits directly. Shares the same
 * DataStore<Preferences> instance via :app's DataStoreModule.
 */
class NotificationStateRepository(
    private val dataStore: DataStore<Preferences>
) {
    private val lastNotifiedReviewCountKey = intPreferencesKey("notif_last_notified_review_count")
    private val lastBacklogNotifiedAtKey = stringPreferencesKey("notif_last_backlog_notified_at")
    private val lastStreakReminderSentDateKey = stringPreferencesKey("notif_last_streak_reminder_sent_date")

    val state: Flow<NotificationState> = dataStore.data.map { prefs ->
        NotificationState(
            lastNotifiedReviewCount = prefs[lastNotifiedReviewCountKey] ?: 0,
            lastBacklogNotifiedAt = prefs[lastBacklogNotifiedAtKey]?.let { runCatching { Instant.parse(it) }.getOrNull() },
            lastStreakReminderSentDate = prefs[lastStreakReminderSentDateKey]?.let {
                runCatching { LocalDate.parse(it) }.getOrNull()
            }
        )
    }.distinctUntilChanged()

    suspend fun updateReviewWatermark(count: Int) {
        dataStore.edit { it[lastNotifiedReviewCountKey] = count }
    }

    suspend fun recordBacklogNotified(at: Instant) {
        dataStore.edit { it[lastBacklogNotifiedAtKey] = at.toString() }
    }

    suspend fun recordStreakReminderSent(date: LocalDate) {
        dataStore.edit { it[lastStreakReminderSentDateKey] = date.toString() }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(lastNotifiedReviewCountKey)
            prefs.remove(lastBacklogNotifiedAtKey)
            prefs.remove(lastStreakReminderSentDateKey)
        }
    }
}
