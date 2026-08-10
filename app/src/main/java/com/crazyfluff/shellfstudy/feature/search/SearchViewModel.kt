package com.crazyfluff.shellfstudy.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val RESULT_CAP = 50
private const val QUERY_DEBOUNCE_MILLIS = 200L

data class SearchUiState(
    val query: String = "",
    val results: List<SubjectSummary> = emptyList(),
    val totalMatchCount: Int = 0,
    val isSyncing: Boolean = false
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            query
                .debounce(QUERY_DEBOUNCE_MILLIS)
                .flatMapLatest { currentQuery ->
                    val trimmed = currentQuery.trim()
                    if (trimmed.isEmpty()) {
                        flowOf(SearchResults())
                    } else {
                        combine(
                            subjectRepository.observeSearch(trimmed),
                            subjectRepository.observeIsSyncingSubjectLibrary()
                        ) { matches, isSyncing ->
                            SearchResults(results = matches.take(RESULT_CAP), totalMatchCount = matches.size, isSyncing = isSyncing)
                        }
                    }
                }
                .collect { searchResults ->
                    _uiState.update {
                        it.copy(
                            results = searchResults.results,
                            totalMatchCount = searchResults.totalMatchCount,
                            isSyncing = searchResults.isSyncing
                        )
                    }
                }
        }
    }

    // Query text updates immediately so the field never appears to swallow keystrokes; only the
    // (debounced) search results above lag behind while the user is still typing.
    fun onQueryChange(value: String) {
        query.value = value
        _uiState.update { it.copy(query = value) }
    }

    private data class SearchResults(
        val results: List<SubjectSummary> = emptyList(),
        val totalMatchCount: Int = 0,
        val isSyncing: Boolean = false
    )
}
