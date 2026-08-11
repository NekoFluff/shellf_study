package com.crazyfluff.shellfstudy.feature.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.data.model.ReviewItem
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.util.RomajiConverter
import com.crazyfluff.shellfstudy.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.feature.search.SubjectSearchOverlay
import com.crazyfluff.shellfstudy.feature.subjectdetail.SubjectDetailSheet

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
    const val CONTINUE_BUTTON = "review_continue_button"
    const val UNDO_BUTTON = "review_undo_button"
    const val SESSION_COMPLETE = "review_session_complete"
    const val DONE_BUTTON = "review_done_button"
    const val BACK_BUTTON = "review_back_button"
    const val SEARCH_BUTTON = "review_search_button"
    const val OVERFLOW_MENU = "review_overflow_menu"
    const val WRAP_UP_MENU_ITEM = "review_wrap_up_menu_item"
    const val ABANDON_MENU_ITEM = "review_abandon_menu_item"
    const val ABANDON_CONFIRM_BUTTON = "review_abandon_confirm_button"
    const val DETAILS_TOGGLE = "review_details_toggle"
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
        onAnswerInputChange = viewModel::onAnswerInputChange,
        onSubmit = viewModel::submitAnswer,
        onDontKnow = viewModel::dontKnowAnswer,
        onContinue = viewModel::onContinue,
        onUndo = viewModel::undoLastAnswer,
        onToggleDetails = viewModel::toggleDetails,
        onRetry = viewModel::loadOrResume,
        onWrapUp = viewModel::wrapUp,
        onAbandon = viewModel::abandonSession,
        onDone = onSessionComplete,
        onBack = onBack,
        searchUiState = searchUiState,
        onSearchQueryChange = searchViewModel::onQueryChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    onAnswerInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onUndo: () -> Unit,
    onToggleDetails: () -> Unit,
    onRetry: () -> Unit,
    onWrapUp: () -> Unit,
    onAbandon: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    searchUiState: SearchUiState = SearchUiState(),
    onSearchQueryChange: (String) -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    // Distinct from the gated details toggle below — an arbitrary subject looked up mid-review via
    // search has no relationship to the current question, so it's never gated by answer state.
    var searchDetailSubjectId by remember { mutableStateOf<Long?>(null) }
    val canManageSession = !uiState.isLoading && !uiState.isSessionComplete && uiState.errorMessage == null

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
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
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag(ReviewScreenTestTags.OVERFLOW_MENU)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Wrap up") },
                                enabled = !uiState.isWrappingUp,
                                onClick = { menuExpanded = false; onWrapUp() },
                                modifier = Modifier.testTag(ReviewScreenTestTags.WRAP_UP_MENU_ITEM)
                            )
                            DropdownMenuItem(
                                text = { Text("Abandon session") },
                                onClick = { menuExpanded = false; showAbandonConfirm = true },
                                modifier = Modifier.testTag(ReviewScreenTestTags.ABANDON_MENU_ITEM)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (showAbandonConfirm) {
            AlertDialog(
                onDismissRequest = { showAbandonConfirm = false },
                title = { Text("Abandon this session?") },
                text = { Text("Progress on items you haven't finished yet will be lost. This won't affect items you've already submitted.") },
                confirmButton = {
                    TextButton(
                        onClick = { showAbandonConfirm = false; onAbandon() },
                        modifier = Modifier.testTag(ReviewScreenTestTags.ABANDON_CONFIRM_BUTTON)
                    ) { Text("Abandon") }
                },
                dismissButton = {
                    TextButton(onClick = { showAbandonConfirm = false }) { Text("Cancel") }
                }
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
                    Column(
                        modifier = Modifier.fillMaxSize().testTag(ReviewScreenTestTags.SESSION_COMPLETE),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Session complete!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Great work. Your reviews have been submitted.")
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier.testTag(ReviewScreenTestTags.DONE_BUTTON)
                        ) { Text("Back to dashboard") }
                    }
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
        // non-interactive bar taking up space and inviting a swipe that does nothing.
        AnimatedVisibility(
            visible = !isSearchActive && uiState.feedback != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SwipeUpDetailsHandle(
                expanded = uiState.isDetailsExpanded,
                onToggle = onToggleDetails
            )
        }

        SubjectSearchOverlay(
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            uiState = searchUiState,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier.fillMaxSize(),
            onSubjectClick = { searchDetailSubjectId = it }
        )

        searchDetailSubjectId?.let { id ->
            SubjectDetailSheet(
                initialSubjectId = id,
                revealMode = DetailRevealMode.FULL,
                isAnswered = true,
                questionType = null,
                onDismiss = { searchDetailSubjectId = null }
            )
        }
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

    Text(
        text = "${uiState.totalCount - uiState.remainingCount} / ${uiState.totalCount}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            .testTag(ReviewScreenTestTags.PROGRESS_COUNT)
    )
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = accentColor,
        drawStopIndicator = {}
    )

    Column(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = item.characters ?: item.meanings.firstOrNull() ?: "?",
            style = MaterialTheme.typography.displayLarge,
            color = accentColor,
            modifier = Modifier.testTag(ReviewScreenTestTags.CHARACTERS)
        )
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = if (questionType == QuestionType.MEANING) "What is the meaning?" else "What is the reading?",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(ReviewScreenTestTags.QUESTION_LABEL)
        )
        Spacer(modifier = Modifier.height(8.dp))
        val feedbackForField = uiState.feedback
        val canUndo = feedbackForField != null && !feedbackForField.isCorrect
        OutlinedTextField(
            value = uiState.answerInput,
            onValueChange = onAnswerInputChange,
            label = { Text("答え") },
            singleLine = true,
            enabled = uiState.feedback == null,
            visualTransformation = if (questionType == QuestionType.READING) {
                RomajiVisualTransformation
            } else {
                VisualTransformation.None
            },
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
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (uiState.feedback == null) onSubmit() }),
            modifier = Modifier.fillMaxWidth().testTag(ReviewScreenTestTags.ANSWER_FIELD)
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
                text = if (feedback.isCorrect) "Correct!" else "Incorrect: ${feedback.correctAnswer}",
                color = if (feedback.isCorrect) SrsStageColors.Enlightened else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(ReviewScreenTestTags.FEEDBACK_TEXT)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onContinue,
                    modifier = Modifier.weight(1f).testTag(ReviewScreenTestTags.CONTINUE_BUTTON)
                ) { Text("Continue") }
            }
        }

        // Reserves room so the swipe-up handle — pinned to the true bottom of the screen as its
        // own overlay in ReviewScreen's outer Box, not laid out inline here — doesn't cover this
        // content. See SwipeUpDetailsHandle for why it lives outside this Column. The extra 24dp
        // covers the handle's own navigationBarsPadding, which grows its footprint on devices with
        // a gesture pill or 3-button nav bar.
        Spacer(modifier = Modifier.height(16.dp + SwipeHandleHeight + 24.dp))
        if (uiState.isDetailsExpanded) {
            SubjectDetailSheet(
                initialSubjectId = item.subjectId,
                revealMode = DetailRevealMode.HIDE_UNTIL_ANSWERED,
                isAnswered = uiState.feedback != null,
                questionType = questionType.toDetailQuestionType(),
                onDismiss = onToggleDetails
            )
        }
    }
}

private fun QuestionType.toDetailQuestionType(): DetailQuestionType = when (this) {
    QuestionType.MEANING -> DetailQuestionType.MEANING
    QuestionType.READING -> DetailQuestionType.READING
}

private val SwipeHandleHeight = 56.dp

/**
 * A bar pinned to the true bottom edge of the screen (rendered from [ReviewScreen]'s outer `Box`,
 * not inline in the scrolling question content) so a swipe starting from the screen's bottom edge
 * — where a user instinctively swipes up from — actually lands on it. [navigationBarsPadding] keeps
 * it clear of the system gesture pill / 3-button nav bar rather than sitting underneath it. Swipe up
 * (or tap, kept as a fallback for accessibility/testability) reveals the subject detail sheet.
 * [draggable] only starts consuming once the drag exceeds touch slop, so a plain tap still passes
 * through untouched to the co-located [clickable] — the two don't fight over the gesture. Triggers
 * as soon as the drag crosses the threshold (not only once the finger lifts) so it feels responsive.
 * Only ever composed once there's something to toggle (see the caller), so it's always interactive —
 * no disabled/dimmed state to render here.
 */
@Composable
private fun SwipeUpDetailsHandle(expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val swipeThresholdPx = with(LocalDensity.current) { 32.dp.toPx() }
    var dragAccumulator by remember { mutableStateOf(0f) }
    var triggeredThisGesture by remember { mutableStateOf(false) }
    val draggableState = rememberDraggableState { delta ->
        dragAccumulator += delta
        if (!triggeredThisGesture && dragAccumulator < -swipeThresholdPx) {
            triggeredThisGesture = true
            onToggle()
        }
    }
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        tonalElevation = 3.dp,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(SwipeHandleHeight)
            .clickable(onClick = onToggle)
            .draggable(
                orientation = Orientation.Vertical,
                state = draggableState,
                onDragStarted = { dragAccumulator = 0f; triggeredThisGesture = false },
                onDragStopped = { dragAccumulator = 0f }
            )
            .testTag(ReviewScreenTestTags.DETAILS_TOGGLE)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = contentColor)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (expanded) "Hide details" else "Swipe up for details",
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Renders the user's raw romaji keystrokes as their live hiragana conversion, without touching
 * the underlying [ReviewUiState.answerInput] — that stays exactly what the user typed, so there's
 * no feedback loop where already-converted kana gets fed back through the converter as more text
 * is typed (which would mis-convert a mid-word "n").
 */
private object RomajiVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val converted = RomajiConverter.toHiragana(text.text)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = converted.length
            override fun transformedToOriginal(offset: Int): Int = text.length
        }
        return TransformedText(AnnotatedString(converted), offsetMapping)
    }
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
            onAnswerInputChange = {},
            onSubmit = {},
            onDontKnow = {},
            onContinue = {},
            onUndo = {},
            onToggleDetails = {},
            onRetry = {},
            onWrapUp = {},
            onAbandon = {},
            onDone = {},
            onBack = {}
        )
    }
}
