package com.crazyfluff.shellfstudy.shared.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.shared.data.isAuthError
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.sync.PitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.shared.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val tokenInput: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val pendingNotificationRequest: Boolean = false,
    val isAuthenticated: Boolean = false
)

class AuthViewModel(
    private val tokenRepository: TokenRepository,
    private val waniKaniRepository: WaniKaniRepository,
    private val syncScheduler: SyncScheduler,
    private val pitchAccentScrapeScheduler: PitchAccentScrapeScheduler,
    private val notificationCoordinator: NotificationCoordinator,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onTokenInputChange(value: String) {
        _uiState.update { it.copy(tokenInput = value, errorMessage = null) }
    }

    fun submitToken() {
        val token = _uiState.value.tokenInput.trim()
        if (token.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Enter your WaniKani API token.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            tokenRepository.saveToken(token)
            when (val result = waniKaniRepository.fetchUser()) {
                is ApiResult.Success -> {
                    syncScheduler.schedulePeriodicSync()
                    pitchAccentScrapeScheduler.schedulePeriodicScrape()
                    notificationCoordinator.onLogin()
                    _uiState.update { it.copy(isSubmitting = false, pendingNotificationRequest = true) }
                }
                is ApiResult.Error -> {
                    if (result.isAuthError) tokenRepository.clearToken()
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
            }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(granted)
            _uiState.update { it.copy(pendingNotificationRequest = false, isAuthenticated = true) }
        }
    }
}
