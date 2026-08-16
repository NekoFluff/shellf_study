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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    val selectedMetric: LeaderboardMetric = LeaderboardMetric.LEARNED,
    val selectedWindow: LeaderboardWindow = LeaderboardWindow.WEEK
)

private data class FormState(
    val isRefreshing: Boolean = false,
    val addFriendNickname: String = "",
    val addFriendToken: String = "",
    val addFriendValidating: Boolean = false,
    val addFriendError: String? = null,
    val selectedMetric: LeaderboardMetric = LeaderboardMetric.LEARNED,
    val selectedWindow: LeaderboardWindow = LeaderboardWindow.WEEK
)

class LeaderboardViewModel(
    private val friendRepository: FriendRepository,
    private val friendStatsRepository: FriendStatsRepository,
    private val json: Json
) : ViewModel() {

    private val _formState = MutableStateFlow(FormState())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val leaderboardFlow = _formState
        .flatMapLatest { form ->
            friendStatsRepository.observeLeaderboard(form.selectedMetric, form.selectedWindow)
        }

    val uiState: StateFlow<LeaderboardUiState> = combine(
        _formState,
        friendRepository.friendsFlow,
        leaderboardFlow
    ) { form, friends, leaderboard ->
        LeaderboardUiState(
            leaderboard = leaderboard,
            friends = friends,
            isRefreshing = form.isRefreshing,
            addFriendNickname = form.addFriendNickname,
            addFriendToken = form.addFriendToken,
            addFriendValidating = form.addFriendValidating,
            addFriendError = form.addFriendError,
            selectedMetric = form.selectedMetric,
            selectedWindow = form.selectedWindow
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaderboardUiState())

    fun onRefresh() {
        viewModelScope.launch {
            _formState.update { it.copy(isRefreshing = true) }
            val friends = friendRepository.friendsFlow
            friends.collect { entries ->
                entries.forEach { entry -> friendStatsRepository.refreshFriend(entry) }
                _formState.update { it.copy(isRefreshing = false) }
                return@collect
            }
        }
    }

    fun onMetricChange(metric: LeaderboardMetric) {
        _formState.update { it.copy(selectedMetric = metric) }
    }

    fun onWindowChange(window: LeaderboardWindow) {
        _formState.update { it.copy(selectedWindow = window) }
    }

    fun onAddFriendNicknameChange(value: String) {
        _formState.update { it.copy(addFriendNickname = value, addFriendError = null) }
    }

    fun onAddFriendTokenChange(value: String) {
        _formState.update { it.copy(addFriendToken = value, addFriendError = null) }
    }

    fun onAddFriendConfirm() {
        val nickname = _formState.value.addFriendNickname.trim()
        val token = _formState.value.addFriendToken.trim()
        if (nickname.isBlank()) {
            _formState.update { it.copy(addFriendError = "Please enter a nickname.") }
            return
        }
        if (token.isBlank()) {
            _formState.update { it.copy(addFriendError = "Please enter an API token.") }
            return
        }
        viewModelScope.launch {
            _formState.update { it.copy(addFriendValidating = true, addFriendError = null) }
            val api = createFriendWaniKaniApi(token, json)
            val result = safeApiCall { api.getUser() }
            when (result) {
                is ApiResult.Success -> {
                    val entry = friendRepository.addFriend(nickname, token)
                    friendStatsRepository.refreshFriend(entry)
                    _formState.update {
                        it.copy(
                            addFriendValidating = false,
                            addFriendNickname = "",
                            addFriendToken = "",
                            addFriendError = null
                        )
                    }
                }
                is ApiResult.Error -> {
                    _formState.update {
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
}
