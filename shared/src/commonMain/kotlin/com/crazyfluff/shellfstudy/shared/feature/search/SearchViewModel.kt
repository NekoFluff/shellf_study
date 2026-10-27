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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
    val sentenceTotalCount: Int = 0,
    val isSyncing: Boolean = false
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

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val subjectRepository: SubjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState
                .map { it.query to it.filters }
                .distinctUntilChanged()
                .debounce(QUERY_DEBOUNCE_MILLIS)
                .flatMapLatest { (currentQuery, filters) ->
                    val trimmed = currentQuery.trim()
                    if (trimmed.isEmpty()) {
                        flowOf(SearchResults())
                    } else {
                        // Both tabs search together, so each tab's label can show its count.
                        combine(
                            subjectRepository.observeSearch(trimmed, filters),
                            subjectRepository.observeSentenceSearch(trimmed, filters),
                            subjectRepository.observeIsSyncingSubjectLibrary()
                        ) { matches, sentences, isSyncing ->
                            SearchResults(
                                results = matches.take(RESULT_CAP),
                                totalMatchCount = matches.size,
                                sentenceResults = sentences.take(RESULT_CAP),
                                sentenceTotalCount = sentences.size,
                                isSyncing = isSyncing
                            )
                        }
                    }
                }
                .collect { searchResults ->
                    _uiState.update {
                        it.copy(
                            results = searchResults.results,
                            totalMatchCount = searchResults.totalMatchCount,
                            sentenceResults = searchResults.sentenceResults,
                            sentenceTotalCount = searchResults.sentenceTotalCount,
                            isSyncing = searchResults.isSyncing
                        )
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

    private data class SearchResults(
        val results: List<SubjectSummary> = emptyList(),
        val totalMatchCount: Int = 0,
        val sentenceResults: List<SentenceSearchResult> = emptyList(),
        val sentenceTotalCount: Int = 0,
        val isSyncing: Boolean = false
    )
}
