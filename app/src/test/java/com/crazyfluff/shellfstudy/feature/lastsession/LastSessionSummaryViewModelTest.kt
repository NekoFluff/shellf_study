package com.crazyfluff.shellfstudy.feature.lastsession

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.feature.lastsession.LastSessionSummaryViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LastSessionSummaryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: LastSessionSummaryRepository

    @Before
    fun setUp() {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        repository = LastSessionSummaryRepository(dataStore, Json { ignoreUnknownKeys = true })
    }

    @Test
    fun `emits null summary when nothing has been saved`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = LastSessionSummaryViewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.summary).isNull()
        }
    }

    @Test
    fun `emits the persisted summary once loaded`() = runTest(mainDispatcherRule.dispatcher) {
        val summary = LastSessionSummary(
            kind = LastSessionKind.REVIEW,
            itemsCount = 5,
            correctFirstTry = 4,
            totalElapsedMs = 60_000,
            averageTimePerItemMs = 12_000,
            slowestAnswers = emptyList(),
            missedItems = emptyList(),
            completedAtMillis = 1_000L
        )
        repository.save(summary)

        val viewModel = LastSessionSummaryViewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.summary).isEqualTo(summary)
        }
    }
}
