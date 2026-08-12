package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class CachedDashboardSummary(
    val username: String,
    val level: Int,
    val lessonCount: Int,
    val reviewCount: Int,
    val lastSyncedAtMillis: Long
)

/**
 * Last-known user/level/lesson/review counts, persisted so the dashboard has something to render
 * immediately on a cold start rather than blocking on network — unlike the rest of the dashboard's
 * data, [WaniKaniRepository]'s user/summary fetches have no Room cache backing them.
 */
@Singleton
class DashboardCacheRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val usernameKey = stringPreferencesKey("dashboard_cache_username")
    private val levelKey = intPreferencesKey("dashboard_cache_level")
    private val lessonCountKey = intPreferencesKey("dashboard_cache_lesson_count")
    private val reviewCountKey = intPreferencesKey("dashboard_cache_review_count")
    private val lastSyncedAtKey = longPreferencesKey("dashboard_cache_last_synced_at")

    // dataStore is shared app-wide (see TokenRepository.tokenFlow for the fuller account), so
    // distinctUntilChanged() keeps an unrelated write elsewhere from re-triggering this flow's
    // collectors.
    val cachedSummary: Flow<CachedDashboardSummary?> = dataStore.data.map { prefs ->
        val username = prefs[usernameKey] ?: return@map null
        val level = prefs[levelKey] ?: return@map null
        val lastSyncedAt = prefs[lastSyncedAtKey] ?: return@map null
        CachedDashboardSummary(
            username = username,
            level = level,
            lessonCount = prefs[lessonCountKey] ?: 0,
            reviewCount = prefs[reviewCountKey] ?: 0,
            lastSyncedAtMillis = lastSyncedAt
        )
    }.distinctUntilChanged()

    suspend fun save(username: String, level: Int, lessonCount: Int, reviewCount: Int, syncedAtMillis: Long) {
        dataStore.edit { prefs ->
            prefs[usernameKey] = username
            prefs[levelKey] = level
            prefs[lessonCountKey] = lessonCount
            prefs[reviewCountKey] = reviewCount
            prefs[lastSyncedAtKey] = syncedAtMillis
        }
    }
}
