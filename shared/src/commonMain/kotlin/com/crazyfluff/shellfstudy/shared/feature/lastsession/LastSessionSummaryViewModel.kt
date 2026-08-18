package com.crazyfluff.shellfstudy.shared.feature.lastsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LastSessionSummaryUiState(
    val isLoading: Boolean = true,
    val summary: LastSessionSummary? = null
)

/** Loads the last completed lesson/review session's persisted summary once, for a read-only
 *  "revisit" view reached from the dashboard — unlike LessonViewModel/ReviewViewModel, there's no
 *  live quiz state here, just whatever was snapshotted when that session finished. */
class LastSessionSummaryViewModel(
    private val lastSessionSummaryRepository: LastSessionSummaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LastSessionSummaryUiState())
    val uiState: StateFlow<LastSessionSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val summary = lastSessionSummaryRepository.load()
            _uiState.update { it.copy(isLoading = false, summary = summary) }
        }
    }
}
