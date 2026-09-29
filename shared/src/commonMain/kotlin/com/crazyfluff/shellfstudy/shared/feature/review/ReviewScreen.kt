package com.crazyfluff.shellfstudy.shared.feature.review
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.LocalOpenSubjectDetail
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.koin.compose.viewmodel.koinViewModel
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizEmptyQueueContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizEmptyQueueTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizErrorContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizErrorTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizLoadingContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizQuestionContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizQuestionTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionCompleteContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionSummaryDisplay
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionCompleteTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.toQuizQuestionUiState
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.toDetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.toSessionAnswerRow
import com.crazyfluff.shellfstudy.shared.quiz.toSessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.feature.quiz.QuizScreenChromeTestTags
import com.crazyfluff.shellfstudy.shared.feature.quiz.QuizScreenScaffold
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheet
import com.crazyfluff.shellfstudy.shared.designsystem.performance.ReportQuizSessionJankState

import com.crazyfluff.shellfstudy.shared.designsystem.performance.QuizAnswerJankState


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
    const val REVEAL_BUTTON = "review_reveal_button"
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

/**
 * Reports the review screen's current state to the jank harness.
 *
 * Kept as its own composable so the mapping from [ReviewUiState] to tags reads as one piece, and so
 * [ReviewRoute] stays about wiring rather than about instrumentation.
 */
/** The review screen's phases, mapped onto the shared jank tags. */
private fun ReviewUiState.Phase.Active?.answerJankState(): QuizAnswerJankState = when {
    this == null -> QuizAnswerJankState.NotAnswering
    feedback == null -> QuizAnswerJankState.Answering
    answerRevealed -> QuizAnswerJankState.FeedbackRevealed
    else -> QuizAnswerJankState.FeedbackHidden
}

@Composable
private fun ReviewJankState(uiState: ReviewUiState) {
    val phase = uiState.phase
    val active = phase as? ReviewUiState.Phase.Active
    ReportQuizSessionJankState(
        screen = "review",
        phaseName = when (phase) {
            ReviewUiState.Phase.Loading -> "loading"
            is ReviewUiState.Phase.Error -> "error"
            ReviewUiState.Phase.NoReviewsAvailable -> "empty"
            is ReviewUiState.Phase.Active -> "active"
            is ReviewUiState.Phase.Complete -> "complete"
        },
        answerState = active.answerJankState(),
        hasRankChange = active?.rankChange != null
    )
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

    // Reported to the jank harness so a stalled frame on this screen says what it was doing. The
    // review screen produced 45 of the 94 stalls in the first real session and the two largest
    // non-sync frames, but with no tags of its own they were all just `screen=review` — which is
    // exactly the unattributable state the harness exists to end.
    //
    // These are the states a stall could plausibly belong to, in the order they occur per item:
    // answering (no feedback yet), showing feedback (grading has happened — the optimistic SRS write,
    // the outbox enqueue and the session persist are all in or just after this window), and the rank
    // change animating.
    ReviewJankState(uiState)

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

@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    actions: ReviewActions,
    onSessionComplete: () -> Unit,
    onBack: () -> Unit,
    searchUiState: SearchUiState = SearchUiState(),
    onSearchQueryChange: (String) -> Unit = {}
) {
    val activePhase = uiState.phase as? ReviewUiState.Phase.Active

    // The answer-details sheet stays mounted once the first question loads — `active` gates its
    // visibility, not its composition — so its AnchoredDraggableState/Surface pays first-mount cost
    // once per session instead of once per question. Between questions it sits on the last subject
    // shown, remembered here; a plain reference, not state, since it is only read when no question
    // is up and is updated after composition.
    val lastDetail = remember { DetailSubjectRef() }
    val detail = activePhase?.let { it.currentItem.subjectId to it.currentQuestionType } ?: lastDetail.value
    SideEffect { if (activePhase != null) lastDetail.value = detail }

    QuizScreenScaffold(
        onBack = onBack,
        canManageSession = activePhase != null,
        abandonDialogText = "Progress on items you haven't finished yet will be lost. This won't affect items you've already submitted.",
        onAbandon = actions::abandonSession,
        searchUiState = searchUiState,
        onSearchQueryChange = onSearchQueryChange,
        testTags = QuizScreenChromeTestTags(
            backButton = ReviewScreenTestTags.BACK_BUTTON,
            searchButton = ReviewScreenTestTags.SEARCH_BUTTON,
            overflowMenu = ReviewScreenTestTags.OVERFLOW_MENU,
            abandonMenuItem = ReviewScreenTestTags.ABANDON_MENU_ITEM,
            abandonConfirmButton = ReviewScreenTestTags.ABANDON_CONFIRM_BUTTON
        ),
        extraMenuItems = { closeMenu ->
            DropdownMenuItem(
                text = { Text("Wrap up") },
                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                enabled = activePhase?.isWrappingUp != true,
                onClick = { closeMenu(); actions.wrapUp() },
                modifier = Modifier.testTag(ReviewScreenTestTags.WRAP_UP_MENU_ITEM)
            )
            HorizontalDivider()
        },
        detailSheet = { isSearchActive ->
            // Only interactive once there's something to toggle — pre-answer it would invite a swipe
            // that leaks the fully revealed answer, since isAnswered is always true here.
            detail?.let { (subjectId, questionType) ->
                SubjectDetailSheet(
                    subjectId = subjectId,
                    active = !isSearchActive && activePhase?.feedback != null,
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
    ) {
        ReviewPhaseContent(uiState = uiState, actions = actions, onSessionComplete = onSessionComplete)
    }
}

/** The subject the review's details sheet last showed — see [ReviewScreen]. */
private class DetailSubjectRef(var value: Pair<Long, QuestionType>? = null)

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
        uiState = phase.toQuizQuestionUiState(
            totalCount = phase.totalCount,
            remainingCount = phase.remainingCount,
            allowUndoAfterCorrect = true,
            answerHint = phase.answerHint
        ),
        onAnswerInputChange = actions::onAnswerInputChange,
        onSubmit = actions::submitAnswer,
        onDontKnow = actions::dontKnowAnswer,
        onContinue = actions::onContinue,
        onUndo = actions::undoLastAnswer,
        onReveal = actions::revealAnswer,
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
            revealButton = ReviewScreenTestTags.REVEAL_BUTTON,
            continueButton = ReviewScreenTestTags.CONTINUE_BUTTON,
            // Review has no session context to name — its queue is the whole session.
            sessionContextLabel = ReviewScreenTestTags.SESSION_CONTEXT_LABEL
        )
    )
}

