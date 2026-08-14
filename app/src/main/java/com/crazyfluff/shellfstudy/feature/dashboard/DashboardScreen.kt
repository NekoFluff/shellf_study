package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.designsystem.components.CompactTopBar
import com.crazyfluff.shellfstudy.core.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.core.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.core.designsystem.theme.SubjectTypeColors
import com.crazyfluff.shellfstudy.core.notifications.NotificationDeepLink
import com.crazyfluff.shellfstudy.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.feature.search.SubjectSearchOverlay
import com.crazyfluff.shellfstudy.feature.subjectdetail.SubjectDetailSheetHost
import com.crazyfluff.shellfstudy.feature.subjectdetail.rememberSubjectDetailSheetState

object DashboardScreenTestTags {
    const val LOADING_INDICATOR = "dashboard_loading_indicator"
    const val REFRESHING_BANNER = "dashboard_refreshing_banner"
    const val OFFLINE_BANNER = "dashboard_offline_banner"
    const val SYNC_BLOCKED_BANNER = "dashboard_sync_blocked_banner"
    const val PENDING_SYNC_BANNER = "dashboard_pending_sync_banner"
    const val ERROR_TEXT = "dashboard_error_text"
    const val LESSON_COUNT = "dashboard_lesson_count"
    const val REVIEW_COUNT = "dashboard_review_count"
    const val LOG_OUT_BUTTON = "dashboard_log_out_button"
    const val RETRY_BUTTON = "dashboard_retry_button"
    const val SEARCH_BUTTON = "dashboard_search_button"
    const val OVERFLOW_MENU = "dashboard_overflow_menu"
    const val SETTINGS_BUTTON = "dashboard_settings_button"
    const val LESSONS_TODAY_PROGRESS = "dashboard_lessons_today_progress"
    const val ABANDON_REVIEW_MENU_ITEM = "dashboard_abandon_review_menu_item"
    const val ABANDON_LESSON_MENU_ITEM = "dashboard_abandon_lesson_menu_item"
    const val ABANDON_REVIEW_CONFIRM_BUTTON = "dashboard_abandon_review_confirm_button"
    const val ABANDON_LESSON_CONFIRM_BUTTON = "dashboard_abandon_lesson_confirm_button"
}

@Composable
fun DashboardRoute(
    onStartReview: () -> Unit,
    onStartLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    onLoggedOut: () -> Unit,
    pendingDestination: String? = null,
    onPendingDestinationConsumed: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
    searchViewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isLoggedOut) {
        if (uiState.isLoggedOut) onLoggedOut()
    }

    // Compose Navigation tears this Route composable down when navigating to Review/Lesson and
    // rebuilds it on the way back (the ViewModel instance itself survives via the nav-graph-scoped
    // ViewModelStore, but its init{} doesn't rerun) — so this is what actually catches "returning
    // to the dashboard" and refreshes lesson/review counts, without needing a lifecycle observer.
    // It's also what catches the very first appearance (cold start / post-login), which is why
    // onDashboardResumed() itself forces a full sync the first time it's called.
    LaunchedEffect(Unit) {
        viewModel.onDashboardResumed()
    }

    // Tapping the daily study-reminder notification while already sitting on the dashboard
    // wouldn't otherwise trigger anything — unlike Review/Lesson, navigating there doesn't create
    // a fresh ViewModel, so there's no init{} to piggyback on. This is the explicit fallback.
    LaunchedEffect(pendingDestination) {
        if (pendingDestination == NotificationDeepLink.DESTINATION_DASHBOARD) {
            viewModel.refresh()
            onPendingDestinationConsumed()
        }
    }

    DashboardScreen(
        uiState = uiState,
        onRefresh = viewModel::refresh,
        onStartReview = onStartReview,
        onStartLesson = onStartLesson,
        onOpenSettings = onOpenSettings,
        onLogOut = viewModel::logOut,
        onAbandonReviewSession = viewModel::abandonReviewSession,
        onAbandonLessonSession = viewModel::abandonLessonSession,
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
    onAbandonReviewSession: () -> Unit = {},
    onAbandonLessonSession: () -> Unit = {},
    searchUiState: SearchUiState = SearchUiState(),
    onSearchQueryChange: (String) -> Unit = {},
    onLevelProgressLevelChange: (Int) -> Unit = {}
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbandonReviewConfirm by remember { mutableStateOf(false) }
    var showAbandonLessonConfirm by remember { mutableStateOf(false) }
    val detailSheetState = rememberSubjectDetailSheetState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // No literal wordmark title here on purpose — a minimal, icon-only action row
                // reads cleaner and keeps the header from competing with the welcome message
                // below it. CompactTopBar (not the stock TopAppBar) so the empty title doesn't
                // reserve a fixed ~64dp band of dead space above that welcome message.
                CompactTopBar(
                    actions = {
                        IconButton(
                            onClick = { isSearchActive = true },
                            modifier = Modifier.testTag(DashboardScreenTestTags.SEARCH_BUTTON)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag(DashboardScreenTestTags.OVERFLOW_MENU)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (uiState.hasActiveReviewSession) {
                                    DropdownMenuItem(
                                        text = { Text("Abandon review session", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = { menuExpanded = false; showAbandonReviewConfirm = true },
                                        modifier = Modifier.testTag(DashboardScreenTestTags.ABANDON_REVIEW_MENU_ITEM)
                                    )
                                    HorizontalDivider()
                                }
                                if (uiState.hasActiveLessonSession) {
                                    DropdownMenuItem(
                                        text = { Text("Abandon lesson session", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        },
                                        onClick = { menuExpanded = false; showAbandonLessonConfirm = true },
                                        modifier = Modifier.testTag(DashboardScreenTestTags.ABANDON_LESSON_MENU_ITEM)
                                    )
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text("Settings") },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                    onClick = { menuExpanded = false; onOpenSettings() },
                                    modifier = Modifier.testTag(DashboardScreenTestTags.SETTINGS_BUTTON)
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Log out", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Logout,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = { menuExpanded = false; onLogOut() },
                                    modifier = Modifier.testTag(DashboardScreenTestTags.LOG_OUT_BUTTON)
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            PullToRefreshBox(
                // Hardcoded rather than bound to uiState.isRefreshing: the drag-follow arrow
                // (state.distanceFraction) works regardless of this flag and always snaps away on
                // release, but wiring the real refresh state here would additionally re-pin it as a
                // spinner for the whole refresh — the status banner below is that signal instead.
                isRefreshing = false,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 24.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    when (val contentState = uiState.contentState) {
                        // Nothing cached yet to show while the very first fetch is in flight — the
                        // only case that still blocks on a full-screen placeholder.
                        DashboardContentState.Loading -> {
                            DashboardLoadingSkeleton(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(DashboardScreenTestTags.LOADING_INDICATOR)
                            )
                        }

                        is DashboardContentState.FullScreenError -> {
                            Text(
                                text = contentState.message,
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

                        DashboardContentState.Content -> {
                            DashboardStatusBanner(
                                bannerState = uiState.bannerState,
                                onRetry = onRefresh
                            )
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

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SummaryCard(
                                    // Kept to one short word — see the matching comment on the
                                    // Reviews card below; the same wrap-height concern applies here.
                                    label = if (uiState.hasActiveLessonSession) "Resume" else "Lessons",
                                    count = uiState.lessonCount,
                                    // Fixed brand color rather than MaterialTheme.colorScheme.tertiary:
                                    // the dark color scheme maps tertiary to a pale tint meant for
                                    // small accents, not a full-bleed card fill — with white text on
                                    // top that read as washed out. This card should look the same
                                    // vivid blue in both themes.
                                    color = SubjectTypeColors.Radical,
                                    onClick = onStartLesson,
                                    enabled = uiState.isLessonsCardEnabled,
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
                                    enabled = uiState.isReviewsCardEnabled,
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
                                    levelUpProgress = uiState.levelUpProgress,
                                    onLevelChange = onLevelProgressLevelChange,
                                    onSubjectClick = { detailSheetState.show(it) },
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
            onSubjectClick = { detailSheetState.show(it) }
        )

        if (showAbandonReviewConfirm) {
            ConfirmationDialog(
                title = "Abandon review session?",
                text = "Progress on reviews you haven't finished yet will be lost. This won't affect items you've already submitted.",
                confirmLabel = "Abandon",
                onConfirm = { showAbandonReviewConfirm = false; onAbandonReviewSession() },
                onDismiss = { showAbandonReviewConfirm = false },
                confirmButtonTestTag = DashboardScreenTestTags.ABANDON_REVIEW_CONFIRM_BUTTON
            )
        }
        if (showAbandonLessonConfirm) {
            ConfirmationDialog(
                title = "Abandon lesson session?",
                text = "Progress on the lessons you haven't finished quizzing yet will be lost. Lessons you've already completed won't be affected.",
                confirmLabel = "Abandon",
                onConfirm = { showAbandonLessonConfirm = false; onAbandonLessonSession() },
                onDismiss = { showAbandonLessonConfirm = false },
                confirmButtonTestTag = DashboardScreenTestTags.ABANDON_LESSON_CONFIRM_BUTTON
            )
        }
    }

    SubjectDetailSheetHost(detailSheetState)
}

@Composable
private fun SummaryCard(
    label: String,
    count: Int,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = if (enabled) {
            CardDefaults.cardColors(containerColor = color, contentColor = Color.White)
        } else {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
            if (enabled) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            if (badge != null) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    badge()
                }
            }
        }
    }
}

/**
 * Sits above the welcome message instead of blocking the screen: a slim "Refreshing…" bar while a
 * sync is in flight, an offline notice (with the age of the data on screen and a tap-to-retry)
 * when the last attempt failed but there's still content to show, or a note about queued
 * reviews/lessons waiting to sync in the background. Renders nothing the rest of the time, so it
 * takes up no space when the dashboard is idle and up to date. At most one banner shows at a time,
 * in priority order: sync-blocked-on-auth (needs the user to act) > offline (connectivity) >
 * pending-sync-count (informational, expected offline-first behavior) > refreshing.
 */
@Composable
private fun DashboardStatusBanner(bannerState: DashboardBannerState, onRetry: () -> Unit) {
    when (bannerState) {
        DashboardBannerState.None -> Unit

        DashboardBannerState.SyncBlockedOnAuth -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag(DashboardScreenTestTags.SYNC_BLOCKED_BANNER),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync paused — check your API token.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        is DashboardBannerState.Offline -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag(DashboardScreenTestTags.OFFLINE_BANNER),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "You're offline — showing data from ${formatRelativeSyncTime(bannerState.lastSyncedAtMillis)}. Tap to retry.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        is DashboardBannerState.PendingSync -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag(DashboardScreenTestTags.PENDING_SYNC_BANNER),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val noun = if (bannerState.count == 1) "item" else "items"
                Text(
                    text = "${bannerState.count} $noun waiting to sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        DashboardBannerState.Refreshing -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag(DashboardScreenTestTags.REFRESHING_BANNER),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Refreshing…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun formatRelativeSyncTime(lastSyncedAtMillis: Long?): String {
    if (lastSyncedAtMillis == null) return "an earlier sync"
    val minutesAgo = (System.currentTimeMillis() - lastSyncedAtMillis).coerceAtLeast(0) / 60_000
    return when {
        minutesAgo < 1 -> "just now"
        minutesAgo < 60 -> "$minutesAgo minute${if (minutesAgo == 1L) "" else "s"} ago"
        minutesAgo < 60 * 24 -> {
            val hoursAgo = minutesAgo / 60
            "$hoursAgo hour${if (hoursAgo == 1L) "" else "s"} ago"
        }
        else -> {
            val daysAgo = minutesAgo / (60 * 24)
            "$daysAgo day${if (daysAgo == 1L) "" else "s"} ago"
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
                isRefreshing = false,
                username = "durtle_fan",
                level = 12,
                lessonCount = 5,
                reviewCount = 23,
                lessonsCompletedToday = 3,
                dailyLessonGoal = 15,
                levelUpProgress = LevelUpProgress(kanjiGuruedOrHigher = 18, kanjiTotal = 25),
                daysOnCurrentLevel = 6
            ),
            onRefresh = {},
            onStartReview = {},
            onLogOut = {}
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun DashboardScreenAllCaughtUpPreview() {
    ShellfStudyTheme {
        DashboardScreen(
            uiState = DashboardUiState(
                isRefreshing = false,
                username = "durtle_fan",
                level = 12,
                lessonCount = 0,
                reviewCount = 0,
                lessonsCompletedToday = 3,
                dailyLessonGoal = 15,
                levelUpProgress = LevelUpProgress(kanjiGuruedOrHigher = 18, kanjiTotal = 25),
                daysOnCurrentLevel = 6
            ),
            onRefresh = {},
            onStartReview = {},
            onLogOut = {}
        )
    }
}
