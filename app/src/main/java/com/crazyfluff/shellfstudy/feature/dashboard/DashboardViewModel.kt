package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.StatsRepository
import com.crazyfluff.shellfstudy.core.data.SubjectRepository
import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.data.model.CompletionProjection
import com.crazyfluff.shellfstudy.core.data.model.ItemSpread
import com.crazyfluff.shellfstudy.core.data.model.LevelProgress
import com.crazyfluff.shellfstudy.core.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.core.data.model.ReviewsCompletedStats
import com.crazyfluff.shellfstudy.core.data.model.StudyStreak
import com.crazyfluff.shellfstudy.core.sync.SyncOrchestrator
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.ceil

data class DashboardUiState(
    val isLoading: Boolean = true,
    val username: String? = null,
    val level: Int? = null,
    val lessonCount: Int = 0,
    val reviewCount: Int = 0,
    val errorMessage: String? = null,
    val isLoggedOut: Boolean = false,
    val hasActiveReviewSession: Boolean = false,
    val lessonsCompletedToday: Int = 0,
    val dailyLessonGoal: Int = 15,
    val kanjiGuruedForLevelUp: Int = 0,
    val kanjiTotalForLevelUp: Int = 0,
    val daysOnCurrentLevel: Int? = null,
    val reviewForecast: ReviewForecast? = null,
    val studyStreak: StudyStreak? = null,
    val reviewsCompletedStats: ReviewsCompletedStats? = null,
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
    private val syncOrchestrator: SyncOrchestrator,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _dashboardData = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _dashboardData.asStateFlow()

    init {
        refresh()

        observe(reviewSessionRepository.hasActiveSession) { copy(hasActiveReviewSession = it) }
        observe(settingsRepository.settings) { copy(dailyLessonGoal = it.dailyLessonGoal) }
        observe(assignmentRepository.observeLessonsCompletedToday()) { copy(lessonsCompletedToday = it) }
        observe(statsRepository.observeDaysOnCurrentLevel()) { copy(daysOnCurrentLevel = it) }
        observe(assignmentRepository.observeReviewForecast()) { copy(reviewForecast = it) }
        observe(assignmentRepository.observeSrsItemSpread()) { copy(itemSpread = it) }
        observe(statsRepository.observeStudyStreak()) { copy(studyStreak = it) }
        observe(statsRepository.observeReviewsCompletedStats()) { copy(reviewsCompletedStats = it) }
        observeLevelScopedStats()
        observeCompletionProjection()
    }

    fun refresh() {
        viewModelScope.launch {
            _dashboardData.update { it.copy(isLoading = true, errorMessage = null) }

            syncOrchestrator.syncAll(force = true)

            val userResult = waniKaniRepository.fetchUser()
            val summaryResult = waniKaniRepository.fetchDashboardSummary()

            if (userResult is ApiResult.Error) {
                _dashboardData.update { it.copy(isLoading = false, errorMessage = userResult.message) }
                return@launch
            }
            if (summaryResult is ApiResult.Error) {
                _dashboardData.update { it.copy(isLoading = false, errorMessage = summaryResult.message) }
                return@launch
            }

            val user = (userResult as ApiResult.Success).data
            val summary = (summaryResult as ApiResult.Success).data
            _dashboardData.update {
                it.copy(
                    isLoading = false,
                    username = user.username,
                    level = user.level,
                    lessonCount = summary.lessonCount,
                    reviewCount = summary.reviewCount
                )
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            tokenRepository.clearToken()
            syncScheduler.cancelPeriodicSync()
            _dashboardData.update { it.copy(isLoggedOut = true) }
        }
    }

    /** Guru'd-kanji progress and per-type item breakdown, both scoped to whichever level loads in. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeLevelScopedStats() {
        viewModelScope.launch {
            _dashboardData.map { it.level }.filterNotNull().distinctUntilChanged().flatMapLatest { level ->
                combine(
                    assignmentRepository.observeLevelUpProgress(level),
                    assignmentRepository.observeLevelProgress(level)
                ) { levelUp, progress -> levelUp to progress }
            }.collect { (levelUp, progress) ->
                _dashboardData.update {
                    it.copy(
                        kanjiGuruedForLevelUp = levelUp.kanjiGuruedOrHigher,
                        kanjiTotalForLevelUp = levelUp.kanjiTotal,
                        levelProgress = progress
                    )
                }
            }
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
