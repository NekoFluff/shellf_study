package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.AssignmentStatsRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardSyncCoordinator
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LogoutCoordinator
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.isAuthError
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import com.crazyfluff.shellfstudy.shared.data.model.CompletionProjection
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpread
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.model.LevelProgress
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastColorMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastWindow
import com.crazyfluff.shellfstudy.shared.lifecycle.AppForegroundTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeOverview
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeRepository
import kotlin.math.ceil
import kotlin.time.Clock

@Immutable
data class DashboardUiState(
    val fetchState: DashboardFetch = DashboardFetch.InFlight,
    val username: String? = null,
    val level: Int? = null,
    val lessonCount: Int = 0,
    val reviewCount: Int = 0,
    val pendingSyncCount: Int = 0,
    val syncBlockedOnAuth: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val isLoggedOut: Boolean = false,
    val hasActiveReviewSession: Boolean = false,
    val hasActiveLessonSession: Boolean = false,
    val lessonsCompletedToday: Int = 0,
    val dailyLessonGoal: Int = 15,
    val levelUpProgress: LevelUpProgress? = null,
    val daysOnCurrentLevel: Int? = null,
    val reviewForecast: ReviewForecast? = null,
    val levelProgress: LevelProgress? = null,
    val itemSpread: ItemSpread? = null,
    val completionProjection: CompletionProjection? = null,
    val leaderboard: Leaderboard? = null,
    val leaderboardLoading: Boolean = false,
    val selectedMetric: LeaderboardMetric = LeaderboardMetric.LEARNED,
    val selectedWindow: LeaderboardWindow = LeaderboardWindow.WEEK,
    val selectedForecastWindow: ReviewForecastWindow = ReviewForecastWindow.DAY,
    val selectedForecastColorMode: ReviewForecastColorMode = ReviewForecastColorMode.SUBJECT_TYPE,
    val hasLastSessionSummary: Boolean = false,
    /** Null until the study-time log has been read once. */
    val studyTime: StudyTimeOverview? = null
) {
    /** The last fetch failed but cached content is still on screen — see [DashboardFetch.Stale]. */
    val isShowingCachedData: Boolean
        get() = fetchState is DashboardFetch.Stale

    val bannerState: DashboardBannerState
        get() = when {
            syncBlockedOnAuth -> DashboardBannerState.SyncBlockedOnAuth
            isShowingCachedData -> DashboardBannerState.Offline(lastSyncedAtMillis)
            pendingSyncCount > 0 -> DashboardBannerState.PendingSync(pendingSyncCount)
            fetchState is DashboardFetch.InFlight -> DashboardBannerState.Refreshing
            else -> DashboardBannerState.None
        }

    val contentState: DashboardContentState
        get() = when {
            fetchState is DashboardFetch.InFlight && username == null -> DashboardContentState.Loading
            fetchState is DashboardFetch.Failed -> DashboardContentState.FullScreenError(fetchState.message)
            else -> DashboardContentState.Content
        }

    val isLessonsCardEnabled: Boolean
        get() = hasActiveLessonSession || lessonCount > 0

    val isReviewsCardEnabled: Boolean
        get() = hasActiveReviewSession || reviewCount > 0
}

/**
 * What the dashboard's last attempt to fetch its own summary did. One value rather than the
 * `isRefreshing` / `errorMessage` / `isOffline` trio it replaces, which encoded the same three
 * outcomes across three fields that could not be active together — [DashboardBannerState] and
 * [DashboardContentState] resolved the winner by `when` ordering, so the two getters were the only
 * thing keeping an impossible combination (a spinner *and* a full-screen error) off the screen.
 *
 * Deliberately not folded into the banner state: that type is a *view* state the screen renders, and
 * its `Offline` case carries `lastSyncedAtMillis`, which is written at different moments from this
 * one. Keeping the banner derived means it always reads the freshest timestamp rather than one frozen
 * at the moment of a failure.
 */
sealed interface DashboardFetch {
    /** A fetch is in flight. */
    data object InFlight : DashboardFetch

    /** Not fetching, and the last fetch succeeded — there is nothing to report. */
    data object Idle : DashboardFetch

    /** The last fetch failed and cached content is still on screen; the banner says so. */
    data object Stale : DashboardFetch

    /** The last fetch failed with nothing cached, so the whole screen is the error. */
    data class Failed(val message: String) : DashboardFetch
}

sealed interface DashboardBannerState {
    data object None : DashboardBannerState
    data object SyncBlockedOnAuth : DashboardBannerState
    data class Offline(val lastSyncedAtMillis: Long?) : DashboardBannerState
    data class PendingSync(val count: Int) : DashboardBannerState
    data object Refreshing : DashboardBannerState
}

sealed interface DashboardContentState {
    data object Loading : DashboardContentState
    data class FullScreenError(val message: String) : DashboardContentState
    data object Content : DashboardContentState
}

private data class SessionSyncState(
    val hasActiveReviewSession: Boolean,
    val hasActiveLessonSession: Boolean,
    val pendingSyncCount: Int,
    val syncBlockedOnAuth: Boolean,
    val dailyLessonGoal: Int
)

private data class ProgressStatsState(
    val lessonsCompletedToday: Int,
    val daysOnCurrentLevel: Int?,
    val itemSpread: ItemSpread,
    val completionProjection: CompletionProjection
)

private data class LevelDependentState(
    val levelUpProgress: LevelUpProgress? = null,
    val levelProgress: LevelProgress? = null
)

/** Locally-known due counts, reactive to Room writes — see their use in [DashboardViewModel.uiState]
 *  for why these reconcile against the WaniKani `/summary`-derived counts rather than replacing
 *  them outright. */
private data class SecondaryCardsState(
    val hasLastSessionSummary: Boolean,
    val reviewForecast: ReviewForecast,
    val studyTime: StudyTimeOverview
)

private data class LocalDueCounts(val reviewCount: Int, val lessonCount: Int)

class DashboardViewModel(
    private val reviewSessionController: ReviewSessionController,
    private val lessonSessionController: LessonSessionController,
    private val settingsRepository: SettingsRepository,
    private val subjectRepository: SubjectRepository,
    private val assignmentRepository: AssignmentRepository,
    private val assignmentStatsRepository: AssignmentStatsRepository,
    private val statsRepository: StatsRepository,
    private val outboxRepository: OutboxRepository,
    private val friendStatsRepository: FriendStatsRepository,
    private val logoutCoordinator: LogoutCoordinator,
    private val dashboardSyncCoordinator: DashboardSyncCoordinator,
    private val lastSessionSummaryRepository: LastSessionSummaryRepository,
    private val appForegroundTracker: AppForegroundTracker,
    private val studyTimeRepository: StudyTimeRepository
) : ViewModel() {

    private val _dashboardData = MutableStateFlow(DashboardUiState())
    private val selectedProgressLevel = MutableStateFlow<Int?>(null)
    private val currentLevel: Flow<Int?> = _dashboardData.map { it.level }.distinctUntilChanged()
    // A count rather than a plain boolean: performForcedRefresh can overlap with itself (a resume-
    // triggered initial sync racing a fast pull-to-refresh), each launching its own friend-stats
    // refresh. A shared boolean toggled by each independently would let the first call to finish
    // flip it back to false while the other's refresh is still in flight, turning off the
    // leaderboard's loading indicator prematurely.
    private val leaderboardRefreshCount = MutableStateFlow(0)
    private val _leaderboardRefreshing: Flow<Boolean> = leaderboardRefreshCount.map { it > 0 }.distinctUntilChanged()

    private val sessionSyncState: Flow<SessionSyncState> = combine(
        reviewSessionController.hasActiveSession,
        lessonSessionController.hasActiveSession,
        outboxRepository.observePendingCount(),
        outboxRepository.blockedOnAuth,
        settingsRepository.settings.map { it.dailyLessonGoal }.distinctUntilChanged()
    ) { hasReviewSession, hasLessonSession, pendingCount, blockedOnAuth, dailyGoal ->
        SessionSyncState(hasReviewSession, hasLessonSession, pendingCount, blockedOnAuth, dailyGoal)
    }

    private val completionProjectionFlow: Flow<CompletionProjection> = combine(
        subjectRepository.observeTotalSubjectCount(),
        assignmentStatsRepository.observeItemsSeenCount(),
        settingsRepository.settings
    ) { totalItems, itemsSeen, settings -> buildCompletionProjection(totalItems, itemsSeen, settings.dailyLessonGoal) }

    // Reconciled into uiState only while pendingSyncCount > 0 — see its use below. The WaniKani
    // `/summary` count can briefly lag a session just completed on this device (its outbox
    // submission hasn't reached the server yet), which otherwise leaves the dashboard's
    // review/lesson card advertising items that locally are already known to be done, and the very
    // next visit to that screen finds nothing to do.
    private val localDueCounts: Flow<LocalDueCounts> = combine(
        assignmentRepository.observeReviewDueCount(),
        assignmentRepository.observeLessonDueCount()
    ) { reviewCount, lessonCount -> LocalDueCounts(reviewCount, lessonCount) }

    private val progressStatsState: Flow<ProgressStatsState> = combine(
        assignmentStatsRepository.observeLessonsCompletedToday(),
        statsRepository.observeDaysOnCurrentLevel(),
        assignmentStatsRepository.observeSrsItemSpread(),
        completionProjectionFlow
    ) { lessonsToday, daysOnLevel, itemSpread, projection ->
        ProgressStatsState(lessonsToday, daysOnLevel, itemSpread, projection)
    }

    /**
     * The review forecast, deduped.
     *
     * [AssignmentRepository.observeReviewForecast] re-subscribes on every hour boundary (the DAO query
     * bakes `nowIso` in at subscription time), and Room re-runs it on any assignments write. A
     * re-subscription that produces the same buckets — which is the normal case, an hour passing with
     * nothing becoming due that the selected window shows — otherwise emitted a new equal
     * `ReviewForecast` that propagated through both `combine`s and invalidated the forecast Canvas.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val reviewForecastFlow: Flow<ReviewForecast> = _dashboardData
        .map { it.selectedForecastWindow }
        .distinctUntilChanged()
        .flatMapLatest { window -> assignmentStatsRepository.observeReviewForecast(window) }
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val levelDependentState: Flow<LevelDependentState> = currentLevel.flatMapLatest { level ->
        if (level == null) {
            flowOf(LevelDependentState())
        } else {
            combine(
                assignmentStatsRepository.observeLevelUpProgress(level),
                selectedProgressLevel.map { it ?: level }.distinctUntilChanged()
                    .flatMapLatest { pagedLevel -> assignmentStatsRepository.observeLevelProgress(pagedLevel) }
            ) { levelUp, levelProgress ->
                LevelDependentState(levelUp, levelProgress)
            }
        }
    }

    /**
     * The leaderboard, deduped.
     *
     * [FriendStatsRepository] rebuilds this from a friend-stats refresh, a roster change and its own
     * self-stats flow, and the dashboard refreshes friend stats on every resume and pull-to-refresh.
     * A refresh that changes nothing any chart draws — the common case, since the TTL means most
     * resumes fetch nothing — still produced a new equal `Leaderboard`, which re-ran the outer
     * `combine`'s copy and, because [RaceChartCard] passes it as a `remember` key, re-derived both
     * charts' entire series for no visible change.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val leaderboardFlow: Flow<Leaderboard?> = _dashboardData
        .map { it.selectedMetric to it.selectedWindow }
        .distinctUntilChanged()
        .flatMapLatest { (metric, window) -> friendStatsRepository.observeLeaderboard(metric, window) }
        .distinctUntilChanged()

    // Grouped only because the outer combine below is already at the five-flow typed overload.
    private val secondaryCardsState: Flow<SecondaryCardsState> = combine(
        lastSessionSummaryRepository.exists,
        reviewForecastFlow,
        studyTimeRepository.observeOverview().distinctUntilChanged()
    ) { hasLastSessionSummary, reviewForecast, studyTime ->
        SecondaryCardsState(hasLastSessionSummary, reviewForecast, studyTime)
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(_dashboardData, sessionSyncState, progressStatsState, levelDependentState, localDueCounts)
        { imperative, sessionSync, progress, levelDependent, localCounts ->
            // While offline, `imperative.reviewCount`/`lessonCount` are whatever /summary last
            // reported — possibly hours stale — so trust the local assignments table instead; it's
            // queried against `availableAt <= now` and re-subscribed at every hour boundary (see
            // AssignmentRepository.observeReviewDueCount), so it stays current regardless of
            // connectivity and of how long this ViewModel has been alive. While online, only clamp to
            // the local count when the outbox still has unsent rows — that's exactly the window where
            // a just-completed session's submissions haven't reached the server yet, so /summary's
            // count is known-stale. Once the outbox drains, trust the freshly-fetched remote count
            // outright again — the local assignments table isn't guaranteed to be resynced on every
            // dashboard resume, so clamping unconditionally would let a merely-unsynced local cache
            // mask genuinely due reviews/lessons.
            val hasUnsentSubmissions = sessionSync.pendingSyncCount > 0
            fun reconcile(remoteCount: Int, localCount: Int) = when {
                imperative.isShowingCachedData -> localCount
                hasUnsentSubmissions -> minOf(remoteCount, localCount)
                else -> remoteCount
            }
            imperative.copy(
                reviewCount = reconcile(imperative.reviewCount, localCounts.reviewCount),
                lessonCount = reconcile(imperative.lessonCount, localCounts.lessonCount),
                hasActiveReviewSession = sessionSync.hasActiveReviewSession,
                hasActiveLessonSession = sessionSync.hasActiveLessonSession,
                pendingSyncCount = sessionSync.pendingSyncCount,
                syncBlockedOnAuth = sessionSync.syncBlockedOnAuth,
                dailyLessonGoal = sessionSync.dailyLessonGoal,
                lessonsCompletedToday = progress.lessonsCompletedToday,
                daysOnCurrentLevel = progress.daysOnCurrentLevel,
                itemSpread = progress.itemSpread,
                completionProjection = progress.completionProjection,
                levelUpProgress = levelDependent.levelUpProgress,
                levelProgress = levelDependent.levelProgress
            )
        },
        leaderboardFlow,
        _leaderboardRefreshing,
        secondaryCardsState
    ) { dashboardState, leaderboard, leaderboardLoading, secondary ->
        dashboardState.copy(
            leaderboard = leaderboard,
            leaderboardLoading = leaderboardLoading,
            hasLastSessionSummary = secondary.hasLastSessionSummary,
            reviewForecast = secondary.reviewForecast,
            studyTime = secondary.studyTime
        )
    }
        // Room's invalidation is table-level, so every write anywhere in the assignments, subjects,
        // outbox or level_progressions tables re-runs *all* of the flows above — including writes
        // that cannot change any value this screen shows (a friend-stats refresh, a sync-cursor
        // update, an outbox status flip). `combine` re-emits on every input emission regardless of
        // whether the combined result differs, so without this each of those writes published a
        // brand-new DashboardUiState instance to the screen. That matters far more than the extra
        // recomputation it looks like: a new instance defeats every equality check downstream, so the
        // whole dashboard tree — both Canvas charts and the level-progress chip grid included —
        // recomposed for writes that changed nothing on screen.
        //
        // DashboardUiState is a data class whose every field is a value or an immutable data class, so
        // structural equality is exactly "would this render differently".
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    private var hasCompletedInitialSync = false

    init {
        viewModelScope.launch {
            seedFromCache()
        }

        // Compose Navigation only re-fires DashboardRoute's LaunchedEffect(Unit) on true cold
        // start, since this ViewModel survives ordinary Review/Lesson round trips. Returning from
        // background (home button, app switcher, lock screen) without navigating away wouldn't
        // otherwise trigger a sync at all, so mirror the same resume logic on every foreground
        // transition after the first (the first is left to LaunchedEffect(Unit) to avoid a
        // redundant double sync on cold start).
        viewModelScope.launch {
            appForegroundTracker.isForeground.drop(1).filter { it }.collect {
                onDashboardResumed()
            }
        }
    }

    fun onLevelProgressLevelChange(level: Int) {
        selectedProgressLevel.value = level.coerceAtLeast(1)
    }

    fun onLeaderboardMetricChange(metric: LeaderboardMetric) {
        _dashboardData.update { it.copy(selectedMetric = metric) }
    }

    fun onLeaderboardWindowChange(window: LeaderboardWindow) {
        _dashboardData.update { it.copy(selectedWindow = window) }
    }

    fun onReviewForecastWindowChange(window: ReviewForecastWindow) {
        _dashboardData.update { it.copy(selectedForecastWindow = window) }
    }

    fun onReviewForecastColorModeChange(colorMode: ReviewForecastColorMode) {
        _dashboardData.update { it.copy(selectedForecastColorMode = colorMode) }
    }

    private suspend fun seedFromCache() {
        val cached = dashboardSyncCoordinator.cachedSummary.first() ?: return
        _dashboardData.update { current ->
            if (current.lastSyncedAtMillis != null) {
                current
            } else {
                current.copy(
                    username = cached.username,
                    level = cached.level,
                    lessonCount = cached.lessonCount,
                    reviewCount = cached.reviewCount,
                    lastSyncedAtMillis = cached.lastSyncedAtMillis
                )
            }
        }
    }

    fun refresh() {
        viewModelScope.launch { performForcedRefresh(forceFriendStatsRefresh = true) }
    }

    private suspend fun performForcedRefresh(forceFriendStatsRefresh: Boolean = false) {
        _dashboardData.update { it.copy(fetchState = DashboardFetch.InFlight) }

        // Non-blocking: friend stats refresh runs in the background and doesn't gate the main UI.
        // Only pull-to-refresh forces past the TTL — the cold-start call below should still
        // respect it, or every app launch would refetch every friend's stats regardless of when
        // they were last fetched.
        viewModelScope.launch {
            leaderboardRefreshCount.update { it + 1 }
            try {
                friendStatsRepository.refreshAllIfStale(force = forceFriendStatsRefresh)
            } finally {
                leaderboardRefreshCount.update { it - 1 }
            }
        }

        // The pass's own result isn't surfaced: the user and summary fetch that follows fails the
        // same ways (offline, auth) and is what decides what the banner says. A failure only the
        // pass hit — one resource erroring — leaves that resource's cache as it was, and the
        // next pass retries it.
        dashboardSyncCoordinator.sync(force = true)

        val (userResult, summaryResult) = dashboardSyncCoordinator.fetchUserAndSummary()
        val hasContent = _dashboardData.value.username != null

        if (userResult is ApiResult.Error) {
            if (userResult.isAuthError) {
                logoutCoordinator.logout()
                _dashboardData.update { it.copy(fetchState = DashboardFetch.Idle, isLoggedOut = true) }
            } else if (hasContent) {
                _dashboardData.update { it.copy(fetchState = DashboardFetch.Stale) }
            } else {
                _dashboardData.update { it.copy(fetchState = DashboardFetch.Failed(userResult.message)) }
            }
            return
        }
        if (summaryResult is ApiResult.Error) {
            if (hasContent) {
                _dashboardData.update { it.copy(fetchState = DashboardFetch.Stale) }
            } else {
                _dashboardData.update { it.copy(fetchState = DashboardFetch.Failed(summaryResult.message)) }
            }
            return
        }

        val user = (userResult as ApiResult.Success).data
        val summary = (summaryResult as ApiResult.Success).data
        val syncedAtMillis = Clock.System.now().toEpochMilliseconds()
        dashboardSyncCoordinator.cacheSummary(user, summary, syncedAtMillis)
        _dashboardData.update {
            it.copy(
                fetchState = DashboardFetch.Idle,
                username = user.username,
                level = user.level,
                lessonCount = summary.lessonCount,
                reviewCount = summary.reviewCount,
                lastSyncedAtMillis = syncedAtMillis
            )
        }
    }

    fun onDashboardResumed() {
        viewModelScope.launch {
            outboxRepository.requestSyncNow()

            if (!hasCompletedInitialSync) {
                hasCompletedInitialSync = true
                performForcedRefresh()
                return@launch
            }

            // One pass, with assignments on a short freshness window — see
            // SyncOrchestrator.syncAllForResume.
            dashboardSyncCoordinator.syncForResume()

            val (userResult, summaryResult) = dashboardSyncCoordinator.fetchUserAndSummary()

            if (userResult is ApiResult.Error && userResult.isAuthError) {
                logoutCoordinator.logout()
                _dashboardData.update { it.copy(isLoggedOut = true) }
                return@launch
            }

            val user = (userResult as? ApiResult.Success)?.data
            val summary = (summaryResult as? ApiResult.Success)?.data
            // ApiResult has no variant besides Success and Error, so "one of the two came back
            // empty" is the same question as "did either of them come back an Error".
            val failedFetch = (userResult as? ApiResult.Error) ?: (summaryResult as? ApiResult.Error)
            val syncedAtMillis = Clock.System.now().toEpochMilliseconds()

            _dashboardData.update {
                val resolvedUsername = user?.username ?: it.username
                val resolvedLevel = user?.level ?: it.level
                val resolvedLessonCount = summary?.lessonCount ?: it.lessonCount
                val resolvedReviewCount = summary?.reviewCount ?: it.reviewCount
                it.copy(
                    username = resolvedUsername,
                    level = resolvedLevel,
                    lessonCount = resolvedLessonCount,
                    reviewCount = resolvedReviewCount,
                    // Same three outcomes as the cold-start path above: a clean fetch, cached
                    // content that outlived a failure, or nothing at all to show.
                    fetchState = when {
                        failedFetch == null -> DashboardFetch.Idle
                        resolvedUsername != null -> DashboardFetch.Stale
                        else -> DashboardFetch.Failed(failedFetch.message)
                    },
                    lastSyncedAtMillis = if (failedFetch != null) it.lastSyncedAtMillis else syncedAtMillis
                )
            }

            if (user != null && summary != null) {
                dashboardSyncCoordinator.cacheSummary(user, summary, syncedAtMillis)
            }
        }
    }

    fun abandonReviewSession() {
        viewModelScope.launch { reviewSessionController.abandon() }
    }

    fun abandonLessonSession() {
        viewModelScope.launch { lessonSessionController.abandon() }
    }
}

private fun buildCompletionProjection(totalItems: Int, itemsSeen: Int, dailyLessonGoal: Int): CompletionProjection {
    val remaining = (totalItems - itemsSeen).coerceAtLeast(0)
    val daysRemaining = if (dailyLessonGoal > 0) ceil(remaining.toDouble() / dailyLessonGoal).toInt() else 0
    return CompletionProjection(
        totalItems = totalItems,
        itemsSeen = itemsSeen,
        dailyPace = dailyLessonGoal,
        daysRemaining = daysRemaining,
        projectedCompletionDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .plus(daysRemaining, DateTimeUnit.DAY)
    )
}
