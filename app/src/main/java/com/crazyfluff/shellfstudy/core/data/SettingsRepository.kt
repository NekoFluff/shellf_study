package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

const val DEFAULT_DAILY_LESSON_GOAL = 15

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val dailyLessonGoal: Int = DEFAULT_DAILY_LESSON_GOAL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val dailyLessonGoalKey = intPreferencesKey("daily_lesson_goal")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            dailyLessonGoal = prefs[dailyLessonGoalKey] ?: DEFAULT_DAILY_LESSON_GOAL,
            themeMode = prefs[themeModeKey]?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        )
    }

    suspend fun setDailyLessonGoal(goal: Int) {
        dataStore.edit { it[dailyLessonGoalKey] = goal.coerceIn(1, 99) }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[themeModeKey] = mode.name }
    }
}
