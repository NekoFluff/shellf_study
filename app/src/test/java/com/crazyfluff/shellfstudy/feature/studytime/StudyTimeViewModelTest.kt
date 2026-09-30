package com.crazyfluff.shellfstudy.feature.studytime

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.fakes.FakeStudyTimeDao
import com.crazyfluff.shellfstudy.fakes.buildTestStudyTimeRepository
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyKind
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeWindow
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeSegmentEntity
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeUiState
import com.crazyfluff.shellfstudy.shared.feature.studytime.StudyTimeViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Clock
import kotlin.time.Instant

class StudyTimeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dao = FakeStudyTimeDao()
    private val now = Instant.parse("2026-09-24T12:00:00Z")

    private fun createViewModel(): StudyTimeViewModel {
        val scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        val clock = object : Clock {
            override fun now(): Instant = now
        }
        return StudyTimeViewModel(
            buildTestStudyTimeRepository(dao, dataStore, scope, mainDispatcherRule.dispatcher, clock = clock)
        )
    }

    private suspend fun ReceiveTurbine<StudyTimeUiState>.awaitLoaded(
        predicate: (StudyTimeUiState.Loaded) -> Boolean = { true }
    ): StudyTimeUiState.Loaded {
        var state = awaitItem()
        while (state !is StudyTimeUiState.Loaded || !predicate(state)) state = awaitItem()
        return state
    }

    private suspend fun seedToday(minutes: Long) {
        dao.insert(
            StudyTimeSegmentEntity(
                kind = StudyKind.REVIEW.name,
                startedAtMs = now.toEpochMilliseconds() - minutes * 60_000L,
                durationMs = minutes * 60_000L,
                level = 3,
                itemsAnswered = 10
            )
        )
    }

    @Test
    fun `shows an empty week when nothing is recorded`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()
        assertThat(viewModel.uiState.value).isEqualTo(StudyTimeUiState.Loading)

        viewModel.uiState.test {
            val loaded = awaitLoaded()
            assertThat(loaded.report.overview.hasAnyData).isFalse()
            assertThat(loaded.report.window).isEqualTo(StudyTimeWindow.WEEK)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `selecting a window switches the report and clears a bar selection`() = runTest(mainDispatcherRule.dispatcher) {
        seedToday(minutes = 20)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.onBarSelect(6)
            val selected = awaitLoaded { it.selectedBarIndex == 6 }
            assertThat(selected.report.buckets[6].split.reviewMs).isEqualTo(20 * 60_000L)

            viewModel.onWindowSelect(StudyTimeWindow.YEAR)
            val year = awaitLoaded { it.report.window == StudyTimeWindow.YEAR }
            assertThat(year.selectedBarIndex).isNull()
            assertThat(year.report.buckets).hasSize(52)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a newly recorded stretch shows up without reopening the screen`() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitLoaded()
            seedToday(minutes = 15)
            val updated = awaitLoaded { it.report.overview.hasAnyData }
            assertThat(updated.report.overview.today.totalMs).isEqualTo(15 * 60_000L)
            assertThat(updated.report.levels.single().level).isEqualTo(3)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
