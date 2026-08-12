package com.crazyfluff.shellfstudy.feature.lesson

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.data.model.ContextSentence
import com.crazyfluff.shellfstudy.core.data.model.LessonItem
import com.crazyfluff.shellfstudy.core.data.model.PitchAccent
import com.crazyfluff.shellfstudy.core.designsystem.quiz.GatedContinueButton
import com.crazyfluff.shellfstudy.core.designsystem.quiz.feedbackDetailPrefix
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderSection
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.ReadingTypeRow
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.RelatedSubjectsSection
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.SectionEyebrow
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.SubjectGlyph
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.VocabReadingRow
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.WkMnemonicText
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.componentsLabel
import com.crazyfluff.shellfstudy.core.designsystem.text.RomajiVisualTransformation
import com.crazyfluff.shellfstudy.core.designsystem.theme.RankChangeChip
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.core.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.core.designsystem.writing.WritingPracticeSection
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.core.quiz.QuestionType
import com.crazyfluff.shellfstudy.core.util.formatAnswerList
import com.crazyfluff.shellfstudy.feature.subjectdetail.SubjectDetailSheetHost
import com.crazyfluff.shellfstudy.feature.subjectdetail.rememberSubjectDetailSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

object LessonScreenTestTags {
    const val LOADING_INDICATOR = "lesson_loading_indicator"
    const val ERROR_TEXT = "lesson_error_text"
    const val RETRY_BUTTON = "lesson_retry_button"
    const val BACK_BUTTON = "lesson_back_button"
    const val NO_LESSONS_TEXT = "lesson_no_lessons_text"
    const val NO_LESSONS_DONE_BUTTON = "lesson_no_lessons_done_button"
    const val SELECT_ALL_CHIP = "lesson_select_all_chip"
    const val SELECT_NONE_CHIP = "lesson_select_none_chip"
    const val STEPPER_DECREMENT = "lesson_stepper_decrement"
    const val STEPPER_INCREMENT = "lesson_stepper_increment"
    const val STEPPER_SLIDER = "lesson_stepper_slider"
    const val CUSTOMIZE_TOGGLE = "lesson_customize_toggle"
    const val START_SELECTED_BUTTON = "lesson_start_selected_button"
    fun lessonCheckboxTag(assignmentId: Long) = "lesson_checkbox_$assignmentId"
    fun levelGroupToggleTag(level: Int) = "lesson_level_toggle_$level"
    const val STUDY_PAGER = "lesson_study_pager"
    const val STUDY_CHARACTERS = "lesson_study_characters"
    const val STUDY_PROGRESS_COUNT = "lesson_study_progress_count"
    const val STUDY_NEXT_BUTTON = "lesson_study_next_button"
    const val STUDY_PREVIOUS_BUTTON = "lesson_study_previous_button"
    const val START_QUIZ_BUTTON = "lesson_start_quiz_button"
    const val QUIZ_CHARACTERS = "lesson_quiz_characters"
    const val QUIZ_PROGRESS_COUNT = "lesson_quiz_progress_count"
    const val ANSWER_FIELD = "lesson_answer_field"
    const val SUBMIT_BUTTON = "lesson_submit_button"
    const val DONT_KNOW_BUTTON = "lesson_dont_know_button"
    const val FEEDBACK_TEXT = "lesson_feedback_text"
    const val ANSWER_DETAIL_TEXT = "lesson_answer_detail_text"
    const val RANK_CHANGE_TEXT = "lesson_rank_change_text"
    const val QUIZ_SUBJECT_TYPE_LABEL = "lesson_quiz_subject_type_label"
    const val CONTINUE_BUTTON = "lesson_continue_button"
    const val SESSION_COMPLETE = "lesson_session_complete"
    const val DONE_BUTTON = "lesson_done_button"
}

sealed interface LessonScreenEvent {
    data class ToggleLessonSelection(val assignmentId: Long) : LessonScreenEvent
    data class SelectFirst(val count: Int) : LessonScreenEvent
    data object SelectAll : LessonScreenEvent
    data object SelectNone : LessonScreenEvent
    data object StartSelectedLessons : LessonScreenEvent
    data class StudyCardSwiped(val index: Int) : LessonScreenEvent
    data object NextStudyCard : LessonScreenEvent
    data object PreviousStudyCard : LessonScreenEvent
    data class AnswerInputChange(val value: String) : LessonScreenEvent
    data object Submit : LessonScreenEvent
    data object DontKnow : LessonScreenEvent
    data object Continue : LessonScreenEvent
    data object Retry : LessonScreenEvent
    data object Done : LessonScreenEvent
    data object Back : LessonScreenEvent
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
        onEvent = { event ->
            when (event) {
                is LessonScreenEvent.ToggleLessonSelection -> viewModel.toggleLessonSelection(event.assignmentId)
                is LessonScreenEvent.SelectFirst -> viewModel.selectFirst(event.count)
                LessonScreenEvent.SelectAll -> viewModel.selectAll()
                LessonScreenEvent.SelectNone -> viewModel.selectNone()
                LessonScreenEvent.StartSelectedLessons -> viewModel.startSelectedLessons()
                is LessonScreenEvent.StudyCardSwiped -> viewModel.onStudyCardSwiped(event.index)
                LessonScreenEvent.NextStudyCard -> viewModel.nextStudyCard()
                LessonScreenEvent.PreviousStudyCard -> viewModel.previousStudyCard()
                is LessonScreenEvent.AnswerInputChange -> viewModel.onAnswerInputChange(event.value)
                LessonScreenEvent.Submit -> viewModel.submitAnswer()
                LessonScreenEvent.DontKnow -> viewModel.dontKnowAnswer()
                LessonScreenEvent.Continue -> viewModel.onContinue()
                LessonScreenEvent.Retry -> viewModel.load()
                LessonScreenEvent.Done -> onSessionComplete()
                LessonScreenEvent.Back -> onBack()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    uiState: LessonUiState,
    onEvent: (LessonScreenEvent) -> Unit
) {
    val onToggleLessonSelection: (Long) -> Unit = { onEvent(LessonScreenEvent.ToggleLessonSelection(it)) }
    val onSelectFirst: (Int) -> Unit = { onEvent(LessonScreenEvent.SelectFirst(it)) }
    val onSelectAll = { onEvent(LessonScreenEvent.SelectAll) }
    val onSelectNone = { onEvent(LessonScreenEvent.SelectNone) }
    val onStartSelectedLessons = { onEvent(LessonScreenEvent.StartSelectedLessons) }
    val onStudyCardSwiped: (Int) -> Unit = { onEvent(LessonScreenEvent.StudyCardSwiped(it)) }
    val onNextStudyCard = { onEvent(LessonScreenEvent.NextStudyCard) }
    val onPreviousStudyCard = { onEvent(LessonScreenEvent.PreviousStudyCard) }
    val onAnswerInputChange: (String) -> Unit = { onEvent(LessonScreenEvent.AnswerInputChange(it)) }
    val onSubmit = { onEvent(LessonScreenEvent.Submit) }
    val onDontKnow = { onEvent(LessonScreenEvent.DontKnow) }
    val onContinue = { onEvent(LessonScreenEvent.Continue) }
    val onRetry = { onEvent(LessonScreenEvent.Retry) }
    val onDone = { onEvent(LessonScreenEvent.Done) }
    val onBack = { onEvent(LessonScreenEvent.Back) }

    val detailSheetState = rememberSubjectDetailSheetState()

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

                uiState.phase == LessonPhase.SELECT -> {
                    LessonSelectionContent(
                        uiState = uiState,
                        onToggle = onToggleLessonSelection,
                        onSelectFirst = onSelectFirst,
                        onSelectAll = onSelectAll,
                        onSelectNone = onSelectNone,
                        onStart = onStartSelectedLessons
                    )
                }

                uiState.phase == LessonPhase.STUDY -> {
                    LessonStudyContent(
                        uiState = uiState,
                        onNext = onNextStudyCard,
                        onPrevious = onPreviousStudyCard,
                        onSwiped = onStudyCardSwiped,
                        onSubjectClick = { detailSheetState.show(it) }
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

    SubjectDetailSheetHost(detailSheetState)
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LessonStudyContent(
    uiState: LessonUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSwiped: (Int) -> Unit,
    onSubjectClick: (Long) -> Unit
) {
    val currentItem = uiState.studyItems.getOrNull(uiState.studyIndex) ?: return
    val isLastCard = uiState.studyIndex == uiState.studyItems.lastIndex
    val accentColor = subjectColor(currentItem.subjectType)

    val pagerState = rememberPagerState(initialPage = uiState.studyIndex) { uiState.studyItems.size }

    LaunchedEffect(uiState.studyIndex) {
        if (pagerState.currentPage != uiState.studyIndex) {
            pagerState.animateScrollToPage(uiState.studyIndex)
        }
    }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .collect { page -> onSwiped(page) }
    }

    Text(
        text = "${uiState.studyIndex + 1} / ${uiState.studyItems.size}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            .testTag(LessonScreenTestTags.STUDY_PROGRESS_COUNT)
    )
    LinearProgressIndicator(
        progress = { (uiState.studyIndex + 1).toFloat() / uiState.studyItems.size },
        modifier = Modifier.fillMaxWidth(),
        color = accentColor,
        drawStopIndicator = {}
    )

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .testTag(LessonScreenTestTags.STUDY_PAGER)
    ) { page ->
        val item = uiState.studyItems[page]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isVocabulary = item.subjectType == SubjectType.VOCABULARY || item.subjectType == SubjectType.KANA_VOCABULARY
            val hasReadingBreakdown = item.onyomiReadings.isNotEmpty() || item.kunyomiReadings.isNotEmpty() || item.nanoriReadings.isNotEmpty()

            // Headline: glyph + level/type subtitle + (vocab) part-of-speech tags as one tight
            // cluster, mirroring the subject detail view's headline — see SubjectDetailContent.kt.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SubjectGlyph(
                    characters = item.characters ?: item.meanings.firstOrNull(),
                    characterImageUrl = null,
                    subjectType = item.subjectType,
                    size = 96.dp,
                    modifier = Modifier.testTag(LessonScreenTestTags.STUDY_CHARACTERS)
                )
                Text(
                    text = "Level ${item.level} · ${subjectTypeLabel(item.subjectType)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isVocabulary && item.partsOfSpeech.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.partsOfSpeech.forEach { part -> AssistChip(onClick = {}, label = { Text(part) }) }
                    }
                }
            }

            val strokeOrder = uiState.strokeOrderBySubjectId[item.subjectId] ?: StrokeOrderUiState.Unavailable
            StrokeOrderSection(strokeOrder)
            WritingPracticeSection(strokeOrder = strokeOrder, resetKey = item.subjectId)

            RelatedSubjectsSection(
                title = componentsLabel(item.subjectType),
                subjects = item.componentSubjectIds.mapNotNull { uiState.relatedSubjectsById[it] },
                onSubjectClick = onSubjectClick
            )

            LessonMeaningSection(item)

            if (item.readings.isNotEmpty()) {
                HorizontalDivider()
                LessonReadingSection(
                    item = item,
                    isVocabulary = isVocabulary,
                    hasReadingBreakdown = hasReadingBreakdown,
                    pitchAccents = uiState.pitchAccentsBySubjectId[item.subjectId].orEmpty(),
                    showPitchAccent = uiState.showPitchAccent
                )
            }
            if (isVocabulary && item.contextSentences.isNotEmpty()) {
                HorizontalDivider()
                LessonContextSentencesSection(item.contextSentences)
            }
            if (item.subjectType == SubjectType.KANJI) {
                RelatedSubjectsSection(
                    title = "Visually similar",
                    subjects = item.visuallySimilarSubjectIds.mapNotNull { uiState.relatedSubjectsById[it] },
                    onSubjectClick = onSubjectClick
                )
            }
            RelatedSubjectsSection(
                title = "Used in",
                subjects = item.amalgamationSubjectIds.mapNotNull { uiState.relatedSubjectsById[it] },
                onSubjectClick = onSubjectClick
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LessonSelectionContent(
    uiState: LessonUiState,
    onToggle: (Long) -> Unit,
    onSelectFirst: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onStart: () -> Unit
) {
    val selectedCount = uiState.selectedAssignmentIds.size
    val total = uiState.availableLessons.size
    var customizeExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedLevels by rememberSaveable {
        val levelsWithSelection = uiState.availableLessons
            .filter { it.assignmentId in uiState.selectedAssignmentIds }
            .map { it.level }
            .toSet()
        mutableStateOf(levelsWithSelection)
    }

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Choose lessons to study", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text("$selectedCount of $total selected", style = MaterialTheme.typography.bodyMedium)

        if (!customizeExpanded) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "$selectedCount",
                style = MaterialTheme.typography.displaySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = { onSelectFirst(selectedCount - 1) },
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag(LessonScreenTestTags.STEPPER_DECREMENT)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Fewer lessons")
                }
                Slider(
                    value = selectedCount.toFloat(),
                    onValueChange = { onSelectFirst(it.roundToInt()) },
                    valueRange = 0f..total.toFloat(),
                    steps = (total - 1).coerceAtLeast(0),
                    modifier = Modifier.weight(1f).testTag(LessonScreenTestTags.STEPPER_SLIDER)
                )
                IconButton(
                    onClick = { onSelectFirst(selectedCount + 1) },
                    enabled = selectedCount < total,
                    modifier = Modifier.testTag(LessonScreenTestTags.STEPPER_INCREMENT)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "More lessons")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = onStart,
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth().testTag(LessonScreenTestTags.START_SELECTED_BUTTON)
        ) {
            Text(if (selectedCount > 0) "Start session" else "Select lessons to study")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { customizeExpanded = !customizeExpanded }
                .testTag(LessonScreenTestTags.CUSTOMIZE_TOGGLE)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (customizeExpanded) "Back to quick pick" else "Customize selection",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = if (customizeExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
        }

        if (customizeExpanded) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onSelectAll,
                    label = { Text("All") },
                    modifier = Modifier.testTag(LessonScreenTestTags.SELECT_ALL_CHIP)
                )
                AssistChip(
                    onClick = onSelectNone,
                    label = { Text("None") },
                    modifier = Modifier.testTag(LessonScreenTestTags.SELECT_NONE_CHIP)
                )
            }
        }
    }

    if (customizeExpanded) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            uiState.availableLessons.groupBy { it.level }.forEach { (level, itemsForLevel) ->
                val levelExpanded = level in expandedLevels
                val selectedInLevel = itemsForLevel.count { it.assignmentId in uiState.selectedAssignmentIds }
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedLevels = if (levelExpanded) expandedLevels - level else expandedLevels + level
                            }
                            .testTag(LessonScreenTestTags.levelGroupToggleTag(level))
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Level $level · $selectedInLevel of ${itemsForLevel.size} selected",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Icon(
                            imageVector = if (levelExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null
                        )
                    }
                }
                if (levelExpanded) {
                    item {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsForLevel.forEach { lessonItem ->
                                val checked = lessonItem.assignmentId in uiState.selectedAssignmentIds
                                LessonGlyphTile(
                                    lessonItem = lessonItem,
                                    selected = checked,
                                    minWidth = 64.dp,
                                    minHeight = 72.dp,
                                    maxWidth = 112.dp,
                                    modifier = Modifier.testTag(LessonScreenTestTags.lessonCheckboxTag(lessonItem.assignmentId)),
                                    onClick = { onToggle(lessonItem.assignmentId) }
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun LessonGlyphTile(
    lessonItem: LessonItem,
    selected: Boolean,
    minWidth: Dp,
    minHeight: Dp,
    maxWidth: Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val color = subjectColor(lessonItem.subjectType)
    val contentColor = if (selected) Color.White else color
    val shape = RoundedCornerShape(12.dp)
    val furigana = lessonItem.readings.firstOrNull()

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minWidth, minHeight = minHeight)
            .widthIn(max = maxWidth)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            )
            .background(if (selected) color else color.copy(alpha = 0.10f), shape)
            .then(
                if (!selected) Modifier.border(1.dp, color.copy(alpha = 0.35f), shape) else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (furigana != null) {
                Text(
                    text = furigana,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = lessonItem.characters ?: lessonItem.meanings.firstOrNull() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LessonMeaningSection(item: LessonItem) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Meaning", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(item.meanings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
            if (item.auxiliaryMeanings.isNotEmpty()) {
                Text(
                    text = item.auxiliaryMeanings.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!item.meaningMnemonic.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionEyebrow("Meaning mnemonic")
                WkMnemonicText(item.meaningMnemonic, style = MaterialTheme.typography.bodyMedium)
                if (!item.meaningHint.isNullOrBlank()) {
                    WkMnemonicText(item.meaningHint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LessonReadingSection(
    item: LessonItem,
    isVocabulary: Boolean,
    hasReadingBreakdown: Boolean,
    pitchAccents: List<PitchAccent>,
    showPitchAccent: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Reading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            when {
                item.subjectType == SubjectType.KANJI && hasReadingBreakdown -> {
                    if (item.onyomiReadings.isNotEmpty()) ReadingTypeRow(label = "On'yomi", readings = item.onyomiReadings)
                    if (item.kunyomiReadings.isNotEmpty()) ReadingTypeRow(label = "Kun'yomi", readings = item.kunyomiReadings)
                    if (item.nanoriReadings.isNotEmpty()) ReadingTypeRow(label = "Nanori", readings = item.nanoriReadings)
                }
                isVocabulary -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    item.readings.forEach { reading ->
                        VocabReadingRow(
                            reading = reading,
                            pitchAccents = pitchAccents,
                            showPitchAccent = showPitchAccent,
                            hasAudio = false,
                            onPlayReading = null
                        )
                    }
                }
                else -> Text(item.readings.joinToString(", "), style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (!item.readingMnemonic.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionEyebrow("Reading mnemonic")
                WkMnemonicText(item.readingMnemonic, style = MaterialTheme.typography.bodyMedium)
                if (!item.readingHint.isNullOrBlank()) {
                    WkMnemonicText(item.readingHint, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LessonContextSentencesSection(sentences: List<ContextSentence>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionEyebrow("Context sentences")
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            sentences.forEach { sentence ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(sentence.japanese, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = sentence.english,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
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

    val answerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.currentQuizItem, uiState.currentQuestionType) {
        answerFocusRequester.requestFocus()
    }

    val progress = if (uiState.totalQuizCount == 0) 0f else
        (uiState.totalQuizCount - uiState.remainingQuizCount).toFloat() / uiState.totalQuizCount
    val accentColor = subjectColor(item.subjectType)

    Text(
        text = "${uiState.totalQuizCount - uiState.remainingQuizCount} / ${uiState.totalQuizCount}",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            .testTag(LessonScreenTestTags.QUIZ_PROGRESS_COUNT)
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
            modifier = Modifier.testTag(LessonScreenTestTags.QUIZ_CHARACTERS)
        )
        if (uiState.showSubjectTypeLabel) {
            Text(
                text = subjectTypeLabel(item.subjectType),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(LessonScreenTestTags.QUIZ_SUBJECT_TYPE_LABEL)
            )
        }
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = if (questionType == QuestionType.MEANING) "What is the meaning?" else "What is the reading?",
            style = MaterialTheme.typography.bodyLarge
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
            modifier = Modifier.fillMaxWidth()
                .focusRequester(answerFocusRequester)
                .testTag(LessonScreenTestTags.ANSWER_FIELD)
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
                text = if (feedback.isCorrect) "Correct!" else "Incorrect",
                color = if (feedback.isCorrect) SrsStageColors.Enlightened else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(LessonScreenTestTags.FEEDBACK_TEXT)
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
                        .testTag(LessonScreenTestTags.ANSWER_DETAIL_TEXT)
                        .then(
                            if (answers.hasMore) {
                                Modifier.clickable { isDetailExpanded = !isDetailExpanded }
                            } else {
                                Modifier
                            }
                        )
                )
            }
            uiState.rankChange?.let { rankChange ->
                Spacer(modifier = Modifier.height(4.dp))
                RankChangeChip(rankChange, modifier = Modifier.testTag(LessonScreenTestTags.RANK_CHANGE_TEXT))
            }
            Spacer(modifier = Modifier.height(16.dp))
            GatedContinueButton(
                feedback = feedback,
                onContinue = onContinue,
                continueButtonTestTag = LessonScreenTestTags.CONTINUE_BUTTON,
                modifier = Modifier.fillMaxWidth()
            )
        }
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
            onEvent = {}
        )
    }
}
