package com.crazyfluff.shellfstudy.feature.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Undo
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tracing.Trace
import com.crazyfluff.shellfstudy.core.data.model.ReviewItem
import com.crazyfluff.shellfstudy.core.designsystem.components.CompactTopBar
import com.crazyfluff.shellfstudy.core.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.core.designsystem.quiz.ElapsedTimeText
import com.crazyfluff.shellfstudy.core.designsystem.quiz.GatedContinueButton
import com.crazyfluff.shellfstudy.core.designsystem.quiz.PausableElapsedTimeText
import com.crazyfluff.shellfstudy.core.designsystem.quiz.QuizAnswerField
import com.crazyfluff.shellfstudy.core.designsystem.quiz.formatElapsedClock
import com.crazyfluff.shellfstudy.core.designsystem.quiz.SessionAnswerRow
import com.crazyfluff.shellfstudy.core.designsystem.quiz.SessionMissedItemRow
import com.crazyfluff.shellfstudy.core.designsystem.quiz.SessionMissedItemsCard
import com.crazyfluff.shellfstudy.core.designsystem.quiz.SessionOverviewCard
import com.crazyfluff.shellfstudy.core.designsystem.quiz.SessionSlowestAnswersCard
import com.crazyfluff.shellfstudy.core.designsystem.quiz.SessionTimingCard
import com.crazyfluff.shellfstudy.core.designsystem.quiz.feedbackDetailPrefix
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.SubjectGlyph
import com.crazyfluff.shellfstudy.core.designsystem.theme.EinkStageColors
import com.crazyfluff.shellfstudy.core.data.model.RankChange
import com.crazyfluff.shellfstudy.core.data.model.SrsStage
import com.crazyfluff.shellfstudy.core.designsystem.theme.RankChangeChip
import com.crazyfluff.shellfstudy.core.designsystem.theme.RankChangeChipEnterDurationMs
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.core.designsystem.theme.themeAwareColor
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.core.quiz.QuestionType
import com.crazyfluff.shellfstudy.core.quiz.label
import com.crazyfluff.shellfstudy.core.util.formatAnswerList
import com.crazyfluff.shellfstudy.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.feature.search.SubjectSearchOverlay
import com.crazyfluff.shellfstudy.feature.subjectdetail.SubjectDetailHandleHeight
import com.crazyfluff.shellfstudy.feature.subjectdetail.SubjectDetailSheet
import com.crazyfluff.shellfstudy.feature.subjectdetail.SubjectDetailSheetHost
import com.crazyfluff.shellfstudy.feature.subjectdetail.rememberSubjectDetailSheetState

/** Arbitrary non-null value used only to warm up [RankChangeChip]'s first composition ahead of
 *  time — see its call site in [ReviewQuestionContent]. Never actually shown. */
private val RankChangeChipWarmupValue = RankChange(from = SrsStage.APPRENTICE_1, to = SrsStage.APPRENTICE_2)

object ReviewScreenTestTags {
    const val LOADING_INDICATOR = "review_loading_indicator"
    const val ERROR_TEXT = "review_error_text"
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
    viewModel: ReviewViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
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
    val canManageSession = !uiState.isLoading && !uiState.isSessionComplete && uiState.errorMessage == null

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

                uiState.isSessionComplete -> {
                    SessionCompleteContent(
                        uiState = uiState,
                        onDone = onDone,
                        onSubjectClick = { searchDetailSheetState.show(it) }
                    )
                }

                uiState.currentItem != null && uiState.currentQuestionType != null -> {
                    ReviewQuestionContent(
                        uiState = uiState,
                        onAnswerInputChange = onAnswerInputChange,
                        onSubmit = onSubmit,
                        onDontKnow = onDontKnow,
                        onContinue = onContinue,
                        onUndo = onUndo,
                        onToggleDetails = onToggleDetails
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
                // Instant marker (not a duration span) for a captured System Trace — labels the
                // frame where `active` first flips true, i.e. the frame right after Submit, so the
                // AnchoredDraggableState/Surface first-mount cost this gates is easy to find.
                DisposableEffect(active) {
                    if (active) {
                        Trace.beginSection("subjectDetailSheet:activeBecomesTrue")
                        Trace.endSection()
                    }
                    onDispose {}
                }
                SubjectDetailSheet(
                    subjectId = subjectId,
                    active = active,
                    expanded = uiState.isDetailsExpanded,
                    onToggle = onToggleDetails,
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

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ReviewQuestionContent(
    uiState: ReviewUiState,
    onAnswerInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onUndo: () -> Unit,
    onToggleDetails: () -> Unit
) {
    val item = uiState.currentItem ?: return
    val questionType = uiState.currentQuestionType ?: return

    val progress = if (uiState.totalCount == 0) 0f else
        (uiState.totalCount - uiState.remainingCount).toFloat() / uiState.totalCount
    val accentColor = subjectColor(item.subjectType)

    if (uiState.showTotalTimer) {
        PausableElapsedTimeText(
            baseElapsedMs = uiState.sessionActiveElapsedMs,
            segmentStartMs = uiState.sessionActiveSegmentStartMs,
            style = MaterialTheme.typography.labelMedium.copy(textAlign = TextAlign.End),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)
                .testTag(ReviewScreenTestTags.TOTAL_TIMER_TEXT)
        )
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = accentColor,
        drawStopIndicator = {}
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${uiState.totalCount - uiState.remainingCount} / ${uiState.totalCount}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ReviewScreenTestTags.PROGRESS_COUNT)
        )
        if (uiState.showQuestionTimer) {
            val questionElapsedMs = uiState.questionElapsedMs
            val questionStartTimeMs = uiState.questionStartTimeMs
            if (questionElapsedMs != null) {
                // Frozen at the instant the question was answered, matching the elapsedMs recorded
                // for the slowest-answers summary, rather than continuing to tick through feedback.
                Text(
                    text = formatElapsedClock(questionElapsedMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ReviewScreenTestTags.QUESTION_TIMER_TEXT)
                )
            } else if (questionStartTimeMs != null) {
                ElapsedTimeText(
                    startTimeMs = questionStartTimeMs,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(ReviewScreenTestTags.QUESTION_TIMER_TEXT)
                )
            }
        }
    }

    Column(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SubjectGlyph(
            characters = item.characters,
            characterImageUrl = item.characterImageUrl,
            subjectType = item.subjectType,
            size = 104.dp,
            modifier = Modifier.testTag(ReviewScreenTestTags.CHARACTERS)
        )
        if (uiState.showSubjectTypeLabel) {
            Text(
                text = subjectTypeLabel(item.subjectType),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ReviewScreenTestTags.SUBJECT_TYPE_LABEL)
            )
        }
        // Unconditionally mounted warm-up for RankChangeChip's icon/text (measured at ~30ms across
        // two frames; see ReviewSubmitJankProfilingTest). Review resets `rankChange` to null every
        // question (see ReviewViewModel.advanceToNextQuestion), so AnimatedVisibility below tears
        // down and rebuilds its content on every question that has a rank change, not just the
        // session's first — without a warm-up, that cost recurs on every such Submit. Measures its
        // children at a realistic width (unlike a zero-size warm-up, which measures Text/Icon
        // against degenerate 0px constraints and doesn't exercise the same layout path) but reports
        // zero size to this Column and clips its own draw to nothing, so it neither pushes later
        // content down nor is ever actually visible or hit-testable.
        Layout(
            modifier = Modifier.clipToBounds(),
            content = {
                RankChangeChip(RankChangeChipWarmupValue)
                RankChangeChip(RankChangeChipWarmupValue.copy(from = SrsStage.APPRENTICE_2, to = SrsStage.APPRENTICE_1))
            }
        ) { measurables, _ ->
            val childConstraints = Constraints(maxWidth = 200.dp.roundToPx())
            val placeables = measurables.map { it.measure(childConstraints) }
            layout(0, 0) { placeables.forEach { it.place(0, 0) } }
        }
        // Instant marker for a captured System Trace — labels the frame where `rankChange` first
        // turns non-null, i.e. right after Submit, so RankChangeChip's enter animation is easy to
        // find (the animation itself runs on Compose's own clock, so there's nothing to bracket).
        DisposableEffect(uiState.rankChange) {
            if (uiState.rankChange != null) {
                Trace.beginSection("rankChangeChip:becomesVisible")
                Trace.endSection()
            }
            onDispose {}
        }
        AnimatedVisibility(
            visible = uiState.rankChange != null,
            // Fade + slide up from below — no scale (a prior scale+overshoot combination visibly
            // clipped the chip's edges against AnimatedVisibility's clip-to-bounds behavior; a
            // pure fade/slide never exceeds its own laid-out bounds, so there's nothing to clip).
            // Duration shared with RankChangeChip's own internal color-morph animation via
            // RankChangeChipEnterDurationMs so the two can't drift out of sync; at a full-height
            // slide distance so the motion actually reads as an animation — a much shorter/smaller
            // version of this previously was imperceptible.
            enter = fadeIn(animationSpec = tween(durationMillis = RankChangeChipEnterDurationMs)) +
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = RankChangeChipEnterDurationMs, easing = FastOutSlowInEasing)
                ),
            // Snappier than the entrance — the chip should feel dismissed, not lingered on.
            exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                )
        ) {
            val rankChange = uiState.rankChange
            if (rankChange != null) {
                Spacer(modifier = Modifier.height(8.dp))
                RankChangeChip(rankChange, modifier = Modifier.testTag(ReviewScreenTestTags.RANK_CHANGE_TEXT))
            }
        }
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "What is the ${questionType.label}?",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(ReviewScreenTestTags.QUESTION_LABEL)
        )
        Spacer(modifier = Modifier.height(8.dp))
        val feedbackForField = uiState.feedback
        val canUndo = feedbackForField != null && !feedbackForField.isCorrect
        QuizAnswerField(
            value = uiState.answerInput,
            onValueChange = onAnswerInputChange,
            questionType = questionType,
            isAnswered = feedbackForField != null,
            answerTypeMismatchCount = uiState.answerTypeMismatchCount,
            onSubmit = onSubmit,
            answerFieldTestTag = ReviewScreenTestTags.ANSWER_FIELD,
            typeMismatchTextTestTag = ReviewScreenTestTags.TYPE_MISMATCH_TEXT,
            // Also includes undoCounter: undo clears the field and re-enables it without changing
            // item/questionType, so the field's focus-restoring effect wouldn't otherwise refire and
            // the user would be left tapped-out of the field they just asked to retry.
            focusResetKey = Triple(item.assignmentId, questionType, uiState.undoCounter),
            trailingIcon = if (feedbackForField != null) {
                {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.testTag(ReviewScreenTestTags.UNDO_BUTTON)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last answer")
                    }
                }
            } else null
        )

        Spacer(modifier = Modifier.height(16.dp))

        val feedback = uiState.feedback
        if (feedback == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDontKnow,
                    modifier = Modifier.weight(1f).testTag(ReviewScreenTestTags.DONT_KNOW_BUTTON)
                ) { Text("I don't know") }
                Button(
                    onClick = onSubmit,
                    enabled = uiState.answerInput.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag(ReviewScreenTestTags.SUBMIT_BUTTON)
                ) { Text("Submit") }
            }
        } else {
            Text(
                text = if (feedback.isCorrect) "Correct!" else "Incorrect",
                color = if (feedback.isCorrect) {
                    themeAwareColor(SrsStageColors.Enlightened, EinkStageColors.Enlightened)
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(ReviewScreenTestTags.FEEDBACK_TEXT)
            )
            feedbackDetailPrefix(feedback)?.let { prefix ->
                var isDetailExpanded by remember(feedback) { mutableStateOf(false) }
                val answers = formatAnswerList(feedback.correctAnswer, expanded = isDetailExpanded)
                Text(
                    text = "$prefix ${answers.text}",
                    color = if (answers.hasMore) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (isDetailExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .testTag(ReviewScreenTestTags.ANSWER_DETAIL_TEXT)
                        .then(
                            if (answers.hasMore) {
                                Modifier.clickable { isDetailExpanded = !isDetailExpanded }
                            } else {
                                Modifier
                            }
                        )
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            GatedContinueButton(
                feedback = feedback,
                onContinue = onContinue,
                continueButtonTestTag = ReviewScreenTestTags.CONTINUE_BUTTON,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Reserves room so the swipe-up handle — pinned to the true bottom of the screen as its
        // own overlay in ReviewScreen's outer Box, not laid out inline here — doesn't cover this
        // content. See SubjectDetailSheet for why it lives outside this Column. The extra 24dp
        // covers the handle's own navigationBarsPadding, which grows its footprint on devices with
        // a gesture pill or 3-button nav bar.
        Spacer(modifier = Modifier.height(16.dp + SubjectDetailHandleHeight + 24.dp))
    }
}

@Composable
private fun SessionCompleteContent(
    uiState: ReviewUiState,
    onDone: () -> Unit,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(ReviewScreenTestTags.SESSION_COMPLETE)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Celebration,
                contentDescription = null,
                tint = themeAwareColor(SrsStageColors.Enlightened, EinkStageColors.Enlightened)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Session complete!", style = MaterialTheme.typography.headlineMedium)
        }

        if (uiState.sessionItemsReviewed > 0) {
            Spacer(modifier = Modifier.height(24.dp))
            SessionOverviewCard(
                itemsLabel = "Items reviewed",
                itemsCount = uiState.sessionItemsReviewed,
                correctFirstTry = uiState.sessionItemsCorrectFirstTry,
                cardTestTag = ReviewScreenTestTags.SESSION_OVERVIEW_CARD,
                itemsTextTestTag = ReviewScreenTestTags.ITEMS_REVIEWED_TEXT,
                correctFirstTryTextTestTag = ReviewScreenTestTags.CORRECT_FIRST_TRY_TEXT,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            SessionTimingCard(
                totalElapsedMs = uiState.sessionTotalElapsedMs,
                averageTimePerItemMs = uiState.sessionAverageTimePerItemMs,
                averageLabel = "Avg. time per item reviewed",
                cardTestTag = ReviewScreenTestTags.SESSION_TIMING_CARD,
                totalTimeTestTag = ReviewScreenTestTags.SESSION_TOTAL_TIME_TEXT,
                averageTimeTestTag = ReviewScreenTestTags.SESSION_AVERAGE_TIME_TEXT,
                modifier = Modifier.fillMaxWidth()
            )
            if (uiState.sessionSlowestAnswers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SessionSlowestAnswersCard(
                    answers = uiState.sessionSlowestAnswers.map { it.toRow() },
                    onSubjectClick = onSubjectClick,
                    cardTestTag = ReviewScreenTestTags.SESSION_SLOWEST_CARD,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (uiState.sessionMissedItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                SessionMissedItemsCard(
                    items = uiState.sessionMissedItems.map { it.toMissedItemRow() },
                    onSubjectClick = onSubjectClick,
                    cardTestTag = ReviewScreenTestTags.SESSION_MISSED_CARD,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.testTag(ReviewScreenTestTags.DONE_BUTTON)
        ) { Text("Back to dashboard") }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun SlowAnswer.toRow(): SessionAnswerRow = SessionAnswerRow(
    label = item.characters ?: item.meanings.firstOrNull() ?: "?",
    typeLabel = type.label,
    elapsedMs = elapsedMs,
    isCorrect = isCorrect,
    subjectId = item.subjectId,
    subjectType = item.subjectType
)

private fun ReviewItem.toMissedItemRow(): SessionMissedItemRow = SessionMissedItemRow(
    label = characters ?: meanings.firstOrNull() ?: "?",
    subjectId = subjectId,
    subjectType = subjectType
)

private fun QuestionType.toDetailQuestionType(): DetailQuestionType = when (this) {
    QuestionType.MEANING -> DetailQuestionType.MEANING
    QuestionType.READING -> DetailQuestionType.READING
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ReviewScreenPreview() {
    ShellfStudyTheme {
        ReviewScreen(
            uiState = ReviewUiState(
                isLoading = false,
                totalCount = 10,
                remainingCount = 7,
                currentItem = ReviewItem(
                    assignmentId = 1,
                    subjectId = 1,
                    subjectType = SubjectType.KANJI,
                    characters = "水",
                    level = 3,
                    srsStage = 3,
                    meanings = listOf("Water"),
                    readings = listOf("みず")
                ),
                currentQuestionType = QuestionType.MEANING
            ),
            onEvent = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun ReviewScreenSessionCompletePreview() {
    val water = ReviewItem(
        assignmentId = 1, subjectId = 1, subjectType = SubjectType.KANJI, characters = "水",
        level = 3, srsStage = 3, meanings = listOf("Water"), readings = listOf("みず")
    )
    val fire = ReviewItem(
        assignmentId = 2, subjectId = 2, subjectType = SubjectType.KANJI, characters = "火",
        level = 3, srsStage = 3, meanings = listOf("Fire"), readings = listOf("ひ")
    )
    val tree = ReviewItem(
        assignmentId = 3, subjectId = 3, subjectType = SubjectType.RADICAL, characters = "木",
        level = 1, srsStage = 5, meanings = listOf("Tree"), readings = emptyList()
    )
    ShellfStudyTheme {
        ReviewScreen(
            uiState = ReviewUiState(
                isLoading = false,
                isSessionComplete = true,
                sessionItemsReviewed = 12,
                sessionItemsCorrectFirstTry = 9,
                sessionTotalElapsedMs = 245_000L,
                sessionAverageTimePerItemMs = 4_200L,
                sessionSlowestAnswers = listOf(
                    SlowAnswer(fire, QuestionType.READING, 18_400L, isCorrect = true),
                    SlowAnswer(water, QuestionType.MEANING, 12_100L, isCorrect = false),
                    SlowAnswer(tree, QuestionType.MEANING, 9_800L, isCorrect = true)
                ),
                sessionMissedItems = listOf(water, fire)
            ),
            onEvent = {}
        )
    }
}
