package com.crazyfluff.shellfstudy.feature.lesson

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.data.model.LessonItem
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.util.RomajiConverter

object LessonScreenTestTags {
    const val LOADING_INDICATOR = "lesson_loading_indicator"
    const val ERROR_TEXT = "lesson_error_text"
    const val RETRY_BUTTON = "lesson_retry_button"
    const val BACK_BUTTON = "lesson_back_button"
    const val NO_LESSONS_TEXT = "lesson_no_lessons_text"
    const val NO_LESSONS_DONE_BUTTON = "lesson_no_lessons_done_button"
    const val STUDY_CHARACTERS = "lesson_study_characters"
    const val STUDY_NEXT_BUTTON = "lesson_study_next_button"
    const val STUDY_PREVIOUS_BUTTON = "lesson_study_previous_button"
    const val START_QUIZ_BUTTON = "lesson_start_quiz_button"
    const val QUIZ_CHARACTERS = "lesson_quiz_characters"
    const val ANSWER_FIELD = "lesson_answer_field"
    const val SUBMIT_BUTTON = "lesson_submit_button"
    const val DONT_KNOW_BUTTON = "lesson_dont_know_button"
    const val FEEDBACK_TEXT = "lesson_feedback_text"
    const val CONTINUE_BUTTON = "lesson_continue_button"
    const val SESSION_COMPLETE = "lesson_session_complete"
    const val DONE_BUTTON = "lesson_done_button"
}

@Composable
fun LessonRoute(
    onSessionComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: LessonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LessonScreen(
        uiState = uiState,
        onNextStudyCard = viewModel::nextStudyCard,
        onPreviousStudyCard = viewModel::previousStudyCard,
        onAnswerInputChange = viewModel::onAnswerInputChange,
        onSubmit = viewModel::submitAnswer,
        onDontKnow = viewModel::dontKnowAnswer,
        onContinue = viewModel::onContinue,
        onRetry = viewModel::load,
        onDone = onSessionComplete,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    uiState: LessonUiState,
    onNextStudyCard: () -> Unit,
    onPreviousStudyCard: () -> Unit,
    onAnswerInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(LessonScreenTestTags.BACK_BUTTON)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag(LessonScreenTestTags.LOADING_INDICATOR))
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
                            modifier = Modifier.testTag(LessonScreenTestTags.ERROR_TEXT)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag(LessonScreenTestTags.RETRY_BUTTON)
                        ) { Text("Retry") }
                    }
                }

                uiState.hasNoLessonsAvailable -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No lessons available right now.",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.testTag(LessonScreenTestTags.NO_LESSONS_TEXT)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier.testTag(LessonScreenTestTags.NO_LESSONS_DONE_BUTTON)
                        ) { Text("Back to dashboard") }
                    }
                }

                uiState.isSessionComplete -> {
                    Column(
                        modifier = Modifier.fillMaxSize().testTag(LessonScreenTestTags.SESSION_COMPLETE),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Lesson complete!", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Great work. These items will start showing up in your reviews.")
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onDone,
                            modifier = Modifier.testTag(LessonScreenTestTags.DONE_BUTTON)
                        ) { Text("Back to dashboard") }
                    }
                }

                uiState.phase == LessonPhase.STUDY -> {
                    LessonStudyContent(
                        uiState = uiState,
                        onNext = onNextStudyCard,
                        onPrevious = onPreviousStudyCard
                    )
                }

                uiState.phase == LessonPhase.QUIZ -> {
                    LessonQuizContent(
                        uiState = uiState,
                        onAnswerInputChange = onAnswerInputChange,
                        onSubmit = onSubmit,
                        onDontKnow = onDontKnow,
                        onContinue = onContinue
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LessonStudyContent(
    uiState: LessonUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val item = uiState.studyItems.getOrNull(uiState.studyIndex) ?: return
    val accentColor = subjectColor(item.subjectType)
    val isLastCard = uiState.studyIndex == uiState.studyItems.lastIndex

    LinearProgressIndicator(
        progress = { (uiState.studyIndex + 1).toFloat() / uiState.studyItems.size },
        modifier = Modifier.fillMaxWidth(),
        color = accentColor
    )

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = item.characters ?: item.meanings.firstOrNull() ?: "?",
            style = MaterialTheme.typography.displayLarge,
            color = accentColor,
            modifier = Modifier.testTag(LessonScreenTestTags.STUDY_CHARACTERS)
        )
        Text(
            text = "Level ${item.level} · ${subjectTypeLabel(item.subjectType)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        LessonDetailSection(title = "Meaning", primary = item.meanings.joinToString(", "), mnemonic = item.meaningMnemonic)

        if (item.readings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            LessonDetailSection(title = "Reading", primary = item.readings.joinToString(", "), mnemonic = item.readingMnemonic)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onPrevious,
            enabled = uiState.studyIndex > 0,
            modifier = Modifier.weight(1f).testTag(LessonScreenTestTags.STUDY_PREVIOUS_BUTTON)
        ) { Text("Back") }
        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .testTag(if (isLastCard) LessonScreenTestTags.START_QUIZ_BUTTON else LessonScreenTestTags.STUDY_NEXT_BUTTON)
        ) { Text(if (isLastCard) "Start Quiz" else "Next") }
    }
}

@Composable
private fun LessonDetailSection(title: String, primary: String, mnemonic: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(primary, style = MaterialTheme.typography.bodyLarge)
        if (!mnemonic.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(mnemonic, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LessonQuizContent(
    uiState: LessonUiState,
    onAnswerInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit
) {
    val item = uiState.currentQuizItem ?: return
    val questionType = uiState.currentQuestionType ?: return

    val progress = if (uiState.totalQuizCount == 0) 0f else
        (uiState.totalQuizCount - uiState.remainingQuizCount).toFloat() / uiState.totalQuizCount
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
            modifier = Modifier.testTag(LessonScreenTestTags.QUIZ_CHARACTERS)
        )
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = if (questionType == LessonQuestionType.MEANING) "What is the meaning?" else "What is the reading?",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.answerInput,
            onValueChange = onAnswerInputChange,
            label = { Text("答え") },
            singleLine = true,
            enabled = uiState.feedback == null,
            visualTransformation = if (questionType == LessonQuestionType.READING) {
                LessonRomajiVisualTransformation
            } else {
                VisualTransformation.None
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (uiState.feedback == null) onSubmit() }),
            modifier = Modifier.fillMaxWidth().testTag(LessonScreenTestTags.ANSWER_FIELD)
        )

        Spacer(modifier = Modifier.height(16.dp))

        val feedback = uiState.feedback
        if (feedback == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDontKnow,
                    modifier = Modifier.weight(1f).testTag(LessonScreenTestTags.DONT_KNOW_BUTTON)
                ) { Text("I don't know") }
                Button(
                    onClick = onSubmit,
                    enabled = uiState.answerInput.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag(LessonScreenTestTags.SUBMIT_BUTTON)
                ) { Text("Submit") }
            }
        } else {
            Text(
                text = if (feedback.isCorrect) "Correct!" else "Incorrect — answer: ${feedback.correctAnswer}",
                color = if (feedback.isCorrect) SrsStageColors.Enlightened else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(LessonScreenTestTags.FEEDBACK_TEXT)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().testTag(LessonScreenTestTags.CONTINUE_BUTTON)
            ) { Text("Continue") }
        }
    }
}

/** Mirrors ReviewScreen's romaji preview transform — see that file for why this doesn't touch the raw input. */
private object LessonRomajiVisualTransformation : VisualTransformation {
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
private fun LessonScreenStudyPreview() {
    ShellfStudyTheme {
        LessonScreen(
            uiState = LessonUiState(
                isLoading = false,
                phase = LessonPhase.STUDY,
                studyItems = listOf(
                    LessonItem(
                        assignmentId = 1,
                        subjectId = 1,
                        subjectType = SubjectType.KANJI,
                        characters = "水",
                        level = 3,
                        meanings = listOf("Water"),
                        readings = listOf("みず"),
                        meaningMnemonic = "This kanji looks like a stream of water.",
                        readingMnemonic = "Sounds like 'me-zoo'."
                    )
                ),
                studyIndex = 0
            ),
            onNextStudyCard = {},
            onPreviousStudyCard = {},
            onAnswerInputChange = {},
            onSubmit = {},
            onDontKnow = {},
            onContinue = {},
            onRetry = {},
            onDone = {},
            onBack = {}
        )
    }
}
