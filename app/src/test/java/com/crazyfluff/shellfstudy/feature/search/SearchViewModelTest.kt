package com.crazyfluff.shellfstudy.feature.search

import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.shared.data.model.SearchFilters
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.feature.search.SearchTab
import com.crazyfluff.shellfstudy.shared.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.network.ContextSentenceData
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repositories = buildTestRepositories("https://api.wanikani.com/v2/", defaultDispatcher = mainDispatcherRule.dispatcher)
    private val subjectDao = repositories.subjectDao

    private fun createViewModel() = SearchViewModel(repositories.subjectRepository)

    private fun TestScope.settled(viewModel: SearchViewModel): SearchUiState {
        advanceUntilIdle()
        return viewModel.uiState.value
    }

    @Test
    fun `blank query returns no results even when subjects are cached`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        val state = settled(viewModel)
        assertThat(state.results).isEmpty()
        assertThat(state.isSearchingSubjects).isFalse()
    }

    @Test
    fun `typing doesn't flag a search until the debounced query runs, which clears it`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            awaitItem() // initial state

            viewModel.onQueryChange("wat")
            val typed = awaitItem()
            assertThat(typed.query).isEqualTo("wat")
            assertThat(typed.isSearchingSubjects).isFalse() // still inside the debounce

            assertThat(awaitItem().isSearchingSubjects).isTrue()
            val done = awaitItem()
            assertThat(done.isSearchingSubjects).isFalse()
            assertThat(done.results).hasSize(1)
        }
    }

    @Test
    fun `query matches by meaning, character and reading`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        for (query in listOf("wat", "水", "みず")) {
            viewModel.onQueryChange(query)
            assertThat(settled(viewModel).results.map { it.characters }).containsExactly("水")
        }
    }

    @Test
    fun `query with no matches returns empty results`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "みず")
        val viewModel = createViewModel()

        viewModel.onQueryChange("fire")
        val state = settled(viewModel)
        assertThat(state.results).isEmpty()
        assertThat(state.isSearchingSubjects).isFalse()
    }

    @Test
    fun `the first page holds 50 results, with the true total`() = runTest(mainDispatcherRule.dispatcher) {
        seedWaters(120)
        val viewModel = createViewModel()

        viewModel.onQueryChange("water")
        val state = settled(viewModel)
        assertThat(state.results).hasSize(50)
        assertThat(state.totalMatchCount).isEqualTo(120)
    }

    @Test
    fun `loading more adds a page at a time, up to the total, without flagging a new search`() = runTest(mainDispatcherRule.dispatcher) {
        seedWaters(120)
        val viewModel = createViewModel()
        viewModel.onQueryChange("water")
        settled(viewModel)

        viewModel.onLoadMore(SearchTab.SUBJECTS)
        assertThat(viewModel.uiState.value.isSearchingSubjects).isFalse()
        assertThat(settled(viewModel).results).hasSize(100)

        viewModel.onLoadMore(SearchTab.SUBJECTS)
        assertThat(settled(viewModel).results).hasSize(120)

        viewModel.onLoadMore(SearchTab.SUBJECTS) // nothing left
        assertThat(settled(viewModel).results).hasSize(120)
    }

    @Test
    fun `a new query starts again at one page`() = runTest(mainDispatcherRule.dispatcher) {
        seedWaters(120)
        val viewModel = createViewModel()
        viewModel.onQueryChange("water")
        settled(viewModel)
        viewModel.onLoadMore(SearchTab.SUBJECTS)
        assertThat(settled(viewModel).results).hasSize(100)

        viewModel.onQueryChange("wate")
        assertThat(settled(viewModel).results).hasSize(50)
    }

    @Test
    fun `sentences load more too`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(
            id = 1, characters = "水", meaning = "Water", reading = "みず", type = "vocabulary",
            sentences = List(70) { ContextSentenceData(en = "Water $it.", ja = "水。") }
        )
        val viewModel = createViewModel()
        viewModel.onQueryChange("water")
        viewModel.onTabChange(SearchTab.SENTENCES)
        val first = settled(viewModel)
        assertThat(first.sentenceResults).hasSize(50)
        assertThat(first.sentenceTotalCount).isEqualTo(70)

        viewModel.onLoadMore(SearchTab.SENTENCES)
        assertThat(settled(viewModel).sentenceResults).hasSize(70)
    }

    @Test
    fun `type filter narrows results, and the vocabulary chip covers kana-only vocabulary`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(id = 1, characters = "水", meaning = "Water", reading = "すい", type = "kanji")
        seedSubject(id = 2, characters = "水", meaning = "Water", reading = "みず", type = "vocabulary")
        seedSubject(id = 3, characters = "ウォーター", meaning = "Water", reading = "うぉーたー", type = "kana_vocabulary")
        val viewModel = createViewModel()

        viewModel.onQueryChange("water")
        assertThat(settled(viewModel).results.map { it.subjectId }).containsExactly(1L, 2L, 3L)

        viewModel.onFiltersChange(SearchFilters(types = setOf(SubjectType.VOCABULARY)))
        assertThat(settled(viewModel).results.map { it.subjectId }).containsExactly(2L, 3L)

        viewModel.onFiltersChange(SearchFilters(types = setOf(SubjectType.KANJI)))
        assertThat(settled(viewModel).results.map { it.subjectId }).containsExactly(1L)
    }

    @Test
    fun `unlocked hides subjects and sentences without a visible assignment`() = runTest(mainDispatcherRule.dispatcher) {
        seedSubject(
            id = 1, characters = "水", meaning = "Water", reading = "みず",
            sentences = listOf(ContextSentenceData(en = "Water.", ja = "水。"))
        )
        seedSubject(
            id = 2, characters = "氷", meaning = "Ice water", reading = "こおり",
            sentences = listOf(ContextSentenceData(en = "Ice water.", ja = "氷水。"))
        )
        repositories.assignmentDao.upsertAll(
            listOf(
                AssignmentEntity(
                    id = 10, subjectId = 1, subjectType = "vocabulary", srsStage = 1,
                    createdAt = "2026-01-01T00:00:00.000000Z", hidden = false
                )
            )
        )
        val viewModel = createViewModel()

        viewModel.onFiltersChange(SearchFilters(unlockedOnly = true))
        viewModel.onQueryChange("water")
        viewModel.onTabChange(SearchTab.SENTENCES)
        val state = settled(viewModel)

        assertThat(state.results.map { it.subjectId }).containsExactly(1L)
        assertThat(state.sentenceResults.map { it.subjectId }).containsExactly(1L)
    }

    @Test
    fun `sentences aren't searched until the Sentences tab opens`() = runTest(mainDispatcherRule.dispatcher) {
        seedVocabWithSentences()
        val viewModel = createViewModel()

        viewModel.onQueryChange("water")
        val beforeTab = settled(viewModel)
        assertThat(beforeTab.results).isNotEmpty()
        assertThat(beforeTab.sentenceTotalCount).isNull()
        assertThat(beforeTab.sentenceResults).isEmpty()

        viewModel.onTabChange(SearchTab.SENTENCES)
        val afterTab = settled(viewModel)
        assertThat(afterTab.sentenceResults.map { it.english })
            .containsExactly("I drink water.", "The water is cold.", "Fire and water.").inOrder()
        assertThat(afterTab.sentenceTotalCount).isEqualTo(3)
        assertThat(afterTab.sentenceResults.last().characters).isEqualTo("火")
        assertThat(afterTab.sentenceResults.last().level).isEqualTo(5)
        assertThat(afterTab.isSearchingSentences).isFalse()
    }

    @Test
    fun `the type chips don't narrow sentences`() = runTest(mainDispatcherRule.dispatcher) {
        seedVocabWithSentences()
        val viewModel = createViewModel()

        viewModel.onFiltersChange(SearchFilters(types = setOf(SubjectType.KANJI)))
        viewModel.onQueryChange("water")
        viewModel.onTabChange(SearchTab.SENTENCES)
        val state = settled(viewModel)

        assertThat(state.results).isEmpty()
        assertThat(state.sentenceTotalCount).isEqualTo(3)
    }

    @Test
    fun `leaving the Sentences tab keeps its results, until the query changes`() = runTest(mainDispatcherRule.dispatcher) {
        seedVocabWithSentences()
        val viewModel = createViewModel()

        viewModel.onQueryChange("water")
        viewModel.onTabChange(SearchTab.SENTENCES)
        settled(viewModel)

        viewModel.onTabChange(SearchTab.SUBJECTS)
        assertThat(settled(viewModel).sentenceTotalCount).isEqualTo(3)

        viewModel.onQueryChange("cold")
        val changed = settled(viewModel)
        assertThat(changed.sentenceTotalCount).isNull()
        assertThat(changed.sentenceResults).isEmpty()
    }

    @Test
    fun `clearing the query clears sentence results too`() = runTest(mainDispatcherRule.dispatcher) {
        seedVocabWithSentences()
        val viewModel = createViewModel()

        viewModel.onTabChange(SearchTab.SENTENCES)
        viewModel.onQueryChange("drink")
        assertThat(settled(viewModel).sentenceResults).hasSize(1)

        viewModel.onQueryChange("")
        val cleared = settled(viewModel)
        assertThat(cleared.sentenceResults).isEmpty()
        assertThat(cleared.sentenceTotalCount).isNull()
    }

    private suspend fun seedWaters(count: Int) {
        repeat(count) { index ->
            seedSubject(id = index.toLong(), characters = "水$index", meaning = "Water", reading = "みず")
        }
    }

    private suspend fun seedVocabWithSentences() {
        seedSubject(
            id = 1, characters = "水", meaning = "Water", reading = "みず", type = "vocabulary",
            sentences = listOf(
                ContextSentenceData(en = "I drink water.", ja = "水を飲みます。"),
                ContextSentenceData(en = "The water is cold.", ja = "水は冷たい。")
            )
        )
        seedSubject(
            id = 2, characters = "火", meaning = "Fire", reading = "ひ", type = "vocabulary", level = 5,
            sentences = listOf(ContextSentenceData(en = "Fire and water.", ja = "火と水。"))
        )
    }

    private suspend fun seedSubject(
        id: Long,
        characters: String,
        meaning: String,
        reading: String,
        type: String = "kanji",
        level: Int = 3,
        sentences: List<ContextSentenceData> = emptyList()
    ) {
        subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = id,
                    subjectType = type,
                    level = level,
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
