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

    @Test
    fun `lesson and review summaries coexist, and the more recently completed one is shown`() = runTest(mainDispatcherRule.dispatcher) {
        // Bug regression: the repository used to keep both kinds in one shared slot, so a
        // completed-but-unviewed lesson summary was silently destroyed the instant a review
        // session completed (or vice versa). Each kind now persists under its own key, and the
        // screen shows whichever completed more recently.
        val lessonSummary = LastSessionSummary(
            kind = LastSessionKind.LESSON,
            itemsCount = 5,
            correctFirstTry = 5,
            totalElapsedMs = 60_000,
            averageTimePerItemMs = 12_000,
            slowestAnswers = emptyList(),
            missedItems = emptyList(),
            completedAtMillis = 1_000L
        )
        repository.save(lessonSummary)

        // A later review completion must not destroy the still-unviewed lesson summary.
        val reviewSummary = lessonSummary.copy(
            kind = LastSessionKind.REVIEW,
            itemsCount = 7,
            completedAtMillis = 2_000L
        )
        repository.save(reviewSummary)
        assertThat(repository.loadLesson()).isEqualTo(lessonSummary)
        assertThat(repository.loadReview()).isEqualTo(reviewSummary)

        val viewModel = LastSessionSummaryViewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.summary).isEqualTo(reviewSummary)
        }
    }

    @Test
    fun `shows the lesson summary when it completed more recently than the review one`() = runTest(mainDispatcherRule.dispatcher) {
        val reviewSummary = LastSessionSummary(
            kind = LastSessionKind.REVIEW,
            itemsCount = 7,
            correctFirstTry = 6,
            totalElapsedMs = 80_000,
            averageTimePerItemMs = 11_000,
            slowestAnswers = emptyList(),
            missedItems = emptyList(),
            completedAtMillis = 1_000L
        )
        repository.save(reviewSummary)
        repository.save(reviewSummary.copy(kind = LastSessionKind.LESSON, completedAtMillis = 2_000L))

        val viewModel = LastSessionSummaryViewModel(repository)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.summary?.kind).isEqualTo(LastSessionKind.LESSON)
        }
    }
}
