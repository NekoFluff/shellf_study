package com.crazyfluff.shellfstudy.shared.feature.lastsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The last-session screen's three mutually exclusive states. Modelled as a type rather than a
 * `isLoading` flag next to a nullable summary: "still loading" and "nothing recorded yet" are
 * different screens with different copy, and as two fields every consumer had to re-derive which
 * one applied from the pair.
 */
sealed interface LastSessionSummaryUiState {
    data object Loading : LastSessionSummaryUiState

    /** Loaded, and neither store had a summary to show. */
    data object Empty : LastSessionSummaryUiState

    data class Loaded(val summary: LastSessionSummary) : LastSessionSummaryUiState
}

/** Loads the last completed lesson/review session's persisted summary once, for a read-only
 *  "revisit" view reached from the dashboard — unlike LessonViewModel/ReviewViewModel, there's no
 *  live quiz state here, just whatever was snapshotted when that session finished. */
class LastSessionSummaryViewModel(
    private val lastSessionSummaryRepository: LastSessionSummaryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<LastSessionSummaryUiState>(LastSessionSummaryUiState.Loading)
    val uiState: StateFlow<LastSessionSummaryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Two independent stores, one per kind (see LastSessionSummaryRepository's doc comment)
            // — shows whichever was completed more recently, matching this screen's single dashboard
            // entry point ("revisit the session you just finished").
            val summary = listOfNotNull(
                lastSessionSummaryRepository.loadLesson(),
                lastSessionSummaryRepository.loadReview()
            ).maxByOrNull { it.completedAtMillis }
            _uiState.value = summary
                ?.let { LastSessionSummaryUiState.Loaded(it) }
                ?: LastSessionSummaryUiState.Empty
        }
    }
}
