package com.crazyfluff.shellfstudy.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.core.notifications.NotificationCoordinator
import com.crazyfluff.shellfstudy.core.sync.PitchAccentScrapeScheduler
import com.crazyfluff.shellfstudy.core.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SplashDestination { AUTH, DASHBOARD }

data class SplashUiState(val destination: SplashDestination? = null)

/**
 * Decides AUTH vs. DASHBOARD from local token presence only — no network call — so a returning
 * user with a stored token never waits on (or is silently logged out by) a startup validation
 * round-trip. Dashboard's own refresh() is the real validation point once past here.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
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
