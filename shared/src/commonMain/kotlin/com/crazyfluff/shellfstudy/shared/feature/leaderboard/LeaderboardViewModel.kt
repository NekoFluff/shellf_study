package com.crazyfluff.shellfstudy.shared.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.safeApiCall
import com.crazyfluff.shellfstudy.shared.network.createFriendWaniKaniApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class LeaderboardUiState(
    val leaderboard: Leaderboard? = null,
    val friends: List<FriendEntry> = emptyList(),
    val isRefreshing: Boolean = false,
    val addFriendNickname: String = "",
    val addFriendToken: String = "",
    val addFriendValidating: Boolean = false,
    val addFriendError: String? = null,
    val addFriendSuccess: Boolean = false,
    val selectedMetric: LeaderboardMetric = LeaderboardMetric.LEARNED,
    val selectedWindow: LeaderboardWindow = LeaderboardWindow.WEEK
)

class LeaderboardViewModel(
    private val friendRepository: FriendRepository,
    private val friendStatsRepository: FriendStatsRepository,
    private val json: Json
) : ViewModel() {

    private val _uiState = MutableStateFlow(LeaderboardUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val leaderboardFlow = _uiState
        .map { it.selectedMetric to it.selectedWindow }
        .distinctUntilChanged()
        .flatMapLatest { (metric, window) ->
            friendStatsRepository.observeLeaderboard(metric, window)
        }

    val uiState: StateFlow<LeaderboardUiState> = combine(
        _uiState,
        friendRepository.friendsFlow,
        leaderboardFlow
    ) { state, friends, leaderboard ->
        state.copy(leaderboard = leaderboard, friends = friends)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaderboardUiState())

    fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            val entries = friendRepository.friendsFlow.first()
            coroutineScope {
                entries.map { entry -> async { friendStatsRepository.refreshFriend(entry) } }.awaitAll()
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun onMetricChange(metric: LeaderboardMetric) {
        _uiState.update { it.copy(selectedMetric = metric) }
    }

    fun onWindowChange(window: LeaderboardWindow) {
        _uiState.update { it.copy(selectedWindow = window) }
    }

    fun onAddFriendNicknameChange(value: String) {
        _uiState.update { it.copy(addFriendNickname = value, addFriendError = null, addFriendSuccess = false) }
    }

    fun onAddFriendTokenChange(value: String) {
        _uiState.update { it.copy(addFriendToken = value, addFriendError = null) }
    }

    fun onAddFriendConfirm() {
        val nickname = _uiState.value.addFriendNickname.trim()
        val token = _uiState.value.addFriendToken.trim()
        if (nickname.isBlank()) {
            _uiState.update { it.copy(addFriendError = "Please enter a nickname.") }
            return
        }
        if (token.isBlank()) {
            _uiState.update { it.copy(addFriendError = "Please enter an API token.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(addFriendValidating = true, addFriendError = null) }
            val api = createFriendWaniKaniApi(token, json)
            val result = safeApiCall { api.getUser() }
            when (result) {
                is ApiResult.Success -> {
                    val entry = friendRepository.addFriend(nickname, token)
                    friendStatsRepository.refreshFriend(entry)
                    _uiState.update {
                        it.copy(
                            addFriendValidating = false,
                            addFriendNickname = "",
                            addFriendToken = "",
                            addFriendError = null,
                            addFriendSuccess = true
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(addFriendValidating = false, addFriendError = result.message)
                    }
                }
            }
        }
    }

    fun onRemoveFriend(id: String) {
        viewModelScope.launch {
            friendRepository.removeFriend(id)
            friendStatsRepository.removeFriendCache(id)
        }
    }

    fun onEditNickname(id: String, nickname: String) {
        val trimmed = nickname.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            friendRepository.updateNickname(id, trimmed)
        }
    }
}
