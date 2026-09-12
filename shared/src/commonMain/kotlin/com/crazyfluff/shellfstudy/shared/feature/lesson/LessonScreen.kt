package com.crazyfluff.shellfstudy.shared.feature.lesson
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import com.crazyfluff.shellfstudy.shared.designsystem.text.AkebiSelectableContainer
import com.crazyfluff.shellfstudy.shared.designsystem.text.ContextSentenceRow
import com.crazyfluff.shellfstudy.shared.designsystem.text.JapaneseText
import com.crazyfluff.shellfstudy.shared.designsystem.text.rememberShareText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.crazyfluff.shellfstudy.shared.data.model.ContextSentence
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.designsystem.components.CompactTopBar
import com.crazyfluff.shellfstudy.shared.designsystem.components.SectionTitle
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
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderSection
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentUiState
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.RelatedSubjectsSection
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectGlyph
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectMeaningAnswer
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectMnemonicZone
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectReadingAnswer
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.componentsLabel
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.headlineGlyphBoxHeight
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.shared.designsystem.writing.WritingPracticeSection
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.toSessionAnswerRow
import com.crazyfluff.shellfstudy.shared.quiz.toSessionMissedItemRow
import com.crazyfluff.shellfstudy.shared.util.formatAnswerList
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.toDetailQuestionType
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.LocalPitchAccentCheck
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.PitchAccentCheck
import com.crazyfluff.shellfstudy.shared.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.feature.search.SubjectSearchOverlay
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheet
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheetHost
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.rememberSubjectDetailSheetState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlin.math.roundToInt

object LessonScreenTestTags {
    const val LOADING_INDICATOR = "lesson_loading_indicator"
    const val ERROR_TEXT = "lesson_error_text"
    const val RETRY_BUTTON = "lesson_retry_button"
    const val STUDY_OFFLINE_BUTTON = "lesson_study_offline_button"
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
    const val TOTAL_TIMER_TEXT = "lesson_total_timer_text"
    const val QUESTION_TIMER_TEXT = "lesson_question_timer_text"
    const val ANSWER_FIELD = "lesson_answer_field"
    const val TYPE_MISMATCH_TEXT = "lesson_type_mismatch_text"
    const val SUBMIT_BUTTON = "lesson_submit_button"
    const val DONT_KNOW_BUTTON = "lesson_dont_know_button"
    const val FEEDBACK_TEXT = "lesson_feedback_text"
    const val ANSWER_DETAIL_TEXT = "lesson_answer_detail_text"
    const val QUIZ_SUBJECT_TYPE_LABEL = "lesson_quiz_subject_type_label"
    const val QUESTION_LABEL = "lesson_question_label"
    const val UNDO_BUTTON = "lesson_undo_button"
    const val RANK_CHANGE_TEXT = "lesson_rank_change_text"
    const val CONTINUE_BUTTON = "lesson_continue_button"
    const val SESSION_COMPLETE = "lesson_session_complete"
    const val SESSION_OVERVIEW_CARD = "lesson_session_overview_card"
    const val ITEMS_LEARNED_TEXT = "lesson_items_learned_text"
    const val CORRECT_FIRST_TRY_TEXT = "lesson_correct_first_try_text"
    const val SESSION_TIMING_CARD = "lesson_session_timing_card"
    const val SESSION_TOTAL_TIME_TEXT = "lesson_session_total_time_text"
    const val SESSION_AVERAGE_TIME_TEXT = "lesson_session_average_time_text"
    const val SESSION_SLOWEST_CARD = "lesson_session_slowest_card"
    const val SESSION_MISSED_CARD = "lesson_session_missed_card"
    const val DONE_BUTTON = "lesson_done_button"
    const val SEARCH_BUTTON = "lesson_search_button"
    const val OVERFLOW_MENU = "lesson_overflow_menu"
    const val ABANDON_MENU_ITEM = "lesson_abandon_menu_item"
    const val ABANDON_CONFIRM_BUTTON = "lesson_abandon_confirm_button"
    const val STUDY_BATCH_LABEL = "lesson_study_batch_label"
    const val QUIZ_SESSION_CONTEXT_LABEL = "lesson_quiz_session_context_label"
    fun typeSelectorChipTag(type: SubjectType) = "lesson_type_selector_${type.name.lowercase()}"
    const val SORT_DROPDOWN = "lesson_sort_dropdown"
    fun sortOptionTag(sort: LessonSort) = "lesson_sort_option_${sort.name.lowercase()}"
    const val BATCH_COMPLETE = "lesson_batch_complete"
    const val BATCH_COMPLETE_HEADLINE = "lesson_batch_complete_headline"
    const val BATCH_COMPLETE_SUMMARY_TEXT = "lesson_batch_complete_summary_text"
    const val BATCH_COMPLETE_MISSED_TEXT = "lesson_batch_complete_missed_text"
    const val CONTINUE_SESSION_BUTTON = "lesson_continue_session_button"
    const val FINISH_FOR_NOW_BUTTON = "lesson_finish_for_now_button"
    const val PRACTICE_MISSED_BUTTON = "lesson_practice_missed_button"
    const val FINISH_SESSION_BUTTON = "lesson_finish_session_button"
}

sealed interface LessonScreenEvent {
    data class ToggleLessonSelection(val assignmentId: Long) : LessonScreenEvent
    data class ToggleLessonTypeSelection(val type: SubjectType) : LessonScreenEvent
    data class SetLessonSort(val sort: LessonSort) : LessonScreenEvent
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
    data object Undo : LessonScreenEvent
    data object Continue : LessonScreenEvent
    data object ToggleDetails : LessonScreenEvent
    data object CloseDetails : LessonScreenEvent
    /** The quiz hint's "Check now"/"Try again" link — carries the question it was offered on. */
    data class CheckPitchAccent(val item: LessonItem) : LessonScreenEvent
    data object Retry : LessonScreenEvent
    data object StudyOffline : LessonScreenEvent
    data object Abandon : LessonScreenEvent
    /** The batch checkpoint's primary action — walks into the next batch's flashcards. */
    data object ContinueSession : LessonScreenEvent
    /** The batch checkpoint's "Finishing for now" — keeps the session and leaves the screen. */
    data object FinishForNow : LessonScreenEvent
    /** The final checkpoint's optional extra pass over the session's misses. */
    data object PracticeMissed : LessonScreenEvent
    /** The final checkpoint's "See results" — declines the extra practice and shows the summary. */
    data object FinishSession : LessonScreenEvent
    data object Done : LessonScreenEvent
    data object Back : LessonScreenEvent
    data class SearchQueryChange(val query: String) : LessonScreenEvent
}

@Composable
fun LessonRoute(
    onSessionComplete: () -> Unit,
    onBack: () -> Unit,
    viewModel: LessonViewModel = koinViewModel(),
    searchViewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()

    // Both exits navigate back; which one it was is the ViewModel's business (abandoning cleared the
    // persisted session, parking kept it), and the dashboard reads the difference from whether a
    // session is still stored.
    LaunchedEffect(uiState.exit) {
        if (uiState.exit != LessonUiState.ExitRequest.None) onBack()
    }

    LessonScreen(
        uiState = uiState,
        onEvent = { event ->
            when (event) {
                is LessonScreenEvent.ToggleLessonSelection -> viewModel.toggleLessonSelection(event.assignmentId)
                is LessonScreenEvent.ToggleLessonTypeSelection -> viewModel.toggleLessonTypeSelection(event.type)
                is LessonScreenEvent.SetLessonSort -> viewModel.setLessonSort(event.sort)
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
                LessonScreenEvent.Undo -> viewModel.undoLastAnswer()
                LessonScreenEvent.Continue -> viewModel.onContinue()
                LessonScreenEvent.ToggleDetails -> viewModel.toggleDetails()
                LessonScreenEvent.CloseDetails -> viewModel.closeDetails()
                is LessonScreenEvent.CheckPitchAccent -> viewModel.checkPitchAccent(event.item)
                LessonScreenEvent.Retry -> viewModel.load()
                LessonScreenEvent.StudyOffline -> viewModel.studyOffline()
                LessonScreenEvent.Abandon -> viewModel.abandonSession()
                LessonScreenEvent.ContinueSession -> viewModel.continueSession()
                LessonScreenEvent.FinishForNow -> viewModel.finishForNow()
                LessonScreenEvent.PracticeMissed -> viewModel.practiceMissedItems()
                LessonScreenEvent.FinishSession -> viewModel.finishSessionNow()
                LessonScreenEvent.Done -> onSessionComplete()
                LessonScreenEvent.Back -> onBack()
                is LessonScreenEvent.SearchQueryChange -> searchViewModel.onQueryChange(event.query)
            }
        },
        searchUiState = searchUiState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    uiState: LessonUiState,
    onEvent: (LessonScreenEvent) -> Unit,
    searchUiState: SearchUiState = SearchUiState()
) {
    val onToggleLessonSelection: (Long) -> Unit = { onEvent(LessonScreenEvent.ToggleLessonSelection(it)) }
    val onToggleLessonTypeSelection: (SubjectType) -> Unit = { onEvent(LessonScreenEvent.ToggleLessonTypeSelection(it)) }
    val onSetLessonSort: (LessonSort) -> Unit = { onEvent(LessonScreenEvent.SetLessonSort(it)) }
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
    val onUndo = { onEvent(LessonScreenEvent.Undo) }
    val onContinue = { onEvent(LessonScreenEvent.Continue) }
    val onToggleDetails = { onEvent(LessonScreenEvent.ToggleDetails) }
    val onCloseDetails = { onEvent(LessonScreenEvent.CloseDetails) }
    val onRetry = { onEvent(LessonScreenEvent.Retry) }
    val onStudyOffline = { onEvent(LessonScreenEvent.StudyOffline) }
    val onAbandon = { onEvent(LessonScreenEvent.Abandon) }
    val onContinueSession = { onEvent(LessonScreenEvent.ContinueSession) }
    val onFinishForNow = { onEvent(LessonScreenEvent.FinishForNow) }
    val onPracticeMissed = { onEvent(LessonScreenEvent.PracticeMissed) }
    val onFinishSession = { onEvent(LessonScreenEvent.FinishSession) }
    val onDone = { onEvent(LessonScreenEvent.Done) }
    val onBack = { onEvent(LessonScreenEvent.Back) }
    val onSearchQueryChange: (String) -> Unit = { onEvent(LessonScreenEvent.SearchQueryChange(it)) }

    val detailSheetState = rememberSubjectDetailSheetState()
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbandonConfirm by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    // A session only exists to abandon once the user has committed to a lesson session — the SELECT
    // phase hasn't persisted anything yet (see LessonSessionRepository), so there's nothing there
    // for the dashboard's "Abandon lesson session" entry, or this screen's own copy of it, to act on.
    // A batch checkpoint counts: the session is mid-plan and resumable, so it's exactly the state a
    // learner might want to throw away from here.
    val canManageSession = when (uiState.phase) {
        is LessonUiState.Phase.Study, is LessonUiState.Phase.Quiz, is LessonUiState.Phase.BatchComplete -> true
        else -> false
    }

    // Wrapping Scaffold and SubjectDetailSheetHost in a shared Box — rather than leaving them as
    // top-level siblings — is what lets the detail sheet's handle overlay the true bottom of the
    // screen and pick up real navigation-bar insets via its own navigationBarsPadding(), instead of
    // ending up laid out underneath the system nav bar/gesture area. Mirrors ReviewScreen's
    // equivalent wrapping Box.
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            CompactTopBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(LessonScreenTestTags.BACK_BUTTON)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { isSearchActive = true },
                        modifier = Modifier.testTag(LessonScreenTestTags.SEARCH_BUTTON)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                    if (canManageSession) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag(LessonScreenTestTags.OVERFLOW_MENU)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                shape = RoundedCornerShape(16.dp)
                            ) {
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
                                    modifier = Modifier.testTag(LessonScreenTestTags.ABANDON_MENU_ITEM)
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
                text = "Finished batches are kept. Lessons in the batch you're on that you haven't finished, and every batch after it, are dropped from this session — they stay available to study later.",
                confirmLabel = "Abandon",
                onConfirm = { showAbandonConfirm = false; onAbandon() },
                onDismiss = { showAbandonConfirm = false },
                confirmButtonTestTag = LessonScreenTestTags.ABANDON_CONFIRM_BUTTON
            )
        }

        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val phase = uiState.phase) {
                LessonUiState.Phase.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag(LessonScreenTestTags.LOADING_INDICATOR))
                    }
                }

                is LessonUiState.Phase.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = phase.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(LessonScreenTestTags.ERROR_TEXT)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag(LessonScreenTestTags.RETRY_BUTTON)
                        ) { Text("Retry") }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = onStudyOffline,
                            modifier = Modifier.testTag(LessonScreenTestTags.STUDY_OFFLINE_BUTTON)
                        ) { Text("Study offline with cached data") }
                    }
                }

                LessonUiState.Phase.NoLessonsAvailable -> {
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

                is LessonUiState.Phase.Complete -> {
                    SessionCompleteContent(
                        title = "Lesson complete!",
                        subtitle = "Great work. These items will start showing up in your reviews.",
                        itemsLabel = "Items learned",
                        averageLabel = "Avg. time per item learned",
                        itemsCount = phase.sessionItemsLearned,
                        correctFirstTry = phase.sessionItemsCorrectFirstTry,
                        totalElapsedMs = phase.sessionTotalElapsedMs,
                        averageTimePerItemMs = phase.sessionAverageTimePerItemMs,
                        slowestAnswers = phase.sessionSlowestAnswers.map { it.toSessionAnswerRow() },
                        missedItems = phase.sessionMissedItems.map { it.toSessionMissedItemRow() },
                        onDone = onDone,
                        onSubjectClick = { detailSheetState.show(it) },
                        testTags = SessionCompleteTestTags(
                            root = LessonScreenTestTags.SESSION_COMPLETE,
                            overviewCard = LessonScreenTestTags.SESSION_OVERVIEW_CARD,
                            itemsText = LessonScreenTestTags.ITEMS_LEARNED_TEXT,
                            correctFirstTryText = LessonScreenTestTags.CORRECT_FIRST_TRY_TEXT,
                            timingCard = LessonScreenTestTags.SESSION_TIMING_CARD,
                            totalTimeText = LessonScreenTestTags.SESSION_TOTAL_TIME_TEXT,
                            averageTimeText = LessonScreenTestTags.SESSION_AVERAGE_TIME_TEXT,
                            slowestCard = LessonScreenTestTags.SESSION_SLOWEST_CARD,
                            missedCard = LessonScreenTestTags.SESSION_MISSED_CARD,
                            doneButton = LessonScreenTestTags.DONE_BUTTON
                        )
                    )
                }

                is LessonUiState.Phase.Select -> {
                    LessonSelectionContent(
                        select = phase,
                        onToggle = onToggleLessonSelection,
                        onToggleTypeSelection = onToggleLessonTypeSelection,
                        onSortChange = onSetLessonSort,
                        onSelectFirst = onSelectFirst,
                        onSelectAll = onSelectAll,
                        onSelectNone = onSelectNone,
                        onStart = onStartSelectedLessons
                    )
                }

                is LessonUiState.Phase.Study -> {
                    LessonStudyContent(
                        study = phase,
                        settings = uiState.settings,
                        pitchAccentsBySubjectId = uiState.pitchAccentsBySubjectId,
                        onNext = onNextStudyCard,
                        onPrevious = onPreviousStudyCard,
                        onSwiped = onStudyCardSwiped,
                        onSubjectClick = { detailSheetState.show(it) }
                    )
                }

                is LessonUiState.Phase.Quiz -> {
                    // Remembered so a reading only recomposes when the check state flips, not on every
                    // unrelated uiState change (the timers tick through here), exactly as
                    // SubjectDetailSheet hands it to the detail content. Keyed on the current item too,
                    // so the lambda can't capture a previous question's item.
                    val currentItem = phase.currentItem
                    val pitchAccentCheck = remember(uiState.isCheckingPitchAccent, uiState.pitchAccentCheckFailed, currentItem) {
                        PitchAccentCheck(
                            inProgress = uiState.isCheckingPitchAccent,
                            failed = uiState.pitchAccentCheckFailed,
                            onClick = { onEvent(LessonScreenEvent.CheckPitchAccent(currentItem)) }
                        )
                    }
                    CompositionLocalProvider(LocalPitchAccentCheck provides pitchAccentCheck) {
                        QuizQuestionContent(
                            uiState = QuizQuestionUiState(
                                item = phase.currentItem,
                                questionType = phase.currentQuestionType,
                                totalCount = phase.totalQuizCount,
                                remainingCount = phase.remainingQuizCount,
                                // Which pass of the session this question belongs to: a plan of several
                                // batches needs saying out loud, or "3 / 10" reads as the whole session.
                                sessionContextLabel = when {
                                    phase.round == QuizRound.CLEANUP -> "Extra practice"
                                    phase.batchCount > 1 -> "Batch ${phase.batchIndex + 1} of ${phase.batchCount}"
                                    else -> null
                                },
                                answerInput = phase.answerInput,
                                feedback = phase.feedback,
                                rankChange = phase.rankChange,
                                undoCounter = phase.undoCounter,
                                answerTypeMismatchCount = phase.answerTypeMismatchCount,
                                showSubjectTypeLabel = uiState.settings.showSubjectTypeLabel,
                                showQuestionTimer = uiState.settings.showQuestionTimer,
                                showTotalTimer = uiState.settings.showTotalTimer,
                                questionElapsedMs = phase.timing.questionElapsedMs,
                                questionActiveElapsedMs = phase.timing.questionActiveElapsedMs,
                                questionActiveSegmentStartMs = phase.timing.questionActiveSegmentStartMs,
                                sessionActiveElapsedMs = phase.timing.sessionActiveElapsedMs,
                                sessionActiveSegmentStartMs = phase.timing.sessionActiveSegmentStartMs,
                                useJapaneseKeyboard = uiState.settings.useJapaneseKeyboard,
                                showAnswerReadingPitchAccent = uiState.settings.showAnswerReadingPitchAccent,
                                answerReading = phase.answerReading,
                                // Read from the live map rather than a copy taken at grading time — a
                                // batch's own quiz needs the same up-to-the-moment knowledge its study
                                // cards showed. An absent entry is "not checked yet".
                                answerPitchAccents = uiState.pitchAccentsBySubjectId[phase.currentItem.subjectId]
                                    ?: PitchAccentUiState.Loading,
                                answerReadingAudio = phase.answerReadingAudio
                            ),
                            onAnswerInputChange = onAnswerInputChange,
                            onSubmit = onSubmit,
                            onDontKnow = onDontKnow,
                            onContinue = onContinue,
                            onUndo = onUndo,
                            testTags = QuizQuestionTestTags(
                                progressCount = LessonScreenTestTags.QUIZ_PROGRESS_COUNT,
                                questionTimerText = LessonScreenTestTags.QUESTION_TIMER_TEXT,
                                totalTimerText = LessonScreenTestTags.TOTAL_TIMER_TEXT,
                                characters = LessonScreenTestTags.QUIZ_CHARACTERS,
                                subjectTypeLabel = LessonScreenTestTags.QUIZ_SUBJECT_TYPE_LABEL,
                                rankChangeText = LessonScreenTestTags.RANK_CHANGE_TEXT,
                                questionLabel = LessonScreenTestTags.QUESTION_LABEL,
                                answerField = LessonScreenTestTags.ANSWER_FIELD,
                                typeMismatchText = LessonScreenTestTags.TYPE_MISMATCH_TEXT,
                                dontKnowButton = LessonScreenTestTags.DONT_KNOW_BUTTON,
                                submitButton = LessonScreenTestTags.SUBMIT_BUTTON,
                                undoButton = LessonScreenTestTags.UNDO_BUTTON,
                                feedbackText = LessonScreenTestTags.FEEDBACK_TEXT,
                                answerDetailText = LessonScreenTestTags.ANSWER_DETAIL_TEXT,
                                continueButton = LessonScreenTestTags.CONTINUE_BUTTON,
                                sessionContextLabel = LessonScreenTestTags.QUIZ_SESSION_CONTEXT_LABEL
                            )
                        )
                    }
                }

                is LessonUiState.Phase.BatchComplete -> {
                    LessonBatchCompleteContent(
                        checkpoint = phase,
                        onContinue = onContinueSession,
                        onFinishForNow = onFinishForNow,
                        onPracticeMissed = onPracticeMissed,
                        onFinishSession = onFinishSession,
                        onSubjectClick = { detailSheetState.show(it) }
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
        modifier = Modifier.fillMaxSize(),
        onSubjectClick = { detailSheetState.show(it) }
    )

    SubjectDetailSheetHost(detailSheetState)

    val quizPhase = uiState.phase as? LessonUiState.Phase.Quiz
    if (quizPhase != null) {
        var lastDetailSubjectId by remember { mutableStateOf<Long?>(null) }
        var lastDetailQuestionType by remember { mutableStateOf<QuestionType?>(null) }
        lastDetailSubjectId = quizPhase.currentItem.subjectId
        lastDetailQuestionType = quizPhase.currentQuestionType

        lastDetailSubjectId?.let { subjectId ->
            lastDetailQuestionType?.let { questionType ->
                SubjectDetailSheet(
                    subjectId = subjectId,
                    active = !isSearchActive && quizPhase.feedback != null,
                    expanded = quizPhase.isDetailsExpanded,
                    onToggle = onToggleDetails,
                    onDismiss = onCloseDetails,
                    revealMode = DetailRevealMode.HIDE_UNTIL_ANSWERED,
                    isAnswered = true,
                    questionType = questionType.toDetailQuestionType(),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LessonStudyContent(
    study: LessonUiState.Phase.Study,
    settings: LessonUiState.DisplaySettings,
    pitchAccentsBySubjectId: Map<Long, PitchAccentUiState>,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSwiped: (Int) -> Unit,
    onSubjectClick: (Long) -> Unit
) {
    val currentItem = study.studyItems.getOrNull(study.studyIndex) ?: return
    val isLastCard = study.studyIndex == study.studyItems.lastIndex
    val accentColor = subjectColor(currentItem.subjectType)

    val pagerState = rememberPagerState(initialPage = study.studyIndex) { study.studyItems.size }

    LaunchedEffect(study.studyIndex) {
        if (pagerState.currentPage != study.studyIndex) {
            pagerState.animateScrollToPage(study.studyIndex)
        }
    }
    LaunchedEffect(pagerState) {
        androidx.compose.runtime.snapshotFlow { pagerState.currentPage }
            .collect { page -> onSwiped(page) }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Where the learner is in *this batch* — the bar below measures the same thing, and the quiz
        // that follows covers exactly these cards. Solid rather than variant-colored, so the count
        // dominates the row and the batch context reads as the annotation it is.
        Text(
            text = "${study.studyIndex + 1} / ${study.studyItems.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.testTag(LessonScreenTestTags.STUDY_PROGRESS_COUNT)
        )
        if (study.batchCount > 1) {
            // Which batch those cards belong to, in the same "position · context" idiom as the quiz
            // header. The session's own total is deliberately not repeated here: the picker states it
            // when it's the decision being made and the checkpoints state what's left, so a third
            // number would only crowd the one screen where the learner is reading cards rather than
            // counting them.
            Text(
                text = " · ",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Batch ${study.batchIndex + 1} of ${study.batchCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(LessonScreenTestTags.STUDY_BATCH_LABEL)
            )
        }
    }
    LinearProgressIndicator(
        progress = { (study.studyIndex + 1).toFloat() / study.studyItems.size },
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
        val item = study.studyItems[page]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val isVocabulary = item.subjectType == SubjectType.VOCABULARY || item.subjectType == SubjectType.KANA_VOCABULARY

            // Headline: the item's characters with the meaning directly underneath, then the reading,
            // then the level/type on a line of its own (labelMedium — it annotates the item, it is not
            // part of the answer), then the (vocab) part-of-speech tags. Same shape as the subject
            // detail view's headline, see SubjectDetailContent.kt. One cluster, so its parts sit at 8dp
            // from each other rather than at the page's section spacing.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectGlyph(
                    characters = item.characters,
                    characterImageUrl = item.characterImageUrl,
                    subjectType = item.subjectType,
                    size = 96.dp,
                    modifier = Modifier.testTag(LessonScreenTestTags.STUDY_CHARACTERS),
                    boxHeight = headlineGlyphBoxHeight(96.dp)
                )
                SubjectMeaningAnswer(
                    meanings = item.meanings,
                    auxiliaryMeanings = item.auxiliaryMeanings,
                    resetKey = item.subjectId
                )
                SubjectReadingAnswer(
                    subjectType = item.subjectType,
                    readings = item.readings,
                    onyomiReadings = item.onyomiReadings,
                    kunyomiReadings = item.kunyomiReadings,
                    nanoriReadings = item.nanoriReadings,
                    pronunciationAudios = item.pronunciationAudios,
                    pitchAccents = pitchAccentsBySubjectId[item.subjectId] ?: PitchAccentUiState.Loading,
                    showPitchAccent = settings.showPitchAccent,
                    restrictAudioToMp3 = settings.restrictAudioToMp3
                )
                Text(
                    text = "Level ${item.level} · ${subjectTypeLabel(item.subjectType)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isVocabulary && item.partsOfSpeech.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.partsOfSpeech.forEach { part -> AssistChip(onClick = {}, label = { Text(part) }) }
                    }
                }
            }

            val strokeOrder = study.strokeOrderBySubjectId[item.subjectId] ?: StrokeOrderUiState.Unavailable
            StrokeOrderSection(strokeOrder)
            WritingPracticeSection(strokeOrder = strokeOrder, resetKey = item.subjectId)

            RelatedSubjectsSection(
                title = componentsLabel(item.subjectType),
                subjects = item.componentSubjectIds.mapNotNull { study.relatedSubjectsById[it] },
                onSubjectClick = onSubjectClick
            )

            SubjectMnemonicZone(
                meaningMnemonic = item.meaningMnemonic,
                meaningHint = item.meaningHint,
                readingMnemonic = item.readingMnemonic,
                readingHint = item.readingHint,
                showMeaning = true,
                showReading = true
            )

            if (isVocabulary && item.contextSentences.isNotEmpty()) {
                HorizontalDivider()
                LessonContextSentencesSection(item.contextSentences, settings.hideContextSentenceTranslations)
            }
            if (item.subjectType == SubjectType.KANJI) {
                RelatedSubjectsSection(
                    title = "Visually similar",
                    subjects = item.visuallySimilarSubjectIds.mapNotNull { study.relatedSubjectsById[it] },
                    onSubjectClick = onSubjectClick
                )
            }
            RelatedSubjectsSection(
                title = "Used in",
                subjects = item.amalgamationSubjectIds.mapNotNull { study.relatedSubjectsById[it] },
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
            enabled = study.studyIndex > 0,
            modifier = Modifier.weight(1f).testTag(LessonScreenTestTags.STUDY_PREVIOUS_BUTTON)
        ) { Text("Back") }
        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .testTag(if (isLastCard) LessonScreenTestTags.START_QUIZ_BUTTON else LessonScreenTestTags.STUDY_NEXT_BUTTON)
        ) {
            Text(if (isLastCard) "Start Quiz" else "Next")
        }
    }
}

/** The batch checkpoint — the screen a session pauses at between batches, or lands on when the last
 *  batch is done and there's something worth offering. Deliberately short: a per-batch tally, the
 *  misses, and two ways forward. Anything longer (a full summary, timers, accuracy charts) would make
 *  the checkpoint feel like a hurdle rather than a breath. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LessonBatchCompleteContent(
    checkpoint: LessonUiState.Phase.BatchComplete,
    onContinue: () -> Unit,
    onFinishForNow: () -> Unit,
    onPracticeMissed: () -> Unit,
    onFinishSession: () -> Unit,
    onSubjectClick: (Long) -> Unit
) {
    val isFinalBatch = checkpoint.batchIndex == checkpoint.batchCount - 1
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag(LessonScreenTestTags.BATCH_COMPLETE),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (isFinalBatch) "Last batch done!" else "Batch ${checkpoint.batchIndex + 1} of ${checkpoint.batchCount} done!",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag(LessonScreenTestTags.BATCH_COMPLETE_HEADLINE)
        )
        Text(
            text = "${checkpoint.itemsLearned} learned · ${checkpoint.itemsCorrectFirstTry} right first try",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(LessonScreenTestTags.BATCH_COMPLETE_SUMMARY_TEXT)
        )

        if (checkpoint.missedItems.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Worth another look",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.testTag(LessonScreenTestTags.BATCH_COMPLETE_MISSED_TEXT)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    checkpoint.missedItems.forEach { item ->
                        LessonGlyphTile(
                            lessonItem = item,
                            selected = false,
                            minWidth = 56.dp,
                            minHeight = 64.dp,
                            maxWidth = 96.dp,
                            onClick = { onSubjectClick(item.subjectId) }
                        )
                    }
                }
            }
        }

        when (val next = checkpoint.next) {
            is LessonUiState.Phase.BatchComplete.NextStep.StudyBatch -> {
                Text(
                    text = "${next.remainingSessionItems} items left in this session.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Plain "Continue", not "Study next 5": the scale is the line above, and the count in
                // the label was noise on a button whose only job is to move forward.
                Button(
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().testTag(LessonScreenTestTags.CONTINUE_SESSION_BUTTON)
                ) { Text("Continue") }
                TextButton(
                    onClick = onFinishForNow,
                    modifier = Modifier.fillMaxWidth().testTag(LessonScreenTestTags.FINISH_FOR_NOW_BUTTON)
                ) { Text("Finish for now") }
                Text(
                    text = "Stopping here is fine — you can pick the session back up from the dashboard.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            is LessonUiState.Phase.BatchComplete.NextStep.PracticeMissed -> {
                Text(
                    text = "${next.itemCount} didn't stick on the first try.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onPracticeMissed,
                    modifier = Modifier.fillMaxWidth().testTag(LessonScreenTestTags.PRACTICE_MISSED_BUTTON)
                ) { Text("Practice ${next.itemCount} missed") }
                TextButton(
                    onClick = onFinishSession,
                    modifier = Modifier.fillMaxWidth().testTag(LessonScreenTestTags.FINISH_SESSION_BUTTON)
                ) { Text("See results") }
            }
        }
    }
}

/** Picks the picker's queue order. A plain clickable Row rather than a Material button, for the same
 *  reason the dashboard's forecast-window dropdown is one: a button's enforced ~40dp minimum height
 *  would inflate the count line it sits on. */
@Composable
private fun LessonSortDropdownButton(
    selectedSort: LessonSort,
    onSortChange: (LessonSort) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clickable { expanded = true }
                .testTag(LessonScreenTestTags.SORT_DROPDOWN),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sort: ${selectedSort.label}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Change lesson sort",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LessonSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = {
                        onSortChange(sort)
                        expanded = false
                    },
                    trailingIcon = if (sort == selectedSort) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null,
                    modifier = Modifier.testTag(LessonScreenTestTags.sortOptionTag(sort))
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.LessonSelectionContent(
    select: LessonUiState.Phase.Select,
    onToggle: (Long) -> Unit,
    onToggleTypeSelection: (SubjectType) -> Unit,
    onSortChange: (LessonSort) -> Unit,
    onSelectFirst: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onStart: () -> Unit
) {
    val selectedCount = select.selectedAssignmentIds.size
    val total = select.availableLessons.size
    var customizeExpanded by rememberSaveable { mutableStateOf(false) }
    var expandedLevels by rememberSaveable {
        val levelsWithSelection = select.availableLessons
            .filter { it.assignmentId in select.selectedAssignmentIds }
            .map { it.level }
            .toSet()
        mutableStateOf(levelsWithSelection)
    }

    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text("Choose lessons to study", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text("$selectedCount of $total selected", style = MaterialTheme.typography.bodyMedium)

        if (select.availableTypes.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            // Whole-type shortcuts, shown in both picker modes: one tap gets every kanji (or clears
            // them again), which is the fastest route to a kanji-only session. A chip is filled in
            // only while *all* of that type is selected, so a partial selection reads as "tap to
            // complete" rather than as done.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                select.availableTypes.forEach { type ->
                    val allSelected = select.isTypeFullySelected(type)
                    FilterChip(
                        selected = allSelected,
                        onClick = { onToggleTypeSelection(type) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = if (allSelected) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
                        label = { Text("${subjectTypeLabel(type)} · ${select.countOfType(type)}") },
                        modifier = Modifier.testTag(LessonScreenTestTags.typeSelectorChipTag(type))
                    )
                }
            }
        }

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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
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
                // Order only means anything once the queue mixes types — an all-kanji queue sorts to
                // itself either way, so the control would just be noise.
                if (select.availableTypes.size > 1) {
                    LessonSortDropdownButton(selectedSort = select.sort, onSortChange = onSortChange)
                }
            }
        }
    }

    if (customizeExpanded) {
        val lessonsByLevel = remember(select.availableLessons) { select.availableLessons.groupBy { it.level } }
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            lessonsByLevel.forEach { (level, itemsForLevel) ->
                val levelExpanded = level in expandedLevels
                val selectedInLevel = itemsForLevel.count { it.assignmentId in select.selectedAssignmentIds }
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
                                val checked = lessonItem.assignmentId in select.selectedAssignmentIds
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
                JapaneseText(
                    text = furigana,
                    style = MaterialTheme.typography.labelSmall.copy(textAlign = TextAlign.Center),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SubjectGlyph(
                characters = lessonItem.characters,
                characterImageUrl = lessonItem.characterImageUrl,
                subjectType = lessonItem.subjectType,
                color = contentColor,
                fallbackText = lessonItem.meanings.firstOrNull() ?: "?",
                size = 40.dp
            )
        }
    }
}

@Composable
private fun LessonContextSentencesSection(sentences: List<ContextSentence>, hideTranslations: Boolean) {
    val shareText = rememberShareText()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Context sentences")
        AkebiSelectableContainer {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                sentences.forEach { sentence ->
                    ContextSentenceRow(sentence, onShare = shareText, hideTranslation = hideTranslations)
                }
            }
        }
    }
}
