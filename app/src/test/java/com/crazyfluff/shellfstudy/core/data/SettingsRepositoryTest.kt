package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): SettingsRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return SettingsRepository(dataStore)
    }

    @Test
    fun `settings emits defaults when nothing stored`() = runTest {
        val repository = createRepository()
        repository.settings.test {
            val settings = awaitItem()
            assertThat(settings.dailyLessonGoal).isEqualTo(DEFAULT_DAILY_LESSON_GOAL)
            assertThat(settings.themeMode).isEqualTo(ThemeMode.SYSTEM)
            assertThat(settings.showPitchAccent).isTrue()
            assertThat(settings.autoplayPronunciationAudio).isTrue()
        }
    }

    @Test
    fun `setShowPitchAccent persists the chosen value`() = runTest {
        val repository = createRepository()

        repository.setShowPitchAccent(false)

        repository.settings.test {
            assertThat(awaitItem().showPitchAccent).isFalse()
        }
    }

    @Test
    fun `setAutoplayPronunciationAudio persists the chosen value`() = runTest {
        val repository = createRepository()

        repository.setAutoplayPronunciationAudio(false)

        repository.settings.test {
            assertThat(awaitItem().autoplayPronunciationAudio).isFalse()
        }
    }

    @Test
    fun `setDailyLessonGoal persists and clamps to the valid range`() = runTest {
        val repository = createRepository()

        repository.setDailyLessonGoal(30)
        repository.settings.test { assertThat(awaitItem().dailyLessonGoal).isEqualTo(30) }

        repository.setDailyLessonGoal(500)
        repository.settings.test { assertThat(awaitItem().dailyLessonGoal).isEqualTo(99) }

        repository.setDailyLessonGoal(-5)
        repository.settings.test { assertThat(awaitItem().dailyLessonGoal).isEqualTo(1) }
    }

    @Test
    fun `setThemeMode persists the chosen mode`() = runTest {
        val repository = createRepository()

        repository.setThemeMode(ThemeMode.DARK)

        repository.settings.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.DARK)
        }
    }
}
