package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.History
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
import org.koin.compose.viewmodel.koinViewModel
import com.crazyfluff.shellfstudy.shared.designsystem.performance.JANK_STATE_SCREEN
import com.crazyfluff.shellfstudy.shared.designsystem.performance.TimedComposition
import com.crazyfluff.shellfstudy.shared.designsystem.performance.ReportJankState
import com.crazyfluff.shellfstudy.shared.designsystem.components.AbandonSessionMenuItem
import com.crazyfluff.shellfstudy.shared.designsystem.components.CompactTopBar
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.shared.designsystem.theme.kanjiColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.radicalColor
import com.crazyfluff.shellfstudy.shared.notifications.NotificationDeepLink
import com.crazyfluff.shellfstudy.shared.feature.search.SearchUiState
import com.crazyfluff.shellfstudy.shared.feature.search.SearchViewModel
import com.crazyfluff.shellfstudy.shared.feature.search.SubjectSearchOverlay
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastColorMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastWindow
import com.crazyfluff.shellfstudy.shared.util.formatRelativeTime

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
    const val LAST_SESSION_SUMMARY_MENU_ITEM = "dashboard_last_session_summary_menu_item"
}

/** Bundles [DashboardScreen]'s callback lambdas into one param — the screen otherwise ends up with
 *  a flat dozen-parameter list, most of which are only wired by [DashboardRoute].
 *
 *  Every field is required, deliberately. Twelve of them used to default to `{}`, which meant a new
 *  affordance could be added here and silently left unwired by [DashboardRoute] — the screen would
 *  render a tappable control that did nothing, and only a hand-written test would notice. With no
 *  defaults, adding a field breaks the route's compilation and forces the wiring decision. Tests get
 *  their ergonomics back from `dashboardCallbacks()` in the test fakes, where "silent" is the point. */
data class DashboardCallbacks(
    val onRefresh: () -> Unit,
    val onStartReview: () -> Unit,
    val onLogOut: () -> Unit,
    val onStartLesson: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenLeaderboard: () -> Unit,
    val onOpenLastSessionSummary: () -> Unit,
    val onAbandonReviewSession: () -> Unit,
    val onAbandonLessonSession: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onLevelProgressLevelChange: (Int) -> Unit,
    val onLeaderboardMetricChange: (LeaderboardMetric) -> Unit,
    val onLeaderboardWindowChange: (LeaderboardWindow) -> Unit,
    val onReviewForecastWindowChange: (ReviewForecastWindow) -> Unit,
    val onReviewForecastColorModeChange: (ReviewForecastColorMode) -> Unit
)

@Composable
fun DashboardRoute(
    onStartReview: () -> Unit,
    onStartLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenLastSessionSummary: () -> Unit,
    onLoggedOut: () -> Unit,
    pendingDestination: String? = null,
    onPendingDestinationConsumed: () -> Unit = {},
    viewModel: DashboardViewModel = koinViewModel(),
    searchViewModel: SearchViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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

    // Which of the dashboard's expensive pieces are on screen, reported to the jank harness so a
    // stalled frame says what it was made of. The dashboard is the screen every "it feels slow"
    // report is about, and "dashboard" alone is too coarse to act on: the two Canvas charts and the
    // paged level grid are the parts that cost anything, and which of them was mounted when a frame
    // blew its budget is the whole question.
    //
    // Reported as presence flags rather than sizes, so the tag values are low-cardinality and two
    // log lines are directly comparable. Cheap to compute and stable between emissions, so an idle
    // dashboard does not re-enter the tracker frame after frame — see ReportJankState's keying.
    ReportJankState(
        JANK_STATE_SCREEN to "dashboard",
        "data" to if (uiState.username == null) "none" else "cached",
        "content" to when (uiState.contentState) {
            is DashboardContentState.Loading -> "loading"
            is DashboardContentState.FullScreenError -> "error"
            DashboardContentState.Content -> "content"
        },
        "fetch" to when (uiState.fetchState) {
            DashboardFetch.InFlight -> "inflight"
            DashboardFetch.Idle -> "idle"
            DashboardFetch.Stale -> "stale"
            is DashboardFetch.Failed -> "failed"
        },
        "forecast" to if (uiState.reviewForecast == null) "none" else "loaded",
        "levelProgress" to if (uiState.levelProgress == null) "none" else "loaded",
        "leaderboard" to if (uiState.leaderboard == null) "none" else "loaded",
        "session" to if (uiState.hasActiveReviewSession || uiState.hasActiveLessonSession) "active" else "none"
    )

    DashboardWithSearch(
        callbacks = rememberDashboardCallbacks(
            viewModel = viewModel,
            searchViewModel = searchViewModel,
            onStartReview = onStartReview,
            onStartLesson = onStartLesson,
            onOpenSettings = onOpenSettings,
            onOpenLeaderboard = onOpenLeaderboard,
            onOpenLastSessionSummary = onOpenLastSessionSummary,
            onLogOut = onLoggedOut
        ),
        uiState = uiState,
        searchViewModel = searchViewModel
    )
}

/**
 * Collects the search overlay's state *below* the dashboard, so a keystroke recomposes only the
 * overlay.
 *
 * Collecting [SearchViewModel.uiState] up in [DashboardRoute] put the whole dashboard inside the
 * reading scope of a value that changes on every character typed. The overlay is an opaque
 * full-screen layer, so the dashboard underneath is invisible while that happens — yet each
 * keystroke re-ran the entire screen (both Canvas charts, the level chip grid) to produce pixels
 * nobody could see.
 *
 * Taking [uiState] as a parameter rather than re-collecting it here keeps the dashboard's own
 * recomposition scope in [DashboardRoute], where the ViewModel lives. [callbacks] is the remembered
 * bundle from [rememberDashboardCallbacks], so passing it through this layer stays referentially
 * stable and the dashboard still skips when search state changes.
 */
@Composable
private fun DashboardWithSearch(
    callbacks: DashboardCallbacks,
    uiState: DashboardUiState,
    searchViewModel: SearchViewModel
) {
    val searchUiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    DashboardScreen(uiState = uiState, callbacks = callbacks, searchUiState = searchUiState)
}

/**
 * Builds the screen's callback bundle once per distinct set of inputs instead of once per
 * recomposition.
 *
 * A fresh [DashboardCallbacks] each time is not just an allocation: the bundle is compared by
 * `equals`, and its function fields are distinct instances each construction, so a new bundle is
 * never equal to the previous one. That made the bundle a permanently-changing parameter and
 * defeated skipping for every card that receives it — the reported symptom was the whole dashboard
 * re-executing (both Canvas charts, the level chip grid) on state changes that only affected one
 * card, or on none at all.
 *
 * The navigation lambdas are captured deliberately. They are values from
 * [ShellfStudyNavHost][com.crazyfluff.shellfstudy.shared.navigation.ShellfStudyNavHost]'s call sites,
 * which close over the same `NavHostController` for the life of the graph, so holding the first one
 * resolved cannot call into anything stale. The two ViewModels are stable across recomposition for
 * the same reason — the destination's `ViewModelStore` outlives its composition.
 */
@Composable
private fun rememberDashboardCallbacks(
    viewModel: DashboardViewModel,
    searchViewModel: SearchViewModel,
    onStartReview: () -> Unit,
    onStartLesson: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenLastSessionSummary: () -> Unit,
    onLogOut: () -> Unit
): DashboardCallbacks = remember(
    viewModel,
    searchViewModel,
    onStartReview,
    onStartLesson,
    onOpenSettings,
    onOpenLeaderboard,
    onOpenLastSessionSummary,
    onLogOut
) {
    DashboardCallbacks(
        onRefresh = viewModel::refresh,
        onStartReview = onStartReview,
        onStartLesson = onStartLesson,
        onOpenSettings = onOpenSettings,
        onOpenLeaderboard = onOpenLeaderboard,
        onOpenLastSessionSummary = onOpenLastSessionSummary,
        onLogOut = viewModel::logOut,
        onAbandonReviewSession = viewModel::abandonReviewSession,
        onAbandonLessonSession = viewModel::abandonLessonSession,
        onSearchQueryChange = searchViewModel::onQueryChange,
        onLevelProgressLevelChange = viewModel::onLevelProgressLevelChange,
        onLeaderboardMetricChange = viewModel::onLeaderboardMetricChange,
        onLeaderboardWindowChange = viewModel::onLeaderboardWindowChange,
        onReviewForecastWindowChange = viewModel::onReviewForecastWindowChange,
        onReviewForecastColorModeChange = viewModel::onReviewForecastColorModeChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: DashboardUiState,
    callbacks: DashboardCallbacks,
    searchUiState: SearchUiState = SearchUiState()
) {
    var isSearchActive by remember { mutableStateOf(false) }
    var abandonConfirm by remember { mutableStateOf<AbandonConfirmKind?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                DashboardTopBar(
                    uiState = uiState,
                    callbacks = callbacks,
                    onSearch = { isSearchActive = true },
                    onAbandon = { abandonConfirm = it }
                )
            }
        ) { innerPadding ->
            PullToRefreshBox(
                // Hardcoded rather than bound to uiState.fetchState: the drag-follow arrow
                // (state.distanceFraction) works regardless of that state and always snaps away on
                // release, but wiring the real refresh state here would additionally re-pin it as a
                // spinner for the whole refresh — the status banner below is that signal instead.
                isRefreshing = false,
                onRefresh = callbacks.onRefresh,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // The insets belong to the scrollable content rather than clipping the viewport.
                    // Each card is one keyed item, so offscreen cards are never composed or measured.
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp)
                ) {
                    dashboardItems(uiState, callbacks)
                }
            }
        }

        SubjectSearchOverlay(
            active = isSearchActive,
            onActiveChange = { isSearchActive = it },
            uiState = searchUiState,
            onQueryChange = callbacks.onSearchQueryChange,
            modifier = Modifier.fillMaxSize(),
        )
        abandonConfirm?.let { kind ->
            AbandonSessionDialog(kind = kind, callbacks = callbacks, onDismiss = { abandonConfirm = null })
        }
    }
}

/**
 * The dashboard's header: search, and a menu of session and account actions. No literal wordmark
 * title on purpose — a minimal, icon-only action row keeps the header from competing with the welcome
 * message below it. CompactTopBar (not the stock TopAppBar) so the empty title doesn't reserve a fixed
 * ~64dp band of dead space above that welcome message.
 */
@Composable
private fun DashboardTopBar(
    uiState: DashboardUiState,
    callbacks: DashboardCallbacks,
    onSearch: () -> Unit,
    onAbandon: (AbandonConfirmKind) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    CompactTopBar(
        actions = {
            IconButton(onClick = onSearch, modifier = Modifier.testTag(DashboardScreenTestTags.SEARCH_BUTTON)) {
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
            AbandonSessionMenuItem(
                label = "Abandon review session",
                testTag = DashboardScreenTestTags.ABANDON_REVIEW_MENU_ITEM,
                onClick = { menuExpanded = false; onAbandon(AbandonConfirmKind.Review) }
            )
            HorizontalDivider()
        }
        if (uiState.hasActiveLessonSession) {
            AbandonSessionMenuItem(
                label = "Abandon lesson session",
                testTag = DashboardScreenTestTags.ABANDON_LESSON_MENU_ITEM,
                onClick = { menuExpanded = false; onAbandon(AbandonConfirmKind.Lesson) }
            )
            HorizontalDivider()
        }
        if (uiState.hasLastSessionSummary) {
            DropdownMenuItem(
                text = { Text("Last session summary") },
                leadingIcon = { Icon(Icons.Default.History, contentDescription = null) },
                onClick = { menuExpanded = false; callbacks.onOpenLastSessionSummary() },
                modifier = Modifier.testTag(DashboardScreenTestTags.LAST_SESSION_SUMMARY_MENU_ITEM)
            )
            HorizontalDivider()
        }
        DropdownMenuItem(
            text = { Text("Settings") },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = { menuExpanded = false; callbacks.onOpenSettings() },
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
            onClick = { menuExpanded = false; callbacks.onLogOut() },
            modifier = Modifier.testTag(DashboardScreenTestTags.LOG_OUT_BUTTON)
        )
                }
            }
        }
    )
}

/** What the dashboard lists: a placeholder, an error, or every card, each one keyed item. */
private fun LazyListScope.dashboardItems(uiState: DashboardUiState, callbacks: DashboardCallbacks) {
    when (val contentState = uiState.contentState) {
        // Nothing cached yet to show while the very first fetch is in flight — the
        // only case that still blocks on a full-screen placeholder.
        DashboardContentState.Loading -> {
            item(key = "loading") {
                DashboardLoadingSkeleton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DashboardScreenTestTags.LOADING_INDICATOR)
                )
            }
        }

        is DashboardContentState.FullScreenError -> {
            item(key = "error") {
                Text(
                    text = contentState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(DashboardScreenTestTags.ERROR_TEXT)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = callbacks.onRefresh,
                    modifier = Modifier.testTag(DashboardScreenTestTags.RETRY_BUTTON)
                ) {
                    Text("Retry")
                }
            }
        }

        DashboardContentState.Content -> {
            item(key = "statusBanner") {
                DashboardStatusBanner(
                    bannerState = uiState.bannerState,
                    onRetry = callbacks.onRefresh
                )
            }

            item(key = "greeting") {
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
            }

            item(key = "summaryCards") {
                Spacer(modifier = Modifier.height(24.dp))

                TimedComposition("summaryCards") {
                SummaryCardsRow(uiState, callbacks)
                }
            }

            item(key = "reviewForecast") {
                Spacer(modifier = Modifier.height(24.dp))
                TimedComposition("reviewForecastCard") {
                    ReviewForecastCard(
                        forecast = uiState.reviewForecast,
                        selectedWindow = uiState.selectedForecastWindow,
                        onWindowChange = callbacks.onReviewForecastWindowChange,
                        selectedColorMode = uiState.selectedForecastColorMode,
                        onColorModeChange = callbacks.onReviewForecastColorModeChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (uiState.levelProgress != null) {
                item(key = "levelProgress") {
                    Spacer(modifier = Modifier.height(16.dp))
                    TimedComposition("levelProgressCard") {
                        LevelProgressCard(
                            progress = uiState.levelProgress,
                            maxLevel = uiState.level,
                            levelUpProgress = uiState.levelUpProgress,
                            onLevelChange = callbacks.onLevelProgressLevelChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            if (uiState.completionProjection != null) {
                item(key = "completionProjection") {
                    Spacer(modifier = Modifier.height(16.dp))
                    TimedComposition("completionProjectionCard") {
                        CompletionProjectionCard(
                            projection = uiState.completionProjection,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item(key = "itemSpread") {
                Spacer(modifier = Modifier.height(16.dp))
                TimedComposition("itemSpreadCard") {
                    ItemSpreadCard(spread = uiState.itemSpread, modifier = Modifier.fillMaxWidth())
                }
            }

            if (uiState.leaderboard != null) {
                item(key = "leaderboard") {
                    Spacer(modifier = Modifier.height(16.dp))
                    TimedComposition("leaderboardCard") {
                        LeaderboardCard(
                            leaderboard = uiState.leaderboard,
                            isLoading = uiState.leaderboardLoading,
                            onMetricChange = callbacks.onLeaderboardMetricChange,
                            onWindowChange = callbacks.onLeaderboardWindowChange,
                            onSeeAll = callbacks.onOpenLeaderboard,
                            selectedMetric = uiState.selectedMetric,
                            selectedWindow = uiState.selectedWindow,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item(key = "raceChart") {
                    Spacer(modifier = Modifier.height(16.dp))
                    TimedComposition("raceChartCard") {
                        RaceChartCard(
                            leaderboard = uiState.leaderboard,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/** The two start-studying cards, side by side and the same height. */
@Composable
private fun SummaryCardsRow(uiState: DashboardUiState, callbacks: DashboardCallbacks) {
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
            color = radicalColor(),
            onClick = callbacks.onStartLesson,
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
            color = kanjiColor(),
            onClick = callbacks.onStartReview,
            enabled = uiState.isReviewsCardEnabled,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .testTag(DashboardScreenTestTags.REVIEW_COUNT)
        )
    }
}

@Composable
private fun AbandonSessionDialog(kind: AbandonConfirmKind, callbacks: DashboardCallbacks, onDismiss: () -> Unit) {
    when (kind) {
        AbandonConfirmKind.Review -> ConfirmationDialog(
            title = "Abandon review session?",
            text = "Progress on reviews you haven't finished yet will be lost. This won't affect items you've already submitted.",
            confirmLabel = "Abandon",
            onConfirm = { onDismiss(); callbacks.onAbandonReviewSession() },
            onDismiss = onDismiss,
            confirmButtonTestTag = DashboardScreenTestTags.ABANDON_REVIEW_CONFIRM_BUTTON
        )
        AbandonConfirmKind.Lesson -> ConfirmationDialog(
            title = "Abandon lesson session?",
            text = "Finished batches are kept. Lessons in the batch you're on that you haven't finished, and every batch after it, are dropped from this session — they stay available to study later.",
            confirmLabel = "Abandon",
            onConfirm = { onDismiss(); callbacks.onAbandonLessonSession() },
            onDismiss = onDismiss,
            confirmButtonTestTag = DashboardScreenTestTags.ABANDON_LESSON_CONFIRM_BUTTON
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

        DashboardBannerState.SyncBlockedOnAuth -> BannerRow(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            testTag = DashboardScreenTestTags.SYNC_BLOCKED_BANNER
        ) {
            Text(
                text = "Sync paused — check your API token.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        is DashboardBannerState.Offline -> BannerRow(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            testTag = DashboardScreenTestTags.OFFLINE_BANNER,
            onClick = onRetry
        ) {
            Text(
                text = "You're offline — showing data from ${
                    bannerState.lastSyncedAtMillis?.let(::formatRelativeTime) ?: "an earlier sync"
                }. Tap to retry.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        is DashboardBannerState.PendingSync -> BannerRow(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            testTag = DashboardScreenTestTags.PENDING_SYNC_BANNER
        ) {
            val noun = if (bannerState.count == 1) "item" else "items"
            Text(
                text = "${bannerState.count} $noun waiting to sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        DashboardBannerState.Refreshing -> BannerRow(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            testTag = DashboardScreenTestTags.REFRESHING_BANNER
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
    }
}


private enum class AbandonConfirmKind { Review, Lesson }

@Composable
private fun BannerRow(
    containerColor: Color,
    testTag: String,
    onClick: (() -> Unit)? = null,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(containerColor)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 8.dp)
        .testTag(testTag)
    Row(modifier = baseModifier, verticalAlignment = Alignment.CenterVertically, content = content)
    Spacer(modifier = Modifier.height(16.dp))
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
