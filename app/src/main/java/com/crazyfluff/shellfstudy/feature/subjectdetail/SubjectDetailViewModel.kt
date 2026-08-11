package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectDetailUiState(
    val isLoading: Boolean = true,
    val detail: SubjectDetail? = null,
    val relatedSubjects: Map<Long, SubjectSummary> = emptyMap(),
    val backStack: List<Long> = emptyList()
)

/**
 * One instance manages the whole drill-down stack for a detail sheet — navigating into a related
 * subject doesn't recreate the ViewModel, it just pushes onto [SubjectDetailUiState.backStack] and
 * swaps which subject is loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubjectDetailViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository
) : ViewModel() {

    private val currentSubjectId = MutableStateFlow<Long?>(null)
    private val backStack = MutableStateFlow<List<Long>>(emptyList())

    private val _uiState = MutableStateFlow(SubjectDetailUiState())
    val uiState: StateFlow<SubjectDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            currentSubjectId
                .flatMapLatest { id ->
                    if (id == null) {
                        flowOf(null)
                    } else {
                        subjectRepository.observeSubjectDetail(id)
                    }
                }
                .flatMapLatest { detail ->
                    val relatedIds = detail?.let {
                        it.componentSubjectIds + it.amalgamationSubjectIds + it.visuallySimilarSubjectIds
                    }.orEmpty()
                    if (relatedIds.isEmpty()) {
                        flowOf(detail to emptyList<SubjectSummary>())
                    } else {
                        combine(flowOf(detail), subjectRepository.observeSubjectSummaries(relatedIds)) { d, related -> d to related }
                    }
                }
                .combine(backStack) { (detail, related), stack -> Triple(detail, related, stack) }
                .collect { (detail, related, stack) ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            relatedSubjects = related.associateBy { summary -> summary.subjectId },
                            backStack = stack
                        )
                    }
                }
        }
    }

    /** Opens the sheet fresh at [subjectId], clearing any prior drill-down stack. */
    fun open(subjectId: Long) {
        backStack.value = emptyList()
        currentSubjectId.value = subjectId
    }

    /** Drills into a related subject, pushing the current one onto the back stack. */
    fun navigateToRelated(subjectId: Long) {
        val current = currentSubjectId.value ?: return
        backStack.value = backStack.value + current
        currentSubjectId.value = subjectId
    }

    /** Pops the back stack if non-empty. Returns false if there was nothing to pop (caller should dismiss). */
    fun goBack(): Boolean {
        val stack = backStack.value
        val previous = stack.lastOrNull() ?: return false
        backStack.value = stack.dropLast(1)
        currentSubjectId.value = previous
        return true
    }
}
