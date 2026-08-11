package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.core.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.StatsRepository
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.data.isAuthError
import com.crazyfluff.shellfstudy.core.data.model.CompletionProjection
import com.crazyfluff.shellfstudy.core.data.model.ItemSpread
import com.crazyfluff.shellfstudy.core.data.model.LevelProgress
import com.crazyfluff.shellfstudy.core.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.core.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.core.sync.SyncOrchestrator
import com.crazyfluff.shellfstudy.core.sync.PitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.core.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.ceil

data class DashboardUiState(
    val isRefreshing: Boolean = true,
    val username: String? = null,
    val level: Int? = null,
    val lessonCount: Int = 0,
    val reviewCount: Int = 0,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val isLoggedOut: Boolean = false,
    val hasActiveReviewSession: Boolean = false,
    val lessonsCompletedToday: Int = 0,
    val dailyLessonGoal: Int = 15,
    val kanjiGuruedForLevelUp: Int = 0,
    val kanjiTotalForLevelUp: Int = 0,
    val daysOnCurrentLevel: Int? = null,
    val reviewForecast: ReviewForecast? = null,
    val levelProgress: LevelProgress? = null,
    val itemSpread: ItemSpread? = null,
    val completionProjection: CompletionProjection? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val waniKaniRepository: WaniKaniRepository,
    private val tokenRepository: TokenRepository,
    private val reviewSessionRepository: ReviewSessionRepository,
    private val settingsRepository: SettingsRepository,
    private val subjectRepository: SubjectRepository,
    private val assignmentRepository: AssignmentRepository,
    private val statsRepository: StatsRepository,
    private val dashboardCacheRepository: DashboardCacheRepository,
    private val syncOrchestrator: SyncOrchestrator,
    private val syncScheduler: SyncScheduler,
    private val pitchAccentScrapeScheduler: PitchAccentScrapeScheduler,
    private val notificationCoordinator: NotificationCoordinator
) : ViewModel() {

    private val _dashboardData = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _dashboardData.asStateFlow()

    // Which level the Level Progress card is browsing. Null tracks the user's current level
    // automatically; once they page to a different level it's pinned until changed again.
    private val selectedProgressLevel = MutableStateFlow<Int?>(null)

    init {
        // Awaiting the (fast, local) cache read before kicking off refresh() guarantees any cached
        // content is on screen before the network round trip starts, and avoids a race where a
        // slow cache read could otherwise land after refresh() and clobber fresher network data.
        viewModelScope.launch {
            seedFromCache()
            refresh()
        }

        observe(reviewSessionRepository.hasActiveSession) { copy(hasActiveReviewSession = it) }
        observe(settingsRepository.settings) { copy(dailyLessonGoal = it.dailyLessonGoal) }
        observe(assignmentRepository.observeLessonsCompletedToday()) { copy(lessonsCompletedToday = it) }
        observe(statsRepository.observeDaysOnCurrentLevel()) { copy(daysOnCurrentLevel = it) }
        observe(assignmentRepository.observeReviewForecast()) { copy(reviewForecast = it) }
        observe(assignmentRepository.observeSrsItemSpread()) { copy(itemSpread = it) }
        observeLevelUpProgress()
        observeLevelProgress()
        observeCompletionProjection()
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
        viewModelScope.launch {
            _dashboardData.update { it.copy(isRefreshing = true, errorMessage = null, isOffline = false) }

            syncOrchestrator.syncAll(force = true)

            val userResult = waniKaniRepository.fetchUser()
            val summaryResult = waniKaniRepository.fetchDashboardSummary()
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
                return@launch
            }
            if (summaryResult is ApiResult.Error) {
                if (hasContent) {
                    _dashboardData.update { it.copy(isRefreshing = false, isOffline = true) }
                } else {
                    _dashboardData.update { it.copy(isRefreshing = false, errorMessage = summaryResult.message) }
                }
                return@launch
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
    }

    /**
     * Called whenever the dashboard becomes visible again (e.g. returning from a finished
     * review/lesson session), so lesson/review counts don't sit stale until the next hourly
     * background sync or a manual pull-to-refresh. Deliberately lighter than [refresh]: reuses
     * [SyncOrchestrator]'s own per-resource staleness gate (`force = false`) rather than forcing a
     * full resync of everything, and never touches [DashboardUiState.isRefreshing] or
     * [DashboardUiState.errorMessage] — most calls will be near-instant no-ops, and a transient
     * failure here shouldn't blank out an already-populated dashboard, just flag it as
     * [DashboardUiState.isOffline].
     */
    fun onDashboardResumed() {
        viewModelScope.launch {
            syncOrchestrator.syncAll(force = false)

            val user = (waniKaniRepository.fetchUser() as? ApiResult.Success)?.data
            val summary = (waniKaniRepository.fetchDashboardSummary() as? ApiResult.Success)?.data
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

    fun logOut() {
        viewModelScope.launch {
            tokenRepository.clearToken()
            syncScheduler.cancelPeriodicSync()
            pitchAccentScrapeScheduler.cancelPeriodicScrape()
            notificationCoordinator.onLogout()
            _dashboardData.update { it.copy(isLoggedOut = true) }
        }
    }

    /** Guru'd-kanji progress toward leveling up — always scoped to the level currently being studied. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLevelUpProgress() {
        viewModelScope.launch {
            _dashboardData.map { it.level }.filterNotNull().distinctUntilChanged()
                .flatMapLatest { level -> assignmentRepository.observeLevelUpProgress(level) }
                .collect { levelUp ->
                    _dashboardData.update {
                        it.copy(kanjiGuruedForLevelUp = levelUp.kanjiGuruedOrHigher, kanjiTotalForLevelUp = levelUp.kanjiTotal)
                    }
                }
        }
    }

    /** Per-subject-type breakdown for the Level Progress card — defaults to the current level, but browsable. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLevelProgress() {
        viewModelScope.launch {
            combine(
                _dashboardData.map { it.level }.filterNotNull().distinctUntilChanged(),
                selectedProgressLevel
            ) { currentLevel, selected -> selected ?: currentLevel }
                .distinctUntilChanged()
                .flatMapLatest { level -> assignmentRepository.observeLevelProgress(level) }
                .collect { progress -> _dashboardData.update { it.copy(levelProgress = progress) } }
        }
    }

    private fun observeCompletionProjection() {
        viewModelScope.launch {
            combine(
                subjectRepository.observeTotalSubjectCount(),
                assignmentRepository.observeItemsSeenCount(),
                settingsRepository.settings
            ) { totalItems, itemsSeen, settings -> buildCompletionProjection(totalItems, itemsSeen, settings.dailyLessonGoal) }
                .collect { projection -> _dashboardData.update { it.copy(completionProjection = projection) } }
        }
    }

    private fun <T> observe(flow: Flow<T>, update: DashboardUiState.(T) -> DashboardUiState) {
        viewModelScope.launch { flow.collect { value -> _dashboardData.update { it.update(value) } } }
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
        projectedCompletionDate = LocalDate.now().plusDays(daysRemaining.toLong())
    )
}
