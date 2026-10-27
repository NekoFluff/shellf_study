package com.crazyfluff.shellfstudy.feature.search

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.model.SearchFilters
import com.crazyfluff.shellfstudy.shared.feature.search.SearchTab
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.network.ContextSentenceData
import com.crazyfluff.shellfstudy.shared.network.SubjectType
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

    @Test
    fun `type filter narrows results, and the vocabulary chip covers kana-only vocabulary`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "すい", type = "kanji")
        seedSubject(id = 2, characters = "水", meaning = "Water", reading = "みず", type = "vocabulary")
        seedSubject(id = 3, characters = "ウォーター", meaning = "Water", reading = "うぉーたー", type = "kana_vocabulary")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("water")
            awaitItem()
            assertThat(awaitItem().results.map { it.subjectId }).containsExactly(1L, 2L, 3L)

            viewModel.onFiltersChange(SearchFilters(types = setOf(SubjectType.VOCABULARY)))
            awaitItem() // filters update first, with the previous results
            assertThat(awaitItem().results.map { it.subjectId }).containsExactly(2L, 3L)

            viewModel.onFiltersChange(SearchFilters(types = setOf(SubjectType.KANJI)))
            awaitItem()
            assertThat(awaitItem().results.map { it.subjectId }).containsExactly(1L)
        }
    }

    @Test
    fun `unlocked only hides subjects without a visible assignment`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        seedSubject(id = 2, characters = "氷", meaning = "Ice water", reading = "こおり")
        repositories.assignmentDao.upsertAll(
            listOf(
                AssignmentEntity(
                    id = 10, subjectId = 1, subjectType = "kanji", srsStage = 1,
                    createdAt = "2026-01-01T00:00:00.000000Z", hidden = false
                )
            )
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onFiltersChange(SearchFilters(unlockedOnly = true))
            awaitItem()
            viewModel.onQueryChange("water")
            awaitItem()

            assertThat(awaitItem().results.map { it.subjectId }).containsExactly(1L)
        }
    }

    @Test
    fun `sentences are searched alongside subjects, with their own count`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(
            id = 1, characters = "水", meaning = "Water", reading = "みず", type = "vocabulary",
            sentences = listOf(
                ContextSentenceData(en = "I drink water.", ja = "水を飲みます。"),
                ContextSentenceData(en = "The water is cold.", ja = "水は冷たい。")
            )
        )
        seedSubject(
            id = 2, characters = "火", meaning = "Fire", reading = "ひ", type = "vocabulary",
            sentences = listOf(ContextSentenceData(en = "Fire and water.", ja = "火と水。"))
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("water")
            awaitItem()

            val state = awaitItem()
            assertThat(state.results.map { it.subjectId }).containsExactly(1L)
            assertThat(state.sentenceResults.map { it.english })
                .containsExactly("I drink water.", "The water is cold.", "Fire and water.")
            assertThat(state.sentenceTotalCount).isEqualTo(3)
            assertThat(state.sentenceResults.last().characters).isEqualTo("火")
        }
    }

    @Test
    fun `clearing the query clears sentence results too`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(
            id = 1, characters = "水", meaning = "Water", reading = "みず", type = "vocabulary",
            sentences = listOf(ContextSentenceData(en = "I drink water.", ja = "水を飲みます。"))
        )
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("drink")
            awaitItem()
            assertThat(awaitItem().sentenceResults).hasSize(1)

            viewModel.onQueryChange("")
            awaitItem()
            val cleared = awaitItem()
            assertThat(cleared.sentenceResults).isEmpty()
            assertThat(cleared.sentenceTotalCount).isEqualTo(0)
        }
    }

    @Test
    fun `switching tabs keeps the query and results`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem()
            viewModel.onQueryChange("water")
            awaitItem()
            awaitItem()

            viewModel.onTabChange(SearchTab.SENTENCES)
            val state = awaitItem()
            assertThat(state.tab).isEqualTo(SearchTab.SENTENCES)
            assertThat(state.query).isEqualTo("water")
            assertThat(state.results).hasSize(1)
            expectNoEvents()
        }
    }

    private suspend fun seedSubject(
        id: Long,
        characters: String,
        meaning: String,
        reading: String,
        type: String = "kanji",
        sentences: List<ContextSentenceData> = emptyList()
    ) {
        subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = id,
                    subjectType = type,
                    level = 3,
                    slug = characters,
                    characters = characters,
                    meanings = listOf(MeaningData(meaning = meaning, primary = true)),
                    readings = listOf(ReadingData(reading = reading, primary = true)),
                    documentUrl = null,
                    contextSentences = sentences,
                    searchTarget = "$characters $meaning $reading".lowercase()
                )
            )
        )
    }
}
