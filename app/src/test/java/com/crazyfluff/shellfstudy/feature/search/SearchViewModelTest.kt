package com.crazyfluff.shellfstudy.feature.search

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repositories = buildTestRepositories("https://api.wanikani.com/v2/", defaultDispatcher = mainDispatcherRule.dispatcher)
    private val subjectDao = repositories.subjectDao

    private fun createViewModel() = SearchViewModel(repositories.subjectRepository)

    @Test
    fun `blank query returns no results even when subjects are cached`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertThat(awaitItem().results).isEmpty()
            expectNoEvents()
        }
    }

    @Test
    fun `query text updates immediately, before the debounced results arrive`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // initial state

            viewModel.onQueryChange("wat")
            val afterQueryChange = awaitItem()
            assertThat(afterQueryChange.query).isEqualTo("wat")
            assertThat(afterQueryChange.results).isEmpty() // debounced results haven't landed yet

            val afterResults = awaitItem()
            assertThat(afterResults.results).hasSize(1)
        }
    }

    @Test
    fun `query matches by meaning`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // initial state
            viewModel.onQueryChange("wat")
            awaitItem() // query text updates first, with stale (empty) results

            val state = awaitItem()
            assertThat(state.results).hasSize(1)
            assertThat(state.results.first().characters).isEqualTo("水")
        }
    }

    @Test
    fun `query matches by character`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("水")
            awaitItem()

            assertThat(awaitItem().results).hasSize(1)
        }
    }

    @Test
    fun `query matches by reading`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("みず")
            awaitItem()

            assertThat(awaitItem().results).hasSize(1)
        }
    }

    @Test
    fun `query with no matches returns empty results`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("fire")
            awaitItem()
            expectNoEvents()
        }
    }

    @Test
    fun `results are capped at 50 with the true match count reported separately`() = runTest(mainDispatcherRule.dispatcher) {
        repeat(60) { index -> seedSubject(id = index.toLong(), characters = "水$index", meaning = "Water", reading = "みず") }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("water")
            awaitItem()

            val state = awaitItem()
            assertThat(state.results).hasSize(50)
            assertThat(state.totalMatchCount).isEqualTo(60)
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
                    documentUrl = null,
                    searchTarget = "$characters $meaning $reading".lowercase()
                )
            )
        )
    }
}
