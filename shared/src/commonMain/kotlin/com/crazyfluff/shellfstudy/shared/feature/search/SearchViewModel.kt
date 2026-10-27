package com.crazyfluff.shellfstudy.shared.feature.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.model.SearchFilters
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

private const val RESULT_CAP = 50
private const val QUERY_DEBOUNCE_MILLIS = 200L

enum class SearchTab { SUBJECTS, SENTENCES }

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilters = SearchFilters(),
    val tab: SearchTab = SearchTab.SUBJECTS,
    val results: List<SubjectSummary> = emptyList(),
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
    val onTabChange: (SearchTab) -> Unit
) {
    companion object {
        /** For previews and screens rendered without a [SearchViewModel]. */
        val Inert = SearchActions(onQueryChange = {}, onFiltersChange = {}, onTabChange = {})
    }
}

/** [viewModel]'s intents as [SearchActions], kept stable across recompositions so the overlay can skip. */
@Composable
fun rememberSearchActions(viewModel: SearchViewModel): SearchActions = remember(viewModel) {
    SearchActions(
        onQueryChange = viewModel::onQueryChange,
        onFiltersChange = viewModel::onFiltersChange,
        onTabChange = viewModel::onTabChange
    )
}

/**
 * Subjects and sentences are searched by two independent pipelines, so subject results never wait
 * for the sentence search, and sentences are only searched while their tab is open — a user looking
 * up a kanji doesn't pay for scanning every example sentence on each keystroke.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val subjectRepository: SubjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** The query and unlocked filter the current [SearchUiState.sentenceResults] were found for. */
    private var sentenceResultsInput: SentenceInput? = null

    init {
        viewModelScope.launch { collectSubjectSearch() }
        viewModelScope.launch { collectSentenceSearch() }
    }

    private suspend fun collectSubjectSearch() {
        _uiState
            .map { it.query.trim() to it.filters }
            .distinctUntilChanged()
            .debounce(QUERY_DEBOUNCE_MILLIS)
            .flatMapLatest { (query, filters) ->
                if (query.isEmpty()) {
                    flowOf(SearchProgress.Done(SubjectResults()))
                } else {
                    searching(
                        combine(
                            subjectRepository.observeSearch(query, filters),
                            subjectRepository.observeIsSyncingSubjectLibrary()
                        ) { matches, isSyncing -> SubjectResults(matches, isSyncing) }
                    )
                }
            }
            .collect { progress ->
                _uiState.update { state ->
                    when (progress) {
                        SearchProgress.Searching -> state.copy(isSearchingSubjects = true)
                        is SearchProgress.Done -> state.copy(
                            results = progress.value.matches.take(RESULT_CAP),
                            totalMatchCount = progress.value.matches.size,
                            isSyncing = progress.value.isSyncing,
                            isSearchingSubjects = false
                        )
                    }
                }
            }
    }

    private suspend fun collectSentenceSearch() {
        val debouncedInput = _uiState
            .map { SentenceInput(it.query.trim(), it.filters.unlockedOnly) }
            .distinctUntilChanged()
            .debounce(QUERY_DEBOUNCE_MILLIS)
        // The tab joins after the debounce, so opening the Sentences tab searches straight away.
        val tabOpen = _uiState.map { it.tab == SearchTab.SENTENCES }.distinctUntilChanged()
        combine(debouncedInput, tabOpen, ::Pair)
            .flatMapLatest { (input, isTabOpen) ->
                when {
                    input.query.isEmpty() -> flowOf(SearchProgress.Done(SentenceResults(input, null)))
                    isTabOpen -> searching(
                        subjectRepository.observeSentenceSearch(input.query, input.unlockedOnly)
                            .map { SentenceResults(input, it) }
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
                                sentenceResults = sentences.orEmpty().take(RESULT_CAP),
                                sentenceTotalCount = sentences?.size,
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

    /** [results] preceded by [SearchProgress.Searching], so the UI knows a query is running. */
    private fun <T> searching(results: Flow<T>): Flow<SearchProgress<T>> = flow {
        emit(SearchProgress.Searching)
        emitAll(results.map { SearchProgress.Done(it) })
    }

    private sealed interface SearchProgress<out T> {
        data object Searching : SearchProgress<Nothing>
        data class Done<T>(val value: T) : SearchProgress<T>
    }

    private data class SubjectResults(val matches: List<SubjectSummary> = emptyList(), val isSyncing: Boolean = false)

    private data class SentenceInput(val query: String, val unlockedOnly: Boolean)

    /** [sentences] is null when nothing was searched for [input]. */
    private data class SentenceResults(val input: SentenceInput, val sentences: List<SentenceSearchResult>?)
}
