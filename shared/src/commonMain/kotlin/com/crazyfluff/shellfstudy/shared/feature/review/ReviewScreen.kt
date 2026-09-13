package com.crazyfluff.shellfstudy.shared.feature.review
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.LocalOpenSubjectDetail
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.designsystem.components.CompactTopBar
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizEmptyQueueContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizEmptyQueueTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizErrorContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizErrorTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizLoadingContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizQuestionContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizQuestionTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizQuestionUiState
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionCompleteContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionSummaryDisplay
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionCompleteTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.toDetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.toSessionAnswerRow
import com.crazyfluff.shellfstudy.shared.quiz.toSessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.feature.search.SubjectSearchOverlay
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheet

object ReviewScreenTestTags {
    const val LOADING_INDICATOR = "review_loading_indicator"
    const val ERROR_TEXT = "review_error_text"
    const val RETRY_BUTTON = "review_retry_button"
    const val STUDY_OFFLINE_BUTTON = "review_study_offline_button"
    const val NO_REVIEWS_TEXT = "review_no_reviews_text"
    const val NO_REVIEWS_DONE_BUTTON = "review_no_reviews_done_button"
    const val CHARACTERS = "review_characters"
    const val PROGRESS_COUNT = "review_progress_count"
    const val QUESTION_LABEL = "review_question_label"
    const val ANSWER_FIELD = "review_answer_field"
    const val SUBMIT_BUTTON = "review_submit_button"
    const val DONT_KNOW_BUTTON = "review_dont_know_button"
    const val FEEDBACK_TEXT = "review_feedback_text"
    const val ANSWER_DETAIL_TEXT = "review_answer_detail_text"
    const val RANK_CHANGE_TEXT = "review_rank_change_text"
    const val CONTINUE_BUTTON = "review_continue_button"
    const val UNDO_BUTTON = "review_undo_button"
    const val SESSION_COMPLETE = "review_session_complete"
    const val SESSION_OVERVIEW_CARD = "review_session_overview_card"
    const val ITEMS_REVIEWED_TEXT = "review_items_reviewed_text"
    const val CORRECT_FIRST_TRY_TEXT = "review_correct_first_try_text"
    const val SESSION_TIMING_CARD = "review_session_timing_card"
    const val SESSION_TOTAL_TIME_TEXT = "review_session_total_time_text"
    const val SESSION_AVERAGE_TIME_TEXT = "review_session_average_time_text"
    const val SESSION_SLOWEST_CARD = "review_session_slowest_card"
    const val SESSION_MISSED_CARD = "review_session_missed_card"
    const val DONE_BUTTON = "review_done_button"
    const val BACK_BUTTON = "review_back_button"
    const val SEARCH_BUTTON = "review_search_button"
    const val OVERFLOW_MENU = "review_overflow_menu"
    const val WRAP_UP_MENU_ITEM = "review_wrap_up_menu_item"
    const val ABANDON_MENU_ITEM = "review_abandon_menu_item"
    const val ABANDON_CONFIRM_BUTTON = "review_abandon_confirm_button"
    const val DETAILS_TOGGLE = "review_details_toggle"
    const val TYPE_MISMATCH_TEXT = "review_type_mismatch_text"
    const val SUBJECT_TYPE_LABEL = "review_subject_type_label"
    const val TOTAL_TIMER_TEXT = "review_total_timer_text"
    const val QUESTION_TIMER_TEXT = "review_question_timer_text"
    /** Unused by Review's own UI (a review queue *is* the session), but required by the shared
     *  [com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizQuestionTestTags]. */
    const val SESSION_CONTEXT_LABEL = "review_session_context_label"
}

@Composable
fun ReviewRoute(
    onSessionComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReviewViewModel = koinViewModel(),
    searchViewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isAbandoned) {
        if (uiState.isAbandoned) onBack()
    }

    ReviewScreen(
        uiState = uiState,
        actions = viewModel,
        onSessionComplete = onSessionComplete,
        onBack = onBack,
        searchUiState = searchUiState,
        onSearchQueryChange = searchViewModel::onQueryChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    actions: ReviewActions,
    onSessionComplete: () -> Unit,
    onBack: () -> Unit,
    searchUiState: SearchUiState = SearchUiState(),
    onSearchQueryChange: (String) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    val canManageSession = uiState.phase is ReviewUiState.Phase.Active

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            CompactTopBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(ReviewScreenTestTags.BACK_BUTTON)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.testTag(ReviewScreenTestTags.SEARCH_BUTTON)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    if (canManageSession) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag(ReviewScreenTestTags.OVERFLOW_MENU)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Wrap up") },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    enabled = (uiState.phase as? ReviewUiState.Phase.Active)?.isWrappingUp != true,
                                    onClick = { menuExpanded = false; actions.wrapUp() },
                                    modifier = Modifier.testTag(ReviewScreenTestTags.WRAP_UP_MENU_ITEM)
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Abandon session", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = { menuExpanded = false; showAbandonConfirm = true },
                                    modifier = Modifier.testTag(ReviewScreenTestTags.ABANDON_MENU_ITEM)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showAbandonConfirm) {
            ConfirmationDialog(
                title = "Abandon this session?",
                text = "Progress on items you haven't finished yet will be lost. This won't affect items you've already submitted.",
                confirmLabel = "Abandon",
                onConfirm = { showAbandonConfirm = false; actions.abandonSession() },
                onDismiss = { showAbandonConfirm = false },
                confirmButtonTestTag = ReviewScreenTestTags.ABANDON_CONFIRM_BUTTON
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            ReviewPhaseContent(
                uiState = uiState,
                actions = actions,
                onSessionComplete = onSessionComplete,
            )
        }
    }

        // Only shown once there's actually something to toggle — pre-answer it'd just be a dimmed,
        // non-interactive bar taking up space and inviting a swipe that does nothing (and, since
        // isAnswered is hardcoded true below, one that would leak the fully-revealed answer to a
        // question not yet submitted). Kept mounted permanently once the first question of the
        // session loads — `active` gates visibility/interactivity, not composition — so its
        // AnchoredDraggableState/Surface (SubjectDetailSheet's shell) only ever pays first-mount cost
        // once per session instead of once per question, the same off-screen-mount treatment
        // SubjectDetailSheetHost uses. The subjectId/questionType are remembered past the point a
        // new question clears uiState.currentItem/currentQuestionType, so the now-invisible sheet
        // still has a valid (if stale) subject to sit on between questions.
        var lastDetailSubjectId by remember { mutableStateOf<Long?>(null) }
        var lastDetailQuestionType by remember { mutableStateOf<QuestionType?>(null) }
        val activePhase = uiState.phase as? ReviewUiState.Phase.Active
        activePhase?.let { lastDetailSubjectId = it.currentItem.subjectId }
        activePhase?.let { lastDetailQuestionType = it.currentQuestionType }

        lastDetailSubjectId?.let { subjectId ->
            lastDetailQuestionType?.let { questionType ->
                val active = !isSearchActive && activePhase?.feedback != null
                SubjectDetailSheet(
                    subjectId = subjectId,
                    active = active,
                    expanded = activePhase?.isDetailsExpanded == true,
                    onToggle = { actions.toggleDetails() },
                    onDismiss = { actions.closeDetails() },
                    revealMode = DetailRevealMode.HIDE_UNTIL_ANSWERED,
                    isAnswered = true,
                    questionType = questionType.toDetailQuestionType(),
                    handleTestTag = ReviewScreenTestTags.DETAILS_TOGGLE,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        SubjectSearchOverlay(
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            uiState = searchUiState,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier.fillMaxSize(),
        )

    }
}

/**
 * The one place that decides which phase renders — everything above it in [ReviewScreen] is chrome
 * (top bar, search overlay, detail sheet), shared by every phase.
 *
 * Each branch hands off to the composable that owns that phase, so no single composable declares every
 * phase's actions. The four `Quiz*`/`SessionComplete*` composables below take state + callbacks rather
 * than [actions] because they live in `designsystem/` and are shared with Lesson — they cannot depend
 * on this feature's state holder.
 */
@Composable
private fun ColumnScope.ReviewPhaseContent(
    uiState: ReviewUiState,
    actions: ReviewActions,
    onSessionComplete: () -> Unit
) {
    val openSubjectDetail = LocalOpenSubjectDetail.current
    when (val phase = uiState.phase) {
        ReviewUiState.Phase.Loading -> QuizLoadingContent(
            loadingIndicatorTestTag = ReviewScreenTestTags.LOADING_INDICATOR
        )

        is ReviewUiState.Phase.Error -> QuizErrorContent(
            message = phase.message,
            onRetry = actions::loadOrResume,
            onStudyOffline = actions::studyOffline,
            testTags = QuizErrorTestTags(
                errorText = ReviewScreenTestTags.ERROR_TEXT,
                retryButton = ReviewScreenTestTags.RETRY_BUTTON,
                studyOfflineButton = ReviewScreenTestTags.STUDY_OFFLINE_BUTTON
            )
        )

        ReviewUiState.Phase.NoReviewsAvailable -> QuizEmptyQueueContent(
            message = "No reviews available right now.",
            onDone = onSessionComplete,
            testTags = QuizEmptyQueueTestTags(
                messageText = ReviewScreenTestTags.NO_REVIEWS_TEXT,
                doneButton = ReviewScreenTestTags.NO_REVIEWS_DONE_BUTTON
            )
        )

        is ReviewUiState.Phase.Complete -> SessionCompleteContent(
            title = "Session complete!",
            subtitle = null,
            summary = SessionSummaryDisplay(
                kind = LastSessionKind.REVIEW,
                itemsCount = phase.sessionItemsReviewed,
                correctFirstTry = phase.sessionItemsCorrectFirstTry,
                totalElapsedMs = phase.sessionTotalElapsedMs,
                averageTimePerItemMs = phase.sessionAverageTimePerItemMs,
                slowestAnswers = phase.sessionSlowestAnswers.map { it.toSessionAnswerRow() },
                missedItems = phase.sessionMissedItems.map { it.toSessionMissedItemRow() }
            ),
            onDone = onSessionComplete,
            onSubjectClick = openSubjectDetail,
            testTags = SessionCompleteTestTags(
                root = ReviewScreenTestTags.SESSION_COMPLETE,
                overviewCard = ReviewScreenTestTags.SESSION_OVERVIEW_CARD,
                itemsText = ReviewScreenTestTags.ITEMS_REVIEWED_TEXT,
                correctFirstTryText = ReviewScreenTestTags.CORRECT_FIRST_TRY_TEXT,
                timingCard = ReviewScreenTestTags.SESSION_TIMING_CARD,
                totalTimeText = ReviewScreenTestTags.SESSION_TOTAL_TIME_TEXT,
                averageTimeText = ReviewScreenTestTags.SESSION_AVERAGE_TIME_TEXT,
                slowestCard = ReviewScreenTestTags.SESSION_SLOWEST_CARD,
                missedCard = ReviewScreenTestTags.SESSION_MISSED_CARD,
                doneButton = ReviewScreenTestTags.DONE_BUTTON
            )
        )

        is ReviewUiState.Phase.Active -> ReviewActivePhase(
            phase = phase,
            actions = actions
        )
    }
}

/**
 * Builds the shared [QuizQuestionContent]'s state from the active phase plus the session's display
 * settings. Lives here rather than inline in [ReviewPhaseContent] because the mapping is ~35 lines and
 * would otherwise dominate the dispatch.
 */
@Composable
private fun ColumnScope.ReviewActivePhase(
    phase: ReviewUiState.Phase.Active,
    actions: ReviewActions
) {
    QuizQuestionContent(
        uiState = QuizQuestionUiState(
            item = phase.currentItem,
            questionType = phase.currentQuestionType,
            totalCount = phase.totalCount,
            remainingCount = phase.remainingCount,
            answerInput = phase.answerInput,
            feedback = phase.feedback,
            rankChange = phase.rankChange,
            undoCounter = phase.undoCounter,
            questionSequence = phase.questionSequence,
            answerTypeMismatchCount = phase.answerTypeMismatchCount,
            questionElapsedMs = phase.timing.questionElapsedMs,
            questionActiveElapsedMs = phase.timing.questionActiveElapsedMs,
            questionActiveSegmentStartMs = phase.timing.questionActiveSegmentStartMs,
            sessionActiveElapsedMs = phase.timing.sessionActiveElapsedMs,
            sessionActiveSegmentStartMs = phase.timing.sessionActiveSegmentStartMs,
            allowUndoAfterCorrect = true,
            answerHint = phase.answerHint
        ),
        onAnswerInputChange = actions::onAnswerInputChange,
        onSubmit = actions::submitAnswer,
        onDontKnow = actions::dontKnowAnswer,
        onContinue = actions::onContinue,
        onUndo = actions::undoLastAnswer,
        testTags = QuizQuestionTestTags(
            progressCount = ReviewScreenTestTags.PROGRESS_COUNT,
            questionTimerText = ReviewScreenTestTags.QUESTION_TIMER_TEXT,
            totalTimerText = ReviewScreenTestTags.TOTAL_TIMER_TEXT,
            characters = ReviewScreenTestTags.CHARACTERS,
            subjectTypeLabel = ReviewScreenTestTags.SUBJECT_TYPE_LABEL,
            rankChangeText = ReviewScreenTestTags.RANK_CHANGE_TEXT,
            questionLabel = ReviewScreenTestTags.QUESTION_LABEL,
            answerField = ReviewScreenTestTags.ANSWER_FIELD,
            typeMismatchText = ReviewScreenTestTags.TYPE_MISMATCH_TEXT,
            dontKnowButton = ReviewScreenTestTags.DONT_KNOW_BUTTON,
            submitButton = ReviewScreenTestTags.SUBMIT_BUTTON,
            undoButton = ReviewScreenTestTags.UNDO_BUTTON,
            feedbackText = ReviewScreenTestTags.FEEDBACK_TEXT,
            answerDetailText = ReviewScreenTestTags.ANSWER_DETAIL_TEXT,
            continueButton = ReviewScreenTestTags.CONTINUE_BUTTON,
            // Review has no session context to name — its queue is the whole session.
            sessionContextLabel = ReviewScreenTestTags.SESSION_CONTEXT_LABEL
        )
    )
}

