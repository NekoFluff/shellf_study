package com.crazyfluff.shellfstudy.shared.feature.studytime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeReport
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeRepository
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

sealed interface StudyTimeUiState {
    data object Loading : StudyTimeUiState

    /** [selectedBarIndex] is the tapped bar in [StudyTimeReport.buckets], if any. With no recorded
     *  time at all, the screen shows its empty state from [StudyTimeReport.overview]. */
    data class Loaded(val report: StudyTimeReport, val selectedBarIndex: Int? = null) : StudyTimeUiState
}

class StudyTimeViewModel(studyTimeRepository: StudyTimeRepository) : ViewModel() {
    private val window = MutableStateFlow(StudyTimeWindow.WEEK)
    private val selectedBar = MutableStateFlow<Int?>(null)

    val uiState: StateFlow<StudyTimeUiState> = combine(
        studyTimeRepository.observeReport(window),
        selectedBar
    ) { report, selected ->
        // A selection only means something against the bars it was made on — a report for another
        // window can arrive after the tap, so an out-of-range index is dropped rather than shown.
        StudyTimeUiState.Loaded(report, selected?.takeIf { it in report.buckets.indices })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyTimeUiState.Loading)

    fun onWindowSelect(selected: StudyTimeWindow) {
        selectedBar.value = null
        window.value = selected
    }

    fun onBarSelect(index: Int?) {
        selectedBar.value = index
    }
}
