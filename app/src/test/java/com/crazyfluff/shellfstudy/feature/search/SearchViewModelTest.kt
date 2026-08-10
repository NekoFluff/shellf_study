package com.crazyfluff.shellfstudy.feature.search

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.database.SubjectEntity
import com.crazyfluff.shellfstudy.core.network.MeaningData
import com.crazyfluff.shellfstudy.core.network.ReadingData
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repositories = buildTestRepositories("https://api.wanikani.com/v2/")
    private val subjectDao = repositories.subjectDao

    private fun createViewModel() = SearchViewModel(repositories.subjectRepository)

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

    @Test
    fun `results are capped at 50 with the true match count reported separately`() = runTest {
        repeat(60) { index -> seedSubject(id = index.toLong(), characters = "水$index", meaning = "Water", reading = "みず") }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("water")
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
