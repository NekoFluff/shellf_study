package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.feature.search.SubjectSearchOverlay

object DashboardScreenTestTags {
    const val LOADING_INDICATOR = "dashboard_loading_indicator"
    const val ERROR_TEXT = "dashboard_error_text"
    const val LESSON_COUNT = "dashboard_lesson_count"
    const val REVIEW_COUNT = "dashboard_review_count"
    const val LOG_OUT_BUTTON = "dashboard_log_out_button"
    const val RETRY_BUTTON = "dashboard_retry_button"
    const val SEARCH_BUTTON = "dashboard_search_button"
    const val OVERFLOW_MENU = "dashboard_overflow_menu"
    const val SETTINGS_BUTTON = "dashboard_settings_button"
    const val LESSONS_TODAY_PROGRESS = "dashboard_lessons_today_progress"
    const val GURU_PROGRESS = "dashboard_guru_progress"
}

@Composable
fun DashboardRoute(
    onStartReview: () -> Unit,
    onStartLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }

    DashboardScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onStartReview = onStartReview,
        onStartLesson = onStartLesson,
        onOpenSettings = onOpenSettings,
        onLogOut = viewModel::logOut,
        searchUiState = searchUiState,
        onSearchQueryChange = searchViewModel::onQueryChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onStartReview: () -> Unit,
    onLogOut: () -> Unit,
    onStartLesson: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    searchUiState: SearchUiState = SearchUiState(),
    onSearchQueryChange: (String) -> Unit = {}
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // No literal wordmark title here on purpose — a minimal, icon-only action row
                // reads cleaner and keeps the header from competing with the welcome message
                // below it.
                TopAppBar(
                    title = {},
                    actions = {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag(DashboardScreenTestTags.SEARCH_BUTTON)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag(DashboardScreenTestTags.OVERFLOW_MENU)
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { menuExpanded = false; onOpenSettings() },
                                modifier = Modifier.testTag(DashboardScreenTestTags.SETTINGS_BUTTON)
                            )
                            DropdownMenuItem(
                                text = { Text("Log out") },
                                onClick = { menuExpanded = false; onLogOut() },
                                modifier = Modifier.testTag(DashboardScreenTestTags.LOG_OUT_BUTTON)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .testTag(DashboardScreenTestTags.LOADING_INDICATOR)
                        )
                    }

                    uiState.errorMessage != null -> {
                        Text(
                            text = uiState.errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag(DashboardScreenTestTags.ERROR_TEXT)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = onRefresh,
                            modifier = Modifier.testTag(DashboardScreenTestTags.RETRY_BUTTON)
                        ) {
                            Text("Retry")
                        }
                    }

                    else -> {
                        Text(
                            text = "Welcome back, ${uiState.username}!",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = buildString {
                                append("Level ${uiState.level}")
                                uiState.daysOnCurrentLevel?.let { append(" · Day $it") }
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )

                        if (uiState.kanjiTotalForLevelUp > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val guruProgress =
                                (uiState.kanjiGuruedForLevelUp.toFloat() / uiState.kanjiTotalForLevelUp)
                                    .coerceIn(0f, 1f)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(DashboardScreenTestTags.GURU_PROGRESS)
                            ) {
                                Text(
                                    text = "${uiState.kanjiGuruedForLevelUp} / ${uiState.kanjiTotalForLevelUp} kanji guru'd",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { guruProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = SrsStageColors.Guru
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            SummaryCard(
                                label = "Lessons",
                                count = uiState.lessonCount,
                                color = MaterialTheme.colorScheme.tertiary,
                                onClick = onStartLesson,
                                badge = {
                                    LessonsTodayBadge(
                                        completed = uiState.lessonsCompletedToday,
                                        goal = uiState.dailyLessonGoal
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(DashboardScreenTestTags.LESSON_COUNT)
                            )
                            SummaryCard(
                                label = if (uiState.hasActiveReviewSession) "Resume Session" else "Reviews",
                                count = uiState.reviewCount,
                                color = MaterialTheme.colorScheme.secondary,
                                onClick = onStartReview,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(DashboardScreenTestTags.REVIEW_COUNT)
                            )
                        }
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
private fun SummaryCard(
    label: String,
    count: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color, contentColor = Color.White)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = count.toString(), style = MaterialTheme.typography.displayLarge)
                Text(text = label, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
            if (badge != null) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    badge()
                }
            }
        }
    }
}

/** Small ring showing progress toward the daily lesson goal, tucked in a card's corner. */
@Composable
private fun LessonsTodayBadge(completed: Int, goal: Int) {
    val progress = (completed.toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .semantics { contentDescription = "$completed of $goal lessons done today" }
            .testTag(DashboardScreenTestTags.LESSONS_TODAY_PROGRESS)
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 3.dp,
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        Text(text = completed.toString(), style = MaterialTheme.typography.labelSmall)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    ShellfStudyTheme {
        DashboardScreen(
            uiState = DashboardUiState(
                isLoading = false,
                username = "durtle_fan",
                level = 12,
                lessonCount = 5,
                reviewCount = 23,
                lessonsCompletedToday = 3,
                dailyLessonGoal = 15,
                kanjiGuruedForLevelUp = 18,
                kanjiTotalForLevelUp = 25,
                daysOnCurrentLevel = 6
            ),
            onRefresh = {},
            onStartReview = {},
            onLogOut = {}
        )
    }
}
