package com.crazyfluff.shellfstudy.shared.feature.subjectdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.audio.selectAudioFor
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.model.SubjectAssignmentStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectDetail
import com.crazyfluff.shellfstudy.shared.data.model.SubjectReviewStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.data.StrokeOrderRepository
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
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

data class SubjectDetailUiState(
    val isLoading: Boolean = true,
    val detail: SubjectDetail? = null,
    val relatedSubjects: Map<Long, SubjectSummary> = emptyMap(),
    val backStack: List<Long> = emptyList(),
    val showPitchAccent: Boolean = true,
    val restrictAudioToMp3: Boolean = false,
    val showStrokeOrder: Boolean = true,
    val strokeOrder: StrokeOrderUiState = StrokeOrderUiState.Unavailable,
    /** User asked to see every section on the root subject even though the sheet's reveal mode
     *  would otherwise hide the field matching the in-progress/failed question. Reset on [open]. */
    val forceRevealAll: Boolean = false,
    /** Null if the subject hasn't been lessoned yet (no assignment exists for it). */
    val assignmentStats: SubjectAssignmentStats? = null,
    /** Null if the subject hasn't been lessoned, or has been lessoned but never reviewed yet. */
    val reviewStats: SubjectReviewStats? = null,
    /** Scroll offset (px) [SubjectDetailContent] should jump to for the current [detail]'s subject —
     *  the recorded offset when returning via [SubjectDetailViewModel.goBack], 0 otherwise. */
    val pendingScrollOffset: Int = 0
)

/** Intermediate combine result — [SubjectDetailViewModel.uiState]'s detail/related/stroke/stats fields. */
private data class DetailAndRelated(
    val detail: SubjectDetail?,
    val related: List<SubjectSummary>,
    val strokeOrder: StrokeOrderUiState,
    val assignmentStats: SubjectAssignmentStats?,
    val reviewStats: SubjectReviewStats?
)

/**
 * One instance manages the whole drill-down stack for a detail sheet — navigating into a related
 * subject doesn't recreate the ViewModel, it just pushes onto [SubjectDetailUiState.backStack] and
 * swaps which subject is loaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubjectDetailViewModel(
    private val subjectRepository: SubjectRepository,
    private val assignmentRepository: AssignmentRepository,
    private val settingsRepository: SettingsRepository,
    private val audioPlayer: PronunciationAudioPlayer,
    private val strokeOrderRepository: StrokeOrderRepository,
    private val statsRepository: StatsRepository
) : ViewModel() {

    private val currentSubjectId = MutableStateFlow<Long?>(null)
    private val backStack = MutableStateFlow<List<Long>>(emptyList())

    /** Last-recorded scroll offset (px) per subject, keyed across the whole drill-down stack so
     *  [goBack] can restore where the user left off. Never persisted beyond this ViewModel's lifetime. */
    private val scrollOffsets = mutableMapOf<Long, Int>()

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
                    val assignmentStatsFlow = detail?.let { assignmentRepository.observeAssignmentStats(it.subjectId) } ?: flowOf(null)
                    val reviewStatsFlow = detail?.let { statsRepository.observeReviewStatistic(it.subjectId) } ?: flowOf(null)
                    combine(
                        relatedFlow,
                        strokeOrderFlow(detail),
                        assignmentStatsFlow,
                        reviewStatsFlow
                    ) { related, strokeOrder, assignmentStats, reviewStats ->
                        DetailAndRelated(detail, related, strokeOrder, assignmentStats, reviewStats)
                    }
                }
                .combine(backStack) { detailAndRelated, stack -> detailAndRelated to stack }
                .combine(settingsRepository.settings) { pair, settings -> pair to settings }
                .collect { (pair, settings) ->
                    val (detailAndRelated, stack) = pair
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            detail = detailAndRelated.detail,
                            relatedSubjects = detailAndRelated.related.associateBy { summary -> summary.subjectId },
                            backStack = stack,
                            showPitchAccent = settings.showPitchAccent,
                            restrictAudioToMp3 = settings.restrictAudioToMp3,
                            showStrokeOrder = settings.showStrokeOrder,
                            strokeOrder = detailAndRelated.strokeOrder,
                            assignmentStats = detailAndRelated.assignmentStats,
                            reviewStats = detailAndRelated.reviewStats
                        )
                    }
                }
        }
    }

    /** Opens the sheet fresh at [subjectId], clearing any prior drill-down stack. */
    fun open(subjectId: Long) {
        backStack.value = emptyList()
        currentSubjectId.value = subjectId
        _uiState.update { it.copy(forceRevealAll = false, pendingScrollOffset = 0) }
    }

    /** Toggles the "show all" override for the root subject — see [SubjectDetailUiState.forceRevealAll]. */
    fun toggleForceReveal() {
        _uiState.update { it.copy(forceRevealAll = !it.forceRevealAll) }
    }

    /** Drills into a related subject, pushing the current one onto the back stack. Always starts
     *  the new subject scrolled to the top, even if it was previously visited and scrolled. */
    fun navigateToRelated(subjectId: Long) {
        val current = currentSubjectId.value ?: return
        backStack.value = backStack.value + current
        currentSubjectId.value = subjectId
        _uiState.update { it.copy(pendingScrollOffset = 0) }
    }

    /** Pops the back stack if non-empty, restoring the scroll offset [recordScrollOffset] captured
     *  for that subject. Returns false if there was nothing to pop (caller should dismiss). */
    fun goBack(): Boolean {
        val stack = backStack.value
        val previous = stack.lastOrNull() ?: return false
        backStack.value = stack.dropLast(1)
        currentSubjectId.value = previous
        _uiState.update { it.copy(pendingScrollOffset = scrollOffsets[previous] ?: 0) }
        return true
    }

    /** Records the live scroll offset (px) for [subjectId] so [goBack] can restore it later. */
    fun recordScrollOffset(subjectId: Long, offset: Int) {
        scrollOffsets[subjectId] = offset
    }

    fun playReading(reading: String) {
        val state = _uiState.value
        val detail = state.detail ?: return
        selectAudioFor(detail.pronunciationAudios, reading, mp3Only = state.restrictAudioToMp3)
            ?.let(audioPlayer::play)
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
