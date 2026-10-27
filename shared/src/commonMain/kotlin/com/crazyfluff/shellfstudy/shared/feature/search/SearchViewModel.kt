package com.crazyfluff.shellfstudy.shared.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.DEFAULT_SEARCH_LIMIT
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.model.SearchFilters
import com.crazyfluff.shellfstudy.shared.data.model.SearchPage
import com.crazyfluff.shellfstudy.shared.data.model.SentenceSearchResult
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val QUERY_DEBOUNCE_MILLIS = 200L

/** How many more results each [SearchViewModel.onLoadMore] asks for. */
private const val PAGE_SIZE = DEFAULT_SEARCH_LIMIT

enum class SearchTab { SUBJECTS, SENTENCES }

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val tab: SearchTab = SearchTab.SUBJECTS,
    /** The matches loaded so far, best first; more load as the list scrolls ([SearchViewModel.onLoadMore]). */
    val results: List<SubjectSummary> = emptyList(),
    /** Every match, not just those loaded. */
    val totalMatchCount: Int = 0,
    val sentenceResults: List<SentenceSearchResult> = emptyList(),
    /** Null until the Sentences tab has searched for the current query — that search only runs
     *  while its tab is open. */
    val sentenceTotalCount: Int? = null,
    val isSyncing: Boolean = false,
    /** A debounced query is running against the database. Not set while the user is still typing. */
    val isSearchingSubjects: Boolean = false,
    val isSearchingSentences: Boolean = false
)

/** The search overlay's intents, bundled so each screen hosting the overlay threads one parameter.
 *  Every field is required, so a new intent can't be left silently unwired by a host screen. */
data class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onFiltersChange: (SearchFilters) -> Unit,
    val onTabChange: (SearchTab) -> Unit,
    val onLoadMore: (SearchTab) -> Unit
) {
    companion object {
        /** For previews and screens rendered without a [SearchViewModel]. */
        val Inert = SearchActions(onQueryChange = {}, onFiltersChange = {}, onTabChange = {}, onLoadMore = {})
    }
}

/** [viewModel]'s intents as [SearchActions], kept stable across recompositions so the overlay can skip. */
@Composable
fun rememberSearchActions(viewModel: SearchViewModel): SearchActions = remember(viewModel) {
    SearchActions(
        onQueryChange = viewModel::onQueryChange,
        onFiltersChange = viewModel::onFiltersChange,
        onTabChange = viewModel::onTabChange,
        onLoadMore = viewModel::onLoadMore
    )
}

/**
 * Subjects and sentences are searched by two independent pipelines, so subject results never wait
 * for the sentence search, and sentences are only searched while their tab is open — a user looking
 * up a kanji doesn't pay for scanning every example sentence on each keystroke.
 *
 * Each tab loads a page of [PAGE_SIZE] results and asks for more as its list scrolls. The page limit
 * is tied to the input it was raised for, so a new query or filter starts again at one page, and it
 * joins the pipeline after the debounce, so loading more doesn't wait out the typing delay.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val subjectRepository: SubjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** The query and unlocked filter the current [SearchUiState.sentenceResults] were found for. */
    private var sentenceResultsInput: SentenceInput? = null

    private val subjectLimit = MutableStateFlow<PageLimit<SubjectInput>?>(null)
    private val sentenceLimit = MutableStateFlow<PageLimit<SentenceInput>?>(null)

    init {
        viewModelScope.launch { collectSubjectSearch() }
        viewModelScope.launch { collectSentenceSearch() }
    }

    private suspend fun collectSubjectSearch() {
        val debouncedInput = _uiState
            .map { it.subjectInput() }
            .distinctUntilChanged()
            .debounce(QUERY_DEBOUNCE_MILLIS)
        combine(debouncedInput, subjectLimit) { input, limit -> input to limit.forInput(input) }
            .distinctUntilChanged()
            .flatMapLatest { (input, limit) ->
                if (input.query.isEmpty()) {
                    flowOf(SearchProgress.Done(SubjectResults()))
                } else {
                    searching(
                        combine(
                            subjectRepository.observeSearch(input.query, input.filters, limit),
                            subjectRepository.observeIsSyncingSubjectLibrary()
                        ) { page, isSyncing -> SubjectResults(page, isSyncing) },
                        // Loading another page shows its own row at the end of the list instead.
                        isLoadingMore = limit > PAGE_SIZE
                    )
                }
            }
            .collect { progress ->
                _uiState.update { state ->
                    when (progress) {
                        SearchProgress.Searching -> state.copy(isSearchingSubjects = true)
                        is SearchProgress.Done -> state.copy(
                            results = progress.value.page.items,
                            totalMatchCount = progress.value.page.totalCount,
                            isSyncing = progress.value.isSyncing,
                            isSearchingSubjects = false
                        )
                    }
                }
            }
    }

    private suspend fun collectSentenceSearch() {
        val debouncedInput = _uiState
            .map { it.sentenceInput() }
            .distinctUntilChanged()
            .debounce(QUERY_DEBOUNCE_MILLIS)
        // The tab joins after the debounce, so opening the Sentences tab searches straight away.
        val tabOpen = _uiState.map { it.tab == SearchTab.SENTENCES }.distinctUntilChanged()
        combine(debouncedInput, tabOpen, sentenceLimit) { input, isTabOpen, limit ->
            Triple(input, isTabOpen, limit.forInput(input))
        }
            .distinctUntilChanged()
            .flatMapLatest { (input, isTabOpen, limit) ->
                when {
                    input.query.isEmpty() -> flowOf(SearchProgress.Done(SentenceResults(input, null)))
                    isTabOpen -> searching(
                        subjectRepository.observeSentenceSearch(input.query, input.unlockedOnly, limit)
                            .map { SentenceResults(input, it) },
                        isLoadingMore = limit > PAGE_SIZE
                    )
                    // Off-tab, results for an older query are dropped rather than shown as current;
                    // results for this query are kept, so switching back shows them at once.
                    input != sentenceResultsInput -> flowOf(SearchProgress.Done(SentenceResults(input, null)))
                    else -> emptyFlow()
                }
            }
            .collect { progress ->
                when (progress) {
                    SearchProgress.Searching -> _uiState.update { it.copy(isSearchingSentences = true) }
                    is SearchProgress.Done -> {
                        val sentences = progress.value.sentences
                        sentenceResultsInput = progress.value.input.takeIf { sentences != null }
                        _uiState.update {
                            it.copy(
                                sentenceResults = sentences?.items.orEmpty(),
                                sentenceTotalCount = sentences?.totalCount,
                                isSearchingSentences = false
                            )
                        }
                    }
                }
            }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun onFiltersChange(filters: SearchFilters) {
        _uiState.update { it.copy(filters = filters) }
    }

    fun onTabChange(tab: SearchTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    /** Loads another page of [tab]'s results, if it has more and its last page has arrived. */
    fun onLoadMore(tab: SearchTab) {
        val state = _uiState.value
        when (tab) {
            SearchTab.SUBJECTS -> subjectLimit.update {
                it.nextPage(state.subjectInput(), loaded = state.results.size, total = state.totalMatchCount)
            }
            SearchTab.SENTENCES -> sentenceLimit.update {
                it.nextPage(
                    state.sentenceInput(),
                    loaded = state.sentenceResults.size,
                    total = state.sentenceTotalCount ?: 0
                )
            }
        }
    }

    /** One more page for [input] once its current page has fully arrived and more remain; otherwise
     *  unchanged, so a scroll that fires twice before the page lands doesn't skip one. */
    private fun <I> PageLimit<I>?.nextPage(input: I, loaded: Int, total: Int): PageLimit<I>? {
        val limit = forInput(input)
        return if (loaded >= limit && total > limit) PageLimit(input, limit + PAGE_SIZE) else this
    }

    /** [results] preceded by [SearchProgress.Searching] — unless [isLoadingMore] — so the UI knows a
     *  query is running. */
    private fun <T> searching(results: Flow<T>, isLoadingMore: Boolean = false): Flow<SearchProgress<T>> = flow {
        if (!isLoadingMore) emit(SearchProgress.Searching)
        emitAll(results.map { SearchProgress.Done(it) })
    }

    private fun SearchUiState.subjectInput() = SubjectInput(query.trim(), filters)

    private fun SearchUiState.sentenceInput() = SentenceInput(query.trim(), filters.unlockedOnly)

    /** The page limit for [input] — one page unless more were loaded for this very input. */
    private fun <I> PageLimit<I>?.forInput(input: I): Int = if (this?.input == input) limit else PAGE_SIZE

    private sealed interface SearchProgress<out T> {
        data object Searching : SearchProgress<Nothing>
        data class Done<T>(val value: T) : SearchProgress<T>
    }

    private data class SubjectResults(
        val page: SearchPage<SubjectSummary> = SearchPage(),
        val isSyncing: Boolean = false
    )

    private data class SubjectInput(val query: String, val filters: SearchFilters)

    private data class SentenceInput(val query: String, val unlockedOnly: Boolean)

    private data class PageLimit<I>(val input: I, val limit: Int)

    /** [sentences] is null when nothing was searched for [input]. */
    private data class SentenceResults(val input: SentenceInput, val sentences: SearchPage<SentenceSearchResult>?)
}
