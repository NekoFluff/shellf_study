package com.crazyfluff.shellfstudy.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val tokenInput: String = "",
    val isCheckingStoredToken: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false
) {
    val isLoading: Boolean get() = isCheckingStoredToken || isSubmitting
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val tokenRepository: TokenRepository,
    private val waniKaniRepository: WaniKaniRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existingToken = tokenRepository.tokenFlow.first()
            if (existingToken.isNullOrBlank()) {
                _uiState.update { it.copy(isCheckingStoredToken = false) }
            } else {
                validateStoredToken()
            }
        }
    }

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
                    _uiState.update { it.copy(isSubmitting = false, isAuthenticated = true) }
                }
                is ApiResult.Error -> {
                    tokenRepository.clearToken()
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = result.message) }
                }
            }
        }
    }

    private suspend fun validateStoredToken() {
        _uiState.update { it.copy(isCheckingStoredToken = true) }
        when (val result = waniKaniRepository.fetchUser()) {
            is ApiResult.Success -> {
                syncScheduler.schedulePeriodicSync()
                _uiState.update { it.copy(isCheckingStoredToken = false, isAuthenticated = true) }
            }
            is ApiResult.Error -> {
                tokenRepository.clearToken()
                _uiState.update { it.copy(isCheckingStoredToken = false) }
            }
        }
    }
}
