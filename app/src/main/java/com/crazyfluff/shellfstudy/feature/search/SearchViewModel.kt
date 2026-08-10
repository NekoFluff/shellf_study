package com.crazyfluff.shellfstudy.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
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
    val uiState: StateFlow<SearchUiState> = query
        .debounce(QUERY_DEBOUNCE_MILLIS)
        .flatMapLatest { currentQuery ->
            val trimmed = currentQuery.trim()
            if (trimmed.isEmpty()) {
                flowOf(SearchUiState(query = currentQuery))
            } else {
                combine(
                    subjectRepository.observeSearch(trimmed),
                    subjectRepository.observeIsSyncingSubjectLibrary()
                ) { matches, isSyncing ->
                    SearchUiState(
                        query = currentQuery,
                        results = matches.take(RESULT_CAP),
                        totalMatchCount = matches.size,
                        isSyncing = isSyncing
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }
}
