package com.crazyfluff.shellfstudy.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<SubjectSummary> = emptyList()
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    waniKaniRepository: WaniKaniRepository
) : ViewModel() {

    private val query = MutableStateFlow("")
    val uiState: StateFlow<SearchUiState> = combine(
        query,
        waniKaniRepository.observeCachedSubjects()
    ) { currentQuery, subjects ->
        val trimmed = currentQuery.trim()
        val results = if (trimmed.isEmpty()) {
            emptyList()
        } else {
            subjects.filter { it.matches(trimmed) }
        }
        SearchUiState(query = currentQuery, results = results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }
}

private fun SubjectSummary.matches(query: String): Boolean =
    characters?.contains(query, ignoreCase = true) == true ||
        meanings.any { it.contains(query, ignoreCase = true) } ||
        readings.any { it.contains(query, ignoreCase = true) }
