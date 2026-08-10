package com.crazyfluff.shellfstudy.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val daysOnCurrentLevel: Int? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val waniKaniRepository: WaniKaniRepository,
    private val tokenRepository: TokenRepository,
    reviewSessionRepository: ReviewSessionRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    // Holds the network-fetched dashboard data plus login state; hasActiveReviewSession and
    // dailyLessonGoal are overlaid from the repositories' flows below rather than stored here.
    private val _dashboardData = MutableStateFlow(DashboardUiState())

    val uiState: StateFlow<DashboardUiState> = combine(
        _dashboardData,
        reviewSessionRepository.hasActiveSession,
        settingsRepository.settings
    ) { data, hasActiveSession, settings ->
        data.copy(hasActiveReviewSession = hasActiveSession, dailyLessonGoal = settings.dailyLessonGoal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _dashboardData.update { it.copy(isLoading = true, errorMessage = null) }

            val userDeferred = async { waniKaniRepository.fetchUser() }
            val summaryDeferred = async { waniKaniRepository.fetchDashboardSummary() }
            val lessonsTodayDeferred = async { waniKaniRepository.fetchLessonsCompletedToday() }
            val daysOnLevelDeferred = async { waniKaniRepository.fetchDaysOnCurrentLevel() }
            val userResult = userDeferred.await()
            val summaryResult = summaryDeferred.await()

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
            val levelUpProgressResult = waniKaniRepository.fetchLevelUpProgress(user.level)
            // The "lessons done today" count, days-on-level, and Guru'd progress are nice-to-have
            // indicators, not core functionality — if any fails to load, fall back to a default
            // rather than blocking the whole dashboard on it.
            val lessonsToday = (lessonsTodayDeferred.await() as? ApiResult.Success)?.data ?: 0
            val daysOnLevel = (daysOnLevelDeferred.await() as? ApiResult.Success)?.data
            val levelUpProgress = (levelUpProgressResult as? ApiResult.Success)?.data

            _dashboardData.update {
                it.copy(
                    isLoading = false,
                    username = user.username,
                    level = user.level,
                    lessonCount = summary.lessonCount,
                    reviewCount = summary.reviewCount,
                    lessonsCompletedToday = lessonsToday,
                    daysOnCurrentLevel = daysOnLevel,
                    kanjiGuruedForLevelUp = levelUpProgress?.kanjiGuruedOrHigher ?: 0,
                    kanjiTotalForLevelUp = levelUpProgress?.kanjiTotal ?: 0
                )
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            tokenRepository.clearToken()
            _dashboardData.update { it.copy(isLoggedOut = true) }
        }
    }
}
