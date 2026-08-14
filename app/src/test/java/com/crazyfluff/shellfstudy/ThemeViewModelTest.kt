package com.crazyfluff.shellfstudy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThemeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var settingsRepository: SettingsRepository

    @Before
    fun setUp() {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
    }

    @Test
    fun `with no stored theme, defaults to system`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = ThemeViewModel(settingsRepository)

        viewModel.themeMode.test {
            assertThat(awaitItem()).isEqualTo(ThemeMode.SYSTEM)
        }
    }

    @Test
    fun `reflects a theme mode change made through the settings repository`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = ThemeViewModel(settingsRepository)

        viewModel.themeMode.test {
            assertThat(awaitItem()).isEqualTo(ThemeMode.SYSTEM)

            settingsRepository.setThemeMode(ThemeMode.DARK)

            assertThat(awaitItem()).isEqualTo(ThemeMode.DARK)
        }
    }
}
