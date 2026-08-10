package com.crazyfluff.shellfstudy.feature.search

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.database.SubjectEntity
import com.crazyfluff.shellfstudy.core.network.MeaningData
import com.crazyfluff.shellfstudy.core.network.ReadingData
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.FakeSubjectDao
import com.crazyfluff.shellfstudy.fakes.buildTestApi
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val subjectDao = FakeSubjectDao()
    private val waniKaniRepository = WaniKaniRepository(
        api = buildTestApi("https://api.wanikani.com/v2/"),
        subjectDao = subjectDao,
        assignmentDao = FakeAssignmentDao()
    )

    private fun createViewModel() = SearchViewModel(waniKaniRepository)

    @Test
    fun `blank query returns no results even when subjects are cached`() = runTest {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().results).isEmpty()
        }
    }

    @Test
    fun `query matches by meaning`() = runTest {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // initial empty state
            viewModel.onQueryChange("wat")
            val state = awaitItem()
            assertThat(state.results).hasSize(1)
            assertThat(state.results.first().characters).isEqualTo("水")
        }
    }

    @Test
    fun `query matches by character`() = runTest {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("水")
            assertThat(awaitItem().results).hasSize(1)
        }
    }

    @Test
    fun `query matches by reading`() = runTest {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("みず")
            assertThat(awaitItem().results).hasSize(1)
        }
    }

    @Test
    fun `query with no matches returns empty results`() = runTest {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("fire")
            assertThat(awaitItem().results).isEmpty()
        }
    }

    private suspend fun seedSubject(id: Long, characters: String, meaning: String, reading: String) {
        subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = id,
                    subjectType = "kanji",
                    level = 3,
                    slug = characters,
                    characters = characters,
                    meanings = listOf(MeaningData(meaning = meaning, primary = true)),
                    readings = listOf(ReadingData(reading = reading, primary = true)),
                    documentUrl = null
                )
            )
        )
    }
}
