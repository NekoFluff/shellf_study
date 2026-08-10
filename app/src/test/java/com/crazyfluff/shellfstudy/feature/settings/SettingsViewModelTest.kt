package com.crazyfluff.shellfstudy.feature.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createViewModel(): SettingsViewModel {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return SettingsViewModel(SettingsRepository(dataStore))
    }

    @Test
    fun `onDailyLessonGoalChange updates the state`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().dailyLessonGoal).isEqualTo(15)

            viewModel.onDailyLessonGoalChange(20)
            assertThat(awaitItem().dailyLessonGoal).isEqualTo(20)
        }
    }

    @Test
    fun `onThemeModeChange updates the state`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.SYSTEM)

            viewModel.onThemeModeChange(ThemeMode.DARK)
            assertThat(awaitItem().themeMode).isEqualTo(ThemeMode.DARK)
        }
    }
}
