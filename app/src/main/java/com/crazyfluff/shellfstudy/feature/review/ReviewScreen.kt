package com.crazyfluff.shellfstudy.feature.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.data.model.ReviewItem
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.util.RomajiConverter
import com.crazyfluff.shellfstudy.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.feature.search.SubjectSearchOverlay

object ReviewScreenTestTags {
    const val LOADING_INDICATOR = "review_loading_indicator"
    const val ERROR_TEXT = "review_error_text"
    const val CHARACTERS = "review_characters"
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
    const val DETAILS_PANEL = "review_details_panel"
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
    val canManageSession = !uiState.isLoading && !uiState.isSessionComplete && uiState.errorMessage == null
    val canUndo = uiState.feedback != null && !uiState.feedback.isCorrect

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
                                text = { Text("Undo last answer") },
                                enabled = canUndo,
                                onClick = { menuExpanded = false; onUndo() },
                                modifier = Modifier.testTag(ReviewScreenTestTags.UNDO_BUTTON)
                            )
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
                        onToggleDetails = onToggleDetails
                    )
                }
            }
        }
    }

        SubjectSearchOverlay(
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            uiState = searchUiState,
            onQueryChange = onSearchQueryChange,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ReviewQuestionContent(
    uiState: ReviewUiState,
    onAnswerInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onToggleDetails: () -> Unit
) {
    val item = uiState.currentItem ?: return
    val questionType = uiState.currentQuestionType ?: return

    val progress = if (uiState.totalCount == 0) 0f else
        (uiState.totalCount - uiState.remainingCount).toFloat() / uiState.totalCount
    val accentColor = subjectColor(item.subjectType)

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = accentColor
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
                text = if (feedback.isCorrect) "Correct!" else "Incorrect — answer: ${feedback.correctAnswer}",
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

        Spacer(modifier = Modifier.height(16.dp))
        SubjectDetailsSection(
            item = item,
            questionType = questionType,
            expanded = uiState.isDetailsExpanded,
            enabled = uiState.feedback != null,
            onToggle = onToggleDetails
        )
    }
}

@Composable
private fun SubjectDetailsSection(
    item: ReviewItem,
    questionType: QuestionType,
    expanded: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    Column {
        TextButton(
            onClick = onToggle,
            enabled = enabled,
            modifier = Modifier.testTag(ReviewScreenTestTags.DETAILS_TOGGLE)
        ) {
            Text(if (expanded) "Hide details" else "View details")
        }
        if (expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().testTag(ReviewScreenTestTags.DETAILS_PANEL)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Level ${item.level} · ${subjectTypeLabel(item.subjectType)}")
                    // Only surface the field that ISN'T currently being tested — showing the
                    // answer being asked for would defeat the point of the question.
                    if (questionType == QuestionType.READING && item.meanings.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Meaning hint: ${item.meanings.joinToString(", ")}")
                    }
                }
            }
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
