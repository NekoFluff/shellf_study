package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.audio.PlaybackState
import com.crazyfluff.shellfstudy.core.audio.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.core.audio.selectAudioFor
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.core.data.strokeorder.StrokeOrderRepository
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectDetailUiState(
    val isLoading: Boolean = true,
    val detail: SubjectDetail? = null,
    val relatedSubjects: Map<Long, SubjectSummary> = emptyMap(),
    val backStack: List<Long> = emptyList(),
    val showPitchAccent: Boolean = true,
    val strokeOrder: StrokeOrderUiState = StrokeOrderUiState.Unavailable
)

/** Intermediate combine result — [SubjectDetailViewModel.uiState]'s detail/related/stroke fields. */
private data class DetailAndRelated(
    val detail: SubjectDetail?,
    val related: List<SubjectSummary>,
    val strokeOrder: StrokeOrderUiState
)

/**
 * One instance manages the whole drill-down stack for a detail sheet — navigating into a related
 * subject doesn't recreate the ViewModel, it just pushes onto [SubjectDetailUiState.backStack] and
 * swaps which subject is loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubjectDetailViewModel @Inject constructor(
    private val subjectRepository: SubjectRepository,
    private val settingsRepository: SettingsRepository,
    private val audioPlayer: PronunciationAudioPlayer,
    private val strokeOrderRepository: StrokeOrderRepository
) : ViewModel() {

    private val currentSubjectId = MutableStateFlow<Long?>(null)
    private val backStack = MutableStateFlow<List<Long>>(emptyList())

    private val _uiState = MutableStateFlow(SubjectDetailUiState())
    val uiState: StateFlow<SubjectDetailUiState> = _uiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = audioPlayer.state

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
                    val relatedFlow = if (relatedIds.isEmpty()) {
                        flowOf(emptyList<SubjectSummary>())
                    } else {
                        subjectRepository.observeSubjectSummaries(relatedIds)
                    }
                    combine(relatedFlow, strokeOrderFlow(detail)) { related, strokeOrder ->
                        DetailAndRelated(detail, related, strokeOrder)
                    }
                }
                .combine(backStack) { detailAndRelated, stack -> detailAndRelated to stack }
                .combine(settingsRepository.settings) { pair, settings -> pair to settings.showPitchAccent }
                .collect { (pair, showPitchAccent) ->
                    val (detailAndRelated, stack) = pair
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detailAndRelated.detail,
                            relatedSubjects = detailAndRelated.related.associateBy { summary -> summary.subjectId },
                            backStack = stack,
                            showPitchAccent = showPitchAccent,
                            strokeOrder = detailAndRelated.strokeOrder
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

    fun playReading(reading: String) {
        val detail = _uiState.value.detail ?: return
        selectAudioFor(detail.pronunciationAudios, reading)?.let(audioPlayer::play)
    }

    fun stopPlayback() {
        audioPlayer.stop()
    }

    /** Stroke data is keyed purely by character, so only single-glyph subjects (kanji, and any
     *  radical with a real Unicode glyph) ever resolve to anything other than [StrokeOrderUiState.Unavailable]. */
    private fun strokeOrderFlow(detail: SubjectDetail?): Flow<StrokeOrderUiState> {
        val character = detail?.characters?.singleOrNull()
            ?: return flowOf(StrokeOrderUiState.Unavailable)
        return flow {
            emit(StrokeOrderUiState.Loading)
            val strokes = strokeOrderRepository.getStrokeOrder(character)
            emit(strokes?.let { StrokeOrderUiState.Available(it) } ?: StrokeOrderUiState.Unavailable)
        }
    }
}
