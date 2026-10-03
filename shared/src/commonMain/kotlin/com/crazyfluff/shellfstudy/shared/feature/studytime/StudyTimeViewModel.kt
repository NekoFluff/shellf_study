package com.crazyfluff.shellfstudy.shared.feature.studytime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeReport
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeRepository
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface StudyTimeUiState {
    data object Loading : StudyTimeUiState

    /** [selectedBarIndex] is the tapped bar in [StudyTimeReport.buckets], if any. With no recorded
     *  time at all, the screen shows its empty state from [StudyTimeReport.overview].
     *  [studyStreakDays] is the dashboard's streak — days with a review or lesson — so the flame means
     *  the same number on both screens. */
    data class Loaded(
        val report: StudyTimeReport,
        val selectedBarIndex: Int? = null,
        val studyStreakDays: Int = 0
    ) : StudyTimeUiState
}

class StudyTimeViewModel(
    studyTimeRepository: StudyTimeRepository,
    statsRepository: StatsRepository
) : ViewModel() {
    private val window = MutableStateFlow(StudyTimeWindow.WEEK)
    private val selectedBar = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<StudyTimeUiState> = combine(
        studyTimeRepository.observeReport(window),
        selectedBar,
        statsRepository.observeStudyStreak().map { it.currentStreakDays }.distinctUntilChanged()
    ) { report, selected, streakDays ->
        // A selection only means something against the bars it was made on — a report for another
        // window can arrive after the tap, so an out-of-range index is dropped rather than shown.
        StudyTimeUiState.Loaded(report, selected?.takeIf { it in report.buckets.indices }, streakDays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyTimeUiState.Loading)

    fun onWindowSelect(selected: StudyTimeWindow) {
        selectedBar.value = null
        window.value = selected
    }

    fun onBarSelect(index: Int?) {
        selectedBar.value = index
    }
}
