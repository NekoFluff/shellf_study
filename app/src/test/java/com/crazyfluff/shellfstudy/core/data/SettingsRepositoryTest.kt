package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.crazyfluff.shellfstudy.shared.data.DEFAULT_DAILY_LESSON_GOAL
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
            assertThat(settings.showSubjectTypeLabel).isFalse()
            assertThat(settings.showTotalTimer).isFalse()
            assertThat(settings.showQuestionTimer).isFalse()
            assertThat(settings.closeEnoughAnswersEnabled).isTrue()
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
    fun `setShowSubjectTypeLabel persists the chosen value`() = runTest {
        val repository = createRepository()

        repository.setShowSubjectTypeLabel(true)

        repository.settings.test {
            assertThat(awaitItem().showSubjectTypeLabel).isTrue()
        }
    }

    @Test
    fun `setShowTotalTimer persists the chosen value`() = runTest {
        val repository = createRepository()

        repository.setShowTotalTimer(true)

        repository.settings.test {
            assertThat(awaitItem().showTotalTimer).isTrue()
        }
    }

    @Test
    fun `setShowQuestionTimer persists the chosen value`() = runTest {
        val repository = createRepository()

        repository.setShowQuestionTimer(true)

        repository.settings.test {
            assertThat(awaitItem().showQuestionTimer).isTrue()
        }
    }

    @Test
    fun `setCloseEnoughAnswersEnabled persists the chosen value`() = runTest {
        val repository = createRepository()

        repository.setCloseEnoughAnswersEnabled(false)

        repository.settings.test {
            assertThat(awaitItem().closeEnoughAnswersEnabled).isFalse()
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

    @Test
    fun `setThemeMode persists the eink mode`() = runTest {
        val repository = createRepository()

        repository.setThemeMode(ThemeMode.EINK)

        repository.settings.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.EINK)
        }
    }

    @Test
    fun `notificationSettings emits opt-in defaults when nothing stored`() = runTest {
        val repository = createRepository()
        repository.notificationSettings.test {
            val settings = awaitItem()
            assertThat(settings.notificationsEnabled).isFalse()
            assertThat(settings.reviewsAvailableEnabled).isTrue()
            assertThat(settings.reviewsBacklogEnabled).isTrue()
            assertThat(settings.backlogThreshold).isEqualTo(100)
            assertThat(settings.dailyReminderEnabled).isTrue()
            assertThat(settings.dailyReminderHour).isEqualTo(20)
            assertThat(settings.quietHoursEnabled).isTrue()
            assertThat(settings.quietHoursStartHour).isEqualTo(22)
            assertThat(settings.quietHoursEndHour).isEqualTo(7)
        }
    }

    @Test
    fun `setNotificationsEnabled persists the chosen value`() = runTest {
        val repository = createRepository()
        repository.setNotificationsEnabled(true)
        repository.notificationSettings.test { assertThat(awaitItem().notificationsEnabled).isTrue() }
    }

    @Test
    fun `per-category toggles persist independently`() = runTest {
        val repository = createRepository()

        repository.setReviewsAvailableEnabled(false)
        repository.setReviewsBacklogEnabled(false)
        repository.setDailyReminderEnabled(false)

        repository.notificationSettings.test {
            val settings = awaitItem()
            assertThat(settings.reviewsAvailableEnabled).isFalse()
            assertThat(settings.reviewsBacklogEnabled).isFalse()
            assertThat(settings.dailyReminderEnabled).isFalse()
        }
    }

    @Test
    fun `setBacklogThreshold persists and clamps to the valid range`() = runTest {
        val repository = createRepository()

        repository.setBacklogThreshold(100)
        repository.notificationSettings.test { assertThat(awaitItem().backlogThreshold).isEqualTo(100) }

        repository.setBacklogThreshold(1)
        repository.notificationSettings.test { assertThat(awaitItem().backlogThreshold).isEqualTo(5) }

        repository.setBacklogThreshold(1000)
        repository.notificationSettings.test { assertThat(awaitItem().backlogThreshold).isEqualTo(500) }
    }

    @Test
    fun `hour setters persist and clamp to 0 through 23`() = runTest {
        val repository = createRepository()

        repository.setDailyReminderHour(9)
        repository.notificationSettings.test { assertThat(awaitItem().dailyReminderHour).isEqualTo(9) }
        repository.setDailyReminderHour(-1)
        repository.notificationSettings.test { assertThat(awaitItem().dailyReminderHour).isEqualTo(0) }
        repository.setDailyReminderHour(30)
        repository.notificationSettings.test { assertThat(awaitItem().dailyReminderHour).isEqualTo(23) }

        repository.setQuietHoursStartHour(6)
        repository.notificationSettings.test { assertThat(awaitItem().quietHoursStartHour).isEqualTo(6) }
        repository.setQuietHoursEndHour(30)
        repository.notificationSettings.test { assertThat(awaitItem().quietHoursEndHour).isEqualTo(23) }
    }

    @Test
    fun `setQuietHoursEnabled persists the chosen value`() = runTest {
        val repository = createRepository()
        repository.setQuietHoursEnabled(false)
        repository.notificationSettings.test { assertThat(awaitItem().quietHoursEnabled).isFalse() }
    }
}
