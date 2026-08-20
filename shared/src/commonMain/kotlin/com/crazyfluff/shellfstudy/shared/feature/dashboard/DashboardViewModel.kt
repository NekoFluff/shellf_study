package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.AssignmentRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardSyncCoordinator
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.crazyfluff.shellfstudy.shared.data.LastSessionSummaryRepository
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.LogoutCoordinator
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.OutboxSyncScheduler
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.StatsRepository
import com.crazyfluff.shellfstudy.shared.data.SubjectRepository
import com.crazyfluff.shellfstudy.shared.data.isAuthError
import com.crazyfluff.shellfstudy.shared.data.model.CompletionProjection
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpread
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.model.LevelProgress
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
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
import kotlin.math.ceil
import kotlin.time.Clock

data class DashboardUiState(
    val isRefreshing: Boolean = true,
    val username: String? = null,
    val level: Int? = null,
    val lessonCount: Int = 0,
    val reviewCount: Int = 0,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
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
    val hasLastSessionSummary: Boolean = false
) {
    val bannerState: DashboardBannerState
        get() = when {
            syncBlockedOnAuth -> DashboardBannerState.SyncBlockedOnAuth
            isOffline -> DashboardBannerState.Offline(lastSyncedAtMillis)
            pendingSyncCount > 0 -> DashboardBannerState.PendingSync(pendingSyncCount)
            isRefreshing -> DashboardBannerState.Refreshing
            else -> DashboardBannerState.None
        }

    val contentState: DashboardContentState
        get() = when {
            isRefreshing && username == null -> DashboardContentState.Loading
            errorMessage != null -> DashboardContentState.FullScreenError(errorMessage)
            else -> DashboardContentState.Content
        }

    val isLessonsCardEnabled: Boolean
        get() = hasActiveLessonSession || lessonCount > 0

    val isReviewsCardEnabled: Boolean
        get() = hasActiveReviewSession || reviewCount > 0
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
    val reviewForecast: ReviewForecast,
    val itemSpread: ItemSpread,
    val completionProjection: CompletionProjection
)

private data class LevelDependentState(
    val levelUpProgress: LevelUpProgress? = null,
    val levelProgress: LevelProgress? = null
)

class DashboardViewModel(
    private val reviewSessionRepository: ReviewSessionRepository,
    private val lessonSessionRepository: LessonSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val subjectRepository: SubjectRepository,
    private val assignmentRepository: AssignmentRepository,
    private val statsRepository: StatsRepository,
    private val outboxRepository: OutboxRepository,
    private val outboxSyncScheduler: OutboxSyncScheduler,
    private val friendStatsRepository: FriendStatsRepository,
    private val logoutCoordinator: LogoutCoordinator,
    private val dashboardSyncCoordinator: DashboardSyncCoordinator,
    private val lastSessionSummaryRepository: LastSessionSummaryRepository,
    private val appForegroundTracker: AppForegroundTracker
) : ViewModel() {

    private val _dashboardData = MutableStateFlow(DashboardUiState())
    private val selectedProgressLevel = MutableStateFlow<Int?>(null)
    private val currentLevel: Flow<Int?> = _dashboardData.map { it.level }.distinctUntilChanged()
    private val _leaderboardRefreshing = MutableStateFlow(false)

    private val sessionSyncState: Flow<SessionSyncState> = combine(
        reviewSessionRepository.hasActiveSession,
        lessonSessionRepository.hasActiveSession,
        outboxRepository.observePendingCount(),
        outboxRepository.blockedOnAuth,
        settingsRepository.settings.map { it.dailyLessonGoal }.distinctUntilChanged()
    ) { hasReviewSession, hasLessonSession, pendingCount, blockedOnAuth, dailyGoal ->
        SessionSyncState(hasReviewSession, hasLessonSession, pendingCount, blockedOnAuth, dailyGoal)
    }

    private val completionProjectionFlow: Flow<CompletionProjection> = combine(
        subjectRepository.observeTotalSubjectCount(),
        assignmentRepository.observeItemsSeenCount(),
        settingsRepository.settings
    ) { totalItems, itemsSeen, settings -> buildCompletionProjection(totalItems, itemsSeen, settings.dailyLessonGoal) }

    private val progressStatsState: Flow<ProgressStatsState> = combine(
        assignmentRepository.observeLessonsCompletedToday(),
        statsRepository.observeDaysOnCurrentLevel(),
        assignmentRepository.observeReviewForecast(),
        assignmentRepository.observeSrsItemSpread(),
        completionProjectionFlow
    ) { lessonsToday, daysOnLevel, forecast, itemSpread, projection ->
        ProgressStatsState(lessonsToday, daysOnLevel, forecast, itemSpread, projection)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val levelDependentState: Flow<LevelDependentState> = currentLevel.flatMapLatest { level ->
        if (level == null) {
            flowOf(LevelDependentState())
        } else {
            combine(
                assignmentRepository.observeLevelUpProgress(level),
                selectedProgressLevel.map { it ?: level }.distinctUntilChanged()
                    .flatMapLatest { pagedLevel -> assignmentRepository.observeLevelProgress(pagedLevel) }
            ) { levelUp, levelProgress ->
                LevelDependentState(levelUp, levelProgress)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val leaderboardFlow: Flow<Leaderboard?> = _dashboardData
        .map { it.selectedMetric to it.selectedWindow }
        .distinctUntilChanged()
        .flatMapLatest { (metric, window) -> friendStatsRepository.observeLeaderboard(metric, window) }

    val uiState: StateFlow<DashboardUiState> = combine(
        combine(_dashboardData, sessionSyncState, progressStatsState, levelDependentState)
        { imperative, sessionSync, progress, levelDependent ->
            imperative.copy(
                hasActiveReviewSession = sessionSync.hasActiveReviewSession,
                hasActiveLessonSession = sessionSync.hasActiveLessonSession,
                pendingSyncCount = sessionSync.pendingSyncCount,
                syncBlockedOnAuth = sessionSync.syncBlockedOnAuth,
                dailyLessonGoal = sessionSync.dailyLessonGoal,
                lessonsCompletedToday = progress.lessonsCompletedToday,
                daysOnCurrentLevel = progress.daysOnCurrentLevel,
                reviewForecast = progress.reviewForecast,
                itemSpread = progress.itemSpread,
                completionProjection = progress.completionProjection,
                levelUpProgress = levelDependent.levelUpProgress,
                levelProgress = levelDependent.levelProgress
            )
        },
        leaderboardFlow,
        _leaderboardRefreshing,
        lastSessionSummaryRepository.exists
    ) { dashboardState, leaderboard, leaderboardLoading, hasLastSessionSummary ->
        dashboardState.copy(
            leaderboard = leaderboard,
            leaderboardLoading = leaderboardLoading,
            hasLastSessionSummary = hasLastSessionSummary
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

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
        viewModelScope.launch { performForcedRefresh() }
    }

    private suspend fun performForcedRefresh() {
        _dashboardData.update { it.copy(isRefreshing = true, errorMessage = null, isOffline = false) }

        // Non-blocking: friend stats refresh runs in the background and doesn't gate the main UI.
        viewModelScope.launch {
            _leaderboardRefreshing.value = true
            friendStatsRepository.refreshAllIfStale()
            _leaderboardRefreshing.value = false
        }

        dashboardSyncCoordinator.sync(force = true)

        val (userResult, summaryResult) = dashboardSyncCoordinator.fetchUserAndSummary()
        val hasContent = _dashboardData.value.username != null

        if (userResult is ApiResult.Error) {
            if (userResult.isAuthError) {
                logoutCoordinator.logout()
                _dashboardData.update { it.copy(isRefreshing = false, isLoggedOut = true) }
            } else if (hasContent) {
                _dashboardData.update { it.copy(isRefreshing = false, isOffline = true) }
            } else {
                _dashboardData.update { it.copy(isRefreshing = false, errorMessage = userResult.message) }
            }
            return
        }
        if (summaryResult is ApiResult.Error) {
            if (hasContent) {
                _dashboardData.update { it.copy(isRefreshing = false, isOffline = true) }
            } else {
                _dashboardData.update { it.copy(isRefreshing = false, errorMessage = summaryResult.message) }
            }
            return
        }

        val user = (userResult as ApiResult.Success).data
        val summary = (summaryResult as ApiResult.Success).data
        val syncedAtMillis = Clock.System.now().toEpochMilliseconds()
        dashboardSyncCoordinator.cacheSummary(user, summary, syncedAtMillis)
        _dashboardData.update {
            it.copy(
                isRefreshing = false,
                isOffline = false,
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
            outboxSyncScheduler.requestImmediateSync()

            if (!hasCompletedInitialSync) {
                hasCompletedInitialSync = true
                performForcedRefresh()
                return@launch
            }

            dashboardSyncCoordinator.sync(force = false)

            val (userResult, summaryResult) = dashboardSyncCoordinator.fetchUserAndSummary()

            if (userResult is ApiResult.Error && userResult.isAuthError) {
                logoutCoordinator.logout()
                _dashboardData.update { it.copy(isLoggedOut = true) }
                return@launch
            }

            val user = (userResult as? ApiResult.Success)?.data
            val summary = (summaryResult as? ApiResult.Success)?.data
            val fetchFailed = user == null || summary == null
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
                    isOffline = fetchFailed && resolvedUsername != null,
                    lastSyncedAtMillis = if (fetchFailed) it.lastSyncedAtMillis else syncedAtMillis
                )
            }

            if (user != null && summary != null) {
                dashboardSyncCoordinator.cacheSummary(user, summary, syncedAtMillis)
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            logoutCoordinator.logout()
            _dashboardData.update { it.copy(isLoggedOut = true) }
        }
    }

    fun abandonReviewSession() {
        viewModelScope.launch { reviewSessionRepository.clear() }
    }

    fun abandonLessonSession() {
        viewModelScope.launch { lessonSessionRepository.clear() }
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
