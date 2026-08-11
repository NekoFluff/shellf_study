package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.designsystem.subjectdetail.DetailRevealMode
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SrsStageColors
import com.crazyfluff.shellfstudy.core.designsystem.theme.SubjectTypeColors
import com.crazyfluff.shellfstudy.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.feature.search.SubjectSearchOverlay
import com.crazyfluff.shellfstudy.feature.subjectdetail.SubjectDetailSheet

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

    // Compose Navigation tears this Route composable down when navigating to Review/Lesson and
    // rebuilds it on the way back (the ViewModel instance itself survives via the nav-graph-scoped
    // ViewModelStore, but its init{} doesn't rerun) — so this is what actually catches "returning
    // to the dashboard" and refreshes lesson/review counts, without needing a lifecycle observer.
    LaunchedEffect(Unit) {
        viewModel.onDashboardResumed()
    }

    DashboardScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onStartReview = onStartReview,
        onStartLesson = onStartLesson,
        onOpenSettings = onOpenSettings,
        onLogOut = viewModel::logOut,
        searchUiState = searchUiState,
        onSearchQueryChange = searchViewModel::onQueryChange,
        onLevelProgressLevelChange = viewModel::onLevelProgressLevelChange
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
    onSearchQueryChange: (String) -> Unit = {},
    onLevelProgressLevelChange: (Int) -> Unit = {}
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var detailSubjectId by remember { mutableStateOf<Long?>(null) }

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
            PullToRefreshBox(
                isRefreshing = uiState.isLoading,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when {
                        uiState.isLoading -> {
                            DashboardLoadingSkeleton(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                                        color = SrsStageColors.Guru,
                                        drawStopIndicator = {}
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SummaryCard(
                                    label = "Lessons",
                                    count = uiState.lessonCount,
                                    // Fixed brand color rather than MaterialTheme.colorScheme.tertiary:
                                    // the dark color scheme maps tertiary to a pale tint meant for
                                    // small accents, not a full-bleed card fill — with white text on
                                    // top that read as washed out. This card should look the same
                                    // vivid blue in both themes.
                                    color = SubjectTypeColors.Radical,
                                    onClick = onStartLesson,
                                    badge = {
                                        LessonsTodayBadge(
                                            completed = uiState.lessonsCompletedToday,
                                            goal = uiState.dailyLessonGoal
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag(DashboardScreenTestTags.LESSON_COUNT)
                                )
                                SummaryCard(
                                    // Kept to one short word — "Resume Session" wrapped to two lines in
                                    // this half-width card, growing it taller than the "Lessons" card
                                    // next to it (each Card sizes to its own content by default).
                                    label = if (uiState.hasActiveReviewSession) "Resume" else "Reviews",
                                    count = uiState.reviewCount,
                                    color = SubjectTypeColors.Kanji,
                                    onClick = onStartReview,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .testTag(DashboardScreenTestTags.REVIEW_COUNT)
                                )
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            ReviewForecastCard(forecast = uiState.reviewForecast, modifier = Modifier.fillMaxWidth())

                            if (uiState.levelProgress != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                LevelProgressCard(
                                    progress = uiState.levelProgress,
                                    maxLevel = uiState.level,
                                    onLevelChange = onLevelProgressLevelChange,
                                    onSubjectClick = { detailSubjectId = it },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (uiState.completionProjection != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                CompletionProjectionCard(
                                    projection = uiState.completionProjection,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            ItemSpreadCard(spread = uiState.itemSpread, modifier = Modifier.fillMaxWidth())
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
            modifier = Modifier.fillMaxSize(),
            onSubjectClick = { detailSubjectId = it }
        )
    }

    detailSubjectId?.let { id ->
        SubjectDetailSheet(
            initialSubjectId = id,
            revealMode = DetailRevealMode.FULL,
            isAnswered = true,
            questionType = null,
            onDismiss = { detailSubjectId = null }
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = count.toString(), style = MaterialTheme.typography.displayLarge)
                Text(text = label, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
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
            .size(22.dp)
            .semantics { contentDescription = "$completed of $goal lessons done today" }
            .testTag(DashboardScreenTestTags.LESSONS_TODAY_PROGRESS)
    ) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            strokeWidth = 2.dp,
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        Text(text = completed.toString(), style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
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
