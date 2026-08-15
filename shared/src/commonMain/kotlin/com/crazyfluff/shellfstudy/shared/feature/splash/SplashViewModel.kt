package com.crazyfluff.shellfstudy.shared.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.shared.sync.PitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.shared.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SplashDestination { AUTH, DASHBOARD }

data class SplashUiState(val destination: SplashDestination? = null)

class SplashViewModel(
    private val tokenRepository: TokenRepository,
    private val syncScheduler: SyncScheduler,
    private val pitchAccentScrapeScheduler: PitchAccentScrapeScheduler,
    private val notificationCoordinator: NotificationCoordinator
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val token = tokenRepository.tokenFlow.first()
            if (token.isNullOrBlank()) {
                _uiState.update { it.copy(destination = SplashDestination.AUTH) }
            } else {
                syncScheduler.schedulePeriodicSync()
                pitchAccentScrapeScheduler.schedulePeriodicScrape()
                notificationCoordinator.onLogin()
                _uiState.update { it.copy(destination = SplashDestination.DASHBOARD) }
            }
        }
    }
}
