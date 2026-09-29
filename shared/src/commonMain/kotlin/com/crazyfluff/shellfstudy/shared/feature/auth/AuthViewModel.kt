package com.crazyfluff.shellfstudy.shared.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.shared.data.isAuthError
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val tokenInput: String = "",
    val step: AuthStep = AuthStep.Editing()
) {
    val isSubmitting: Boolean get() = step == AuthStep.Submitting
    val errorMessage: String? get() = (step as? AuthStep.Editing)?.error
    val pendingNotificationRequest: Boolean get() = step == AuthStep.RequestingNotifications
    val isAuthenticated: Boolean get() = step == AuthStep.Authenticated
}

/** Where sign-in is: one at a time, in this order. */
sealed interface AuthStep {
    /** Entering a token, showing why the last attempt failed if it did. */
    data class Editing(val error: String? = null) : AuthStep

    /** The token is being checked against WaniKani. */
    data object Submitting : AuthStep

    /** Signed in; the notification permission prompt is up. */
    data object RequestingNotifications : AuthStep

    /** Done — the screen navigates on. */
    data object Authenticated : AuthStep
}

class AuthViewModel(
    private val tokenRepository: TokenRepository,
    private val waniKaniRepository: WaniKaniRepository,
    private val syncScheduler: SyncScheduler,
    private val notificationCoordinator: NotificationCoordinator,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onTokenInputChange(value: String) {
        _uiState.update { it.copy(tokenInput = value, step = AuthStep.Editing()) }
    }

    fun submitToken() {
        val token = _uiState.value.tokenInput.trim()
        if (token.isEmpty()) {
            _uiState.update { it.copy(step = AuthStep.Editing(error = "Enter your WaniKani API token.")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(step = AuthStep.Submitting) }
            tokenRepository.saveToken(token)
            when (val result = waniKaniRepository.fetchUser()) {
                is ApiResult.Success -> {
                    syncScheduler.schedulePeriodicSync()
                    notificationCoordinator.onLogin()
                    _uiState.update { it.copy(step = AuthStep.RequestingNotifications) }
                }
                is ApiResult.Error -> {
                    if (result.isAuthError) tokenRepository.clearToken()
                    _uiState.update { it.copy(step = AuthStep.Editing(error = result.message)) }
                }
            }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            settingsRepository.setNotificationsEnabled(granted)
            _uiState.update { it.copy(step = AuthStep.Authenticated) }
        }
    }
}
