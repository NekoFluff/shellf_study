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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    val dailyLessonGoal: Int = 15
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val waniKaniRepository: WaniKaniRepository,
    private val tokenRepository: TokenRepository,
    private val reviewSessionRepository: ReviewSessionRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            reviewSessionRepository.hasActiveSession.collect { hasSession ->
                _uiState.update { it.copy(hasActiveReviewSession = hasSession) }
            }
        }
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update { it.copy(dailyLessonGoal = settings.dailyLessonGoal) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val userDeferred = async { waniKaniRepository.fetchUser() }
            val summaryDeferred = async { waniKaniRepository.fetchDashboardSummary() }
            val lessonsTodayDeferred = async { waniKaniRepository.fetchLessonsCompletedToday() }
            val userResult = userDeferred.await()
            val summaryResult = summaryDeferred.await()
            val lessonsTodayResult = lessonsTodayDeferred.await()

            if (userResult is ApiResult.Error) {
                _uiState.update { it.copy(isLoading = false, errorMessage = userResult.message) }
                return@launch
            }
            if (summaryResult is ApiResult.Error) {
                _uiState.update { it.copy(isLoading = false, errorMessage = summaryResult.message) }
                return@launch
            }

            val user = (userResult as ApiResult.Success).data
            val summary = (summaryResult as ApiResult.Success).data
            // The "lessons done today" count is a nice-to-have indicator, not core functionality —
            // if it fails to load, default to 0 rather than blocking the whole dashboard on it.
            val lessonsToday = (lessonsTodayResult as? ApiResult.Success)?.data ?: 0

            _uiState.update {
                it.copy(
                    isLoading = false,
                    username = user.username,
                    level = user.level,
                    lessonCount = summary.lessonCount,
                    reviewCount = summary.reviewCount,
                    lessonsCompletedToday = lessonsToday
                )
            }
        }
    }

    fun logOut() {
        viewModelScope.launch {
            tokenRepository.clearToken()
            _uiState.update { it.copy(isLoggedOut = true) }
        }
    }
}
