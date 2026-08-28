package com.crazyfluff.shellfstudy.shared.feature.review
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.designsystem.components.CompactTopBar
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizQuestionContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizQuestionTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.QuizQuestionUiState
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionCompleteContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionCompleteTestTags
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionMissedItemsCard
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionOverviewCard
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionSlowestAnswersCard
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionTimingCard
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.toDetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.toSessionAnswerRow
import com.crazyfluff.shellfstudy.shared.quiz.toSessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.feature.search.SubjectSearchOverlay
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheet
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheetHost
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.rememberSubjectDetailSheetState

object ReviewScreenTestTags {
    const val LOADING_INDICATOR = "review_loading_indicator"
    const val ERROR_TEXT = "review_error_text"
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
}

sealed interface ReviewScreenEvent {
    data class AnswerInputChange(val value: String) : ReviewScreenEvent
    data object Submit : ReviewScreenEvent
    data object DontKnow : ReviewScreenEvent
    data object Continue : ReviewScreenEvent
    data object Undo : ReviewScreenEvent
    data object ToggleDetails : ReviewScreenEvent
    data object CloseDetails : ReviewScreenEvent
    data object Retry : ReviewScreenEvent
    data object WrapUp : ReviewScreenEvent
    data object Abandon : ReviewScreenEvent
    data object Done : ReviewScreenEvent
    data object Back : ReviewScreenEvent
    data class SearchQueryChange(val query: String) : ReviewScreenEvent
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
        onEvent = { event ->
            when (event) {
                is ReviewScreenEvent.AnswerInputChange -> viewModel.onAnswerInputChange(event.value)
                ReviewScreenEvent.Submit -> viewModel.submitAnswer()
                ReviewScreenEvent.DontKnow -> viewModel.dontKnowAnswer()
                ReviewScreenEvent.Continue -> viewModel.onContinue()
                ReviewScreenEvent.Undo -> viewModel.undoLastAnswer()
                ReviewScreenEvent.ToggleDetails -> viewModel.toggleDetails()
                ReviewScreenEvent.CloseDetails -> viewModel.closeDetails()
                ReviewScreenEvent.Retry -> viewModel.loadOrResume()
                ReviewScreenEvent.WrapUp -> viewModel.wrapUp()
                ReviewScreenEvent.Abandon -> viewModel.abandonSession()
                ReviewScreenEvent.Done -> onSessionComplete()
                ReviewScreenEvent.Back -> onBack()
                is ReviewScreenEvent.SearchQueryChange -> searchViewModel.onQueryChange(event.query)
            }
        },
        searchUiState = searchUiState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    onEvent: (ReviewScreenEvent) -> Unit,
    searchUiState: SearchUiState = SearchUiState()
) {
    val onAnswerInputChange: (String) -> Unit = { onEvent(ReviewScreenEvent.AnswerInputChange(it)) }
    val onSubmit = { onEvent(ReviewScreenEvent.Submit) }
    val onDontKnow = { onEvent(ReviewScreenEvent.DontKnow) }
    val onContinue = { onEvent(ReviewScreenEvent.Continue) }
    val onUndo = { onEvent(ReviewScreenEvent.Undo) }
    val onToggleDetails = { onEvent(ReviewScreenEvent.ToggleDetails) }
    val onCloseDetails = { onEvent(ReviewScreenEvent.CloseDetails) }
    val onRetry = { onEvent(ReviewScreenEvent.Retry) }
    val onWrapUp = { onEvent(ReviewScreenEvent.WrapUp) }
    val onAbandon = { onEvent(ReviewScreenEvent.Abandon) }
    val onDone = { onEvent(ReviewScreenEvent.Done) }
    val onBack = { onEvent(ReviewScreenEvent.Back) }
    val onSearchQueryChange: (String) -> Unit = { onEvent(ReviewScreenEvent.SearchQueryChange(it)) }

    var menuExpanded by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    // Distinct from the gated details toggle below — an arbitrary subject looked up mid-review via
    // search has no relationship to the current question, so it's never gated by answer state.
    val searchDetailSheetState = rememberSubjectDetailSheetState()
    val canManageSession = !uiState.isLoading && !uiState.isSessionComplete && !uiState.hasNoReviewsAvailable &&
        uiState.errorMessage == null

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
                                    enabled = !uiState.isWrappingUp,
                                    onClick = { menuExpanded = false; onWrapUp() },
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
                onConfirm = { showAbandonConfirm = false; onAbandon() },
                onDismiss = { showAbandonConfirm = false },
                confirmButtonTestTag = ReviewScreenTestTags.ABANDON_CONFIRM_BUTTON
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag(ReviewScreenTestTags.LOADING_INDICATOR))
                    }
                }

                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(ReviewScreenTestTags.ERROR_TEXT)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = onRetry) { Text("Retry") }
                    }
                }

                uiState.hasNoReviewsAvailable -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No reviews available right now.",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.testTag(ReviewScreenTestTags.NO_REVIEWS_TEXT)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier.testTag(ReviewScreenTestTags.NO_REVIEWS_DONE_BUTTON)
                        ) { Text("Back to dashboard") }
                    }
                }

                uiState.isSessionComplete -> {
                    SessionCompleteContent(
                        title = "Session complete!",
                        subtitle = null,
                        itemsLabel = "Items reviewed",
                        averageLabel = "Avg. time per item reviewed",
                        itemsCount = uiState.sessionItemsReviewed,
                        correctFirstTry = uiState.sessionItemsCorrectFirstTry,
                        totalElapsedMs = uiState.sessionTotalElapsedMs,
                        averageTimePerItemMs = uiState.sessionAverageTimePerItemMs,
                        slowestAnswers = uiState.sessionSlowestAnswers.map { it.toSessionAnswerRow() },
                        missedItems = uiState.sessionMissedItems.map { it.toSessionMissedItemRow() },
                        onDone = onDone,
                        onSubjectClick = { searchDetailSheetState.show(it) },
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
                }

                uiState.currentItem != null && uiState.currentQuestionType != null -> {
                    val item = uiState.currentItem
                    val questionType = uiState.currentQuestionType
                    QuizQuestionContent(
                        uiState = QuizQuestionUiState(
                            item = item,
                            questionType = questionType,
                            totalCount = uiState.totalCount,
                            remainingCount = uiState.remainingCount,
                            answerInput = uiState.answerInput,
                            feedback = uiState.feedback,
                            rankChange = uiState.rankChange,
                            undoCounter = uiState.undoCounter,
                            answerTypeMismatchCount = uiState.answerTypeMismatchCount,
                            showSubjectTypeLabel = uiState.showSubjectTypeLabel,
                            showQuestionTimer = uiState.showQuestionTimer,
                            showTotalTimer = uiState.showTotalTimer,
                            questionElapsedMs = uiState.questionElapsedMs,
                            questionActiveElapsedMs = uiState.questionActiveElapsedMs,
                            questionActiveSegmentStartMs = uiState.questionActiveSegmentStartMs,
                            sessionActiveElapsedMs = uiState.sessionActiveElapsedMs,
                            sessionActiveSegmentStartMs = uiState.sessionActiveSegmentStartMs,
                            useJapaneseKeyboard = uiState.useJapaneseKeyboard
                        ),
                        onAnswerInputChange = onAnswerInputChange,
                        onSubmit = onSubmit,
                        onDontKnow = onDontKnow,
                        onContinue = onContinue,
                        onUndo = onUndo,
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
                            continueButton = ReviewScreenTestTags.CONTINUE_BUTTON
                        )
                    )
                }
            }
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
        uiState.currentItem?.let { lastDetailSubjectId = it.subjectId }
        uiState.currentQuestionType?.let { lastDetailQuestionType = it }

        lastDetailSubjectId?.let { subjectId ->
            lastDetailQuestionType?.let { questionType ->
                val active = !isSearchActive && uiState.feedback != null
                SubjectDetailSheet(
                    subjectId = subjectId,
                    active = active,
                    expanded = uiState.isDetailsExpanded,
                    onToggle = onToggleDetails,
                    onDismiss = onCloseDetails,
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
            onSubjectClick = { searchDetailSheetState.show(it) }
        )

        SubjectDetailSheetHost(searchDetailSheetState)
    }
}

