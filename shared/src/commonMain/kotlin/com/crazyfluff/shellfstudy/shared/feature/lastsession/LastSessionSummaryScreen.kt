package com.crazyfluff.shellfstudy.shared.feature.lastsession

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.koin.compose.viewmodel.koinViewModel
import com.crazyfluff.shellfstudy.shared.data.LastSessionKind
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummary
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionCompleteContent
import com.crazyfluff.shellfstudy.shared.designsystem.quiz.SessionCompleteTestTags
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheetHost
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.rememberSubjectDetailSheetState
import com.crazyfluff.shellfstudy.shared.util.formatRelativeTime

object LastSessionSummaryScreenTestTags {
    const val LOADING_INDICATOR = "last_session_summary_loading_indicator"
    const val EMPTY_TEXT = "last_session_summary_empty_text"
    const val SESSION_COMPLETE = "last_session_summary_session_complete"
    const val SESSION_OVERVIEW_CARD = "last_session_summary_overview_card"
    const val ITEMS_TEXT = "last_session_summary_items_text"
    const val CORRECT_FIRST_TRY_TEXT = "last_session_summary_correct_first_try_text"
    const val SESSION_TIMING_CARD = "last_session_summary_timing_card"
    const val SESSION_TOTAL_TIME_TEXT = "last_session_summary_total_time_text"
    const val SESSION_AVERAGE_TIME_TEXT = "last_session_summary_average_time_text"
    const val SESSION_SLOWEST_CARD = "last_session_summary_slowest_card"
    const val SESSION_MISSED_CARD = "last_session_summary_missed_card"
    const val DONE_BUTTON = "last_session_summary_done_button"
}

@Composable
fun LastSessionSummaryRoute(
    onBack: () -> Unit,
    viewModel: LastSessionSummaryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LastSessionSummaryScreen(uiState = uiState, onBack = onBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastSessionSummaryScreen(
    uiState: LastSessionSummaryUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val detailSheetState = rememberSubjectDetailSheetState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = { Text("Last session") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { innerPadding ->
            val summary = uiState.summary
            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(modifier = Modifier.testTag(LastSessionSummaryScreenTestTags.LOADING_INDICATOR))
                    }
                }

                summary == null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No recent session to show.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(LastSessionSummaryScreenTestTags.EMPTY_TEXT)
                        )
                    }
                }

                else -> {
                    SessionCompleteContent(
                        title = when (summary.kind) {
                            LastSessionKind.LESSON -> "Last lesson session"
                            LastSessionKind.REVIEW -> "Last review session"
                        },
                        subtitle = "Completed ${formatRelativeTime(summary.completedAtMillis)}.",
                        itemsLabel = summary.itemsLabel(),
                        averageLabel = summary.averageLabel(),
                        itemsCount = summary.itemsCount,
                        correctFirstTry = summary.correctFirstTry,
                        totalElapsedMs = summary.totalElapsedMs,
                        averageTimePerItemMs = summary.averageTimePerItemMs,
                        slowestAnswers = summary.slowestAnswers,
                        missedItems = summary.missedItems,
                        onDone = onBack,
                        onSubjectClick = { detailSheetState.show(it) },
                        testTags = SessionCompleteTestTags(
                            root = LastSessionSummaryScreenTestTags.SESSION_COMPLETE,
                            overviewCard = LastSessionSummaryScreenTestTags.SESSION_OVERVIEW_CARD,
                            itemsText = LastSessionSummaryScreenTestTags.ITEMS_TEXT,
                            correctFirstTryText = LastSessionSummaryScreenTestTags.CORRECT_FIRST_TRY_TEXT,
                            timingCard = LastSessionSummaryScreenTestTags.SESSION_TIMING_CARD,
                            totalTimeText = LastSessionSummaryScreenTestTags.SESSION_TOTAL_TIME_TEXT,
                            averageTimeText = LastSessionSummaryScreenTestTags.SESSION_AVERAGE_TIME_TEXT,
                            slowestCard = LastSessionSummaryScreenTestTags.SESSION_SLOWEST_CARD,
                            missedCard = LastSessionSummaryScreenTestTags.SESSION_MISSED_CARD,
                            doneButton = LastSessionSummaryScreenTestTags.DONE_BUTTON
                        ),
                        modifier = Modifier.fillMaxSize().padding(innerPadding)
                    )
                }
            }
        }
    }

    SubjectDetailSheetHost(detailSheetState)
}

private fun LastSessionSummary.itemsLabel(): String = when (kind) {
    LastSessionKind.LESSON -> "Items learned"
    LastSessionKind.REVIEW -> "Items reviewed"
}

private fun LastSessionSummary.averageLabel(): String = when (kind) {
    LastSessionKind.LESSON -> "Avg. time per item learned"
    LastSessionKind.REVIEW -> "Avg. time per item reviewed"
}
