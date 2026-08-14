package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.core.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.core.data.OutboxRepository
import com.crazyfluff.shellfstudy.core.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.StatsRepository
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.data.isAuthError
import com.crazyfluff.shellfstudy.shared.data.model.CompletionProjection
import com.crazyfluff.shellfstudy.shared.data.model.DashboardSummary
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpread
import com.crazyfluff.shellfstudy.shared.data.model.LevelProgress
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.core.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.core.sync.SyncOrchestrator
import com.crazyfluff.shellfstudy.core.sync.PitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.core.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
import javax.inject.Inject
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
    val completionProjection: CompletionProjection? = null
) {
    /** Which status banner takes priority — same order [DashboardStatusBanner] used to hand-derive
     *  from these same flags, now computed once here as the single source of truth. */
    val bannerState: DashboardBannerState
        get() = when {
            syncBlockedOnAuth -> DashboardBannerState.SyncBlockedOnAuth
            isOffline -> DashboardBannerState.Offline(lastSyncedAtMillis)
            pendingSyncCount > 0 -> DashboardBannerState.PendingSync(pendingSyncCount)
            isRefreshing -> DashboardBannerState.Refreshing
            else -> DashboardBannerState.None
        }

    /** Which of the screen's three top-level content regions to render. [isLoggedOut] deliberately
     *  stays outside this — it's a one-shot navigation trigger consumed via `LaunchedEffect`, not
     *  a render state. */
    val contentState: DashboardContentState
        get() = when {
            isRefreshing && username == null -> DashboardContentState.Loading
            errorMessage != null -> DashboardContentState.FullScreenError(errorMessage)
            else -> DashboardContentState.Content
        }

    /** Whether the Lessons/Reviews summary cards should be tappable — false when there's nothing
     *  to start and no in-progress session to resume, so the dashboard doesn't invite a tap that
     *  just bounces straight into an empty session. */
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

/** The pieces of [DashboardUiState] driven by cheap, frequently-changing local flags (active
 *  session / pending sync), grouped so they share one subscription instead of five. */
private data class SessionSyncState(
    val hasActiveReviewSession: Boolean,
    val hasActiveLessonSession: Boolean,
    val pendingSyncCount: Int,
    val syncBlockedOnAuth: Boolean,
    val dailyLessonGoal: Int
)

/** The pieces of [DashboardUiState] that are real Room-derived computations (forecast bucketing,
 *  SRS-stage spread, completion projection) — the ones worth keeping off Main even while visible
 *  (see the `flowOn(Dispatchers.Default)` on their sources in [AssignmentRepository]). */
private data class ProgressStatsState(
    val lessonsCompletedToday: Int,
    val daysOnCurrentLevel: Int?,
    val reviewForecast: ReviewForecast,
    val itemSpread: ItemSpread,
    val completionProjection: CompletionProjection
)

/** The Level Progress / level-up pieces, both keyed off a level (current, or browsed via
 *  [DashboardViewModel.onLevelProgressLevelChange]). Both default to "nothing yet" — [level] can
 *  stay null indefinitely (e.g. the initial user/summary fetch fails with no cache to fall back
 *  on), and this must never block [DashboardViewModel.uiState] from emitting while that's true. */
private data class LevelDependentState(
    val levelUpProgress: LevelUpProgress? = null,
    val levelProgress: LevelProgress? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val waniKaniRepository: WaniKaniRepository,
    private val tokenRepository: TokenRepository,
    private val reviewSessionRepository: ReviewSessionRepository,
    private val lessonSessionRepository: LessonSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val subjectRepository: SubjectRepository,
    private val assignmentRepository: AssignmentRepository,
    private val statsRepository: StatsRepository,
    private val dashboardCacheRepository: DashboardCacheRepository,
    private val outboxRepository: OutboxRepository,
    private val syncOrchestrator: SyncOrchestrator,
    private val syncScheduler: SyncScheduler,
    private val pitchAccentScrapeScheduler: PitchAccentScrapeScheduler,
    private val notificationCoordinator: NotificationCoordinator
) : ViewModel() {

    // Holds only the state set imperatively by one-shot calls below (refresh/onDashboardResumed/
    // seedFromCache/logOut) — never touched by a continuously-running collector, so it costs
    // nothing while unobserved and needs no lifecycle gating of its own.
    private val _dashboardData = MutableStateFlow(DashboardUiState())

    // Which level the Level Progress card is browsing. Null tracks the user's current level
    // automatically; once they page to a different level it's pinned until changed again.
    private val selectedProgressLevel = MutableStateFlow<Int?>(null)

    private val currentLevel: Flow<Int?> = _dashboardData.map { it.level }.distinctUntilChanged()

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
            // No level yet (e.g. the initial user/summary fetch failed with nothing cached) —
            // emit the "nothing to show" default immediately rather than waiting forever, so this
            // never blocks the rest of uiState from emitting.
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

    // The only StateFlow this ViewModel exposes — everything reactive above is plumbed through
    // here rather than mutating _dashboardData directly. SharingStarted.WhileSubscribed(5_000)
    // means every Flow feeding this (and their underlying Room/DataStore queries) only runs while
    // something is actually collecting `uiState` — i.e. while DashboardScreen is visible and using
    // collectAsStateWithLifecycle(). Previously these ran via independent viewModelScope.launch
    // collectors started in init{}, which kept going for this ViewModel's entire lifetime — since
    // Hilt scopes it to Dashboard's NavBackStackEntry and Review/Lesson/Settings are all pushed on
    // top of Dashboard rather than replacing it, that meant real Room-query recomputation (e.g.
    // observeReviewForecast's forecast bucketing) kept firing on every unrelated write to the
    // assignments table — including from Review's own grading — even while Dashboard wasn't the
    // visible screen at all. 5 seconds is the standard Android-recommended timeout: long enough to
    // survive a quick config-change-style blip without dropping the upstream subscription, short
    // enough to genuinely detach for the length of an actual review/lesson session. The last known
    // value is still shown instantly on return (stateIn's replay), with a fresh recompute running
    // just behind it — no blank/loading flash, and no more background cost while truly off-screen.
    val uiState: StateFlow<DashboardUiState> = combine(
        _dashboardData, sessionSyncState, progressStatsState, levelDependentState
    ) { imperative, sessionSync, progress, levelDependent ->
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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    // Guards the very first dashboard appearance in this ViewModel's lifetime: that call goes
    // through onDashboardResumed() (driven by the Route's LaunchedEffect(Unit), which fires on
    // first composition too) rather than a separate init{}-launched refresh(), so cold start does
    // one fetch instead of two racing ones. See onDashboardResumed() for the forced/staleness-gated
    // split this flag drives.
    private var hasCompletedInitialSync = false

    init {
        // Awaiting the (fast, local) cache read guarantees any cached content is on screen before
        // DashboardRoute's LaunchedEffect(Unit) kicks off the first onDashboardResumed() network
        // round trip, and avoids a race where a slow cache read could otherwise land after it and
        // clobber fresher network data.
        viewModelScope.launch {
            seedFromCache()
        }
    }

    /** Lets the Level Progress card page to a different level than the one currently being studied. */
    fun onLevelProgressLevelChange(level: Int) {
        selectedProgressLevel.value = level.coerceAtLeast(1)
    }

    /**
     * Fills in the last-known username/level/lesson/review counts from [DashboardCacheRepository]
     * so a cold start renders real content immediately, instead of the skeleton, while [refresh]
     * (kicked off separately in `init`) races to confirm/update it over the network.
     */
    private suspend fun seedFromCache() {
        val cached = dashboardCacheRepository.cachedSummary.first() ?: return
        _dashboardData.update { current ->
            // A completed refresh (successful or not) always sets lastSyncedAtMillis or leaves it
            // at a prior real value; never let a cache read that lands late override it.
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

    /**
     * Refreshes the dashboard. Deliberately doesn't clear [DashboardUiState]'s content fields up
     * front — any previously loaded (or cache-seeded) values stay on screen for the whole
     * operation, with only [DashboardUiState.isRefreshing] flipping on, so the screen never goes
     * blank mid-refresh. [DashboardUiState.errorMessage] (the full-screen error state) is reserved
     * for a failure with nothing cached to fall back on; a failure while content is already showing
     * sets [DashboardUiState.isOffline] instead, which the screen renders as a small banner.
     */
    fun refresh() {
        viewModelScope.launch { performForcedRefresh() }
    }

    /**
     * The forced-sync path shared by [refresh] (explicit pull-to-refresh) and the very first
     * [onDashboardResumed] call in this ViewModel's lifetime (cold start / right after login) —
     * see [hasCompletedInitialSync]. Always syncs with `force = true` and uses the "full" error
     * handling: an auth error logs the user out, any other failure either flags
     * [DashboardUiState.isOffline] (if content is already on screen) or sets
     * [DashboardUiState.errorMessage] (if not).
     */
    private suspend fun performForcedRefresh() {
        _dashboardData.update { it.copy(isRefreshing = true, errorMessage = null, isOffline = false) }

        syncOrchestrator.syncAll(force = true)

        val (userResult, summaryResult) = fetchUserAndSummary()
        val hasContent = _dashboardData.value.username != null

        if (userResult is ApiResult.Error) {
            if (userResult.isAuthError) {
                tokenRepository.clearToken()
                syncScheduler.cancelPeriodicSync()
                pitchAccentScrapeScheduler.cancelPeriodicScrape()
                notificationCoordinator.onLogout()
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
        val syncedAtMillis = System.currentTimeMillis()
        dashboardCacheRepository.save(
            username = user.username,
            level = user.level,
            lessonCount = summary.lessonCount,
            reviewCount = summary.reviewCount,
            syncedAtMillis = syncedAtMillis
        )
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

    /**
     * Called whenever the dashboard becomes visible again (e.g. returning from a finished
     * review/lesson session, or the Route's first composition — see [hasCompletedInitialSync]),
     * so lesson/review counts don't sit stale until the next hourly background sync or a manual
     * pull-to-refresh. The very first call in this ViewModel's lifetime is indistinguishable from
     * a cold start, so it forces a full sync via [performForcedRefresh] just like [refresh] would;
     * every call after that is deliberately lighter, reusing [SyncOrchestrator]'s own per-resource
     * staleness gate (`force = false`) rather than forcing a full resync of everything, and never
     * touching [DashboardUiState.isRefreshing] or [DashboardUiState.errorMessage] — most calls will
     * be near-instant no-ops, and a transient failure here shouldn't blank out an already-populated
     * dashboard, just flag it as [DashboardUiState.isOffline]. A confirmed auth error is the one
     * exception even in the lighter path: same as [refresh], it logs the user out rather than being
     * treated as merely offline.
     */
    fun onDashboardResumed() {
        viewModelScope.launch {
            if (!hasCompletedInitialSync) {
                hasCompletedInitialSync = true
                performForcedRefresh()
                return@launch
            }

            syncOrchestrator.syncAll(force = false)

            val (userResult, summaryResult) = fetchUserAndSummary()

            if (userResult is ApiResult.Error && userResult.isAuthError) {
                tokenRepository.clearToken()
                syncScheduler.cancelPeriodicSync()
                pitchAccentScrapeScheduler.cancelPeriodicScrape()
                notificationCoordinator.onLogout()
                _dashboardData.update { it.copy(isLoggedOut = true) }
                return@launch
            }

            val user = (userResult as? ApiResult.Success)?.data
            val summary = (summaryResult as? ApiResult.Success)?.data
            val fetchFailed = user == null || summary == null
            val syncedAtMillis = System.currentTimeMillis()

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

            if (!fetchFailed && user != null && summary != null) {
                dashboardCacheRepository.save(
                    username = user.username,
                    level = user.level,
                    lessonCount = summary.lessonCount,
                    reviewCount = summary.reviewCount,
                    syncedAtMillis = syncedAtMillis
                )
            }
        }
    }

    /** The one fetch [refresh] and [onDashboardResumed] share — deliberately just the raw pair of
     *  results, not a collapsed outcome: the two callers apply genuinely different interpretation
     *  policies on top (refresh() is all-or-nothing; onDashboardResumed() resolves each field
     *  independently so a lone summary failure doesn't discard an otherwise-successful user fetch). */
    private suspend fun fetchUserAndSummary(): Pair<ApiResult<WaniKaniUser>, ApiResult<DashboardSummary>> {
        val userResult = waniKaniRepository.fetchUser()
        val summaryResult = waniKaniRepository.fetchDashboardSummary()
        return userResult to summaryResult
    }

    fun logOut() {
        viewModelScope.launch {
            tokenRepository.clearToken()
            syncScheduler.cancelPeriodicSync()
            pitchAccentScrapeScheduler.cancelPeriodicScrape()
            notificationCoordinator.onLogout()
            _dashboardData.update { it.copy(isLoggedOut = true) }
        }
    }

    /** Discards a persisted in-progress review session without needing to open the Review screen
     *  first — [DashboardUiState.hasActiveReviewSession] reflects the clear reactively via its own
     *  observed flow. */
    fun abandonReviewSession() {
        viewModelScope.launch { reviewSessionRepository.clear() }
    }

    /** Same idea as [abandonReviewSession] but for the lesson quiz. */
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
