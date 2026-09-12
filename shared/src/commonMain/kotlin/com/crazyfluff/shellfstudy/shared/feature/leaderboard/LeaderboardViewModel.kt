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

/** The "add a friend" dialog's form — one value rather than five separate fields, since every
 *  mutator writes and clears them together (see [LeaderboardViewModel.onAddFriendNicknameChange]
 *  et al.); a field-by-field reset was easy to get half-right as the flow grew. */
data class AddFriendFormState(
    val nickname: String = "",
    val token: String = "",
    val isValidating: Boolean = false,
    val error: String? = null,
    val success: Boolean = false
)

data class LeaderboardUiState(
    val leaderboard: Leaderboard? = null,
    val friends: List<FriendEntry> = emptyList(),
    val isRefreshing: Boolean = false,
    val addFriendForm: AddFriendFormState = AddFriendFormState(),
    val selectedMetric: LeaderboardMetric = LeaderboardMetric.LEARNED,
    val selectedWindow: LeaderboardWindow = LeaderboardWindow.WEEK,
    val refreshErrorMessage: String? = null
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
            _uiState.update { it.copy(isRefreshing = true, refreshErrorMessage = null) }
            val entries = friendRepository.friendsFlow.first()
            val results = coroutineScope {
                entries.map { entry -> async { friendStatsRepository.refreshFriend(entry) } }.awaitAll()
            }
            val failedCount = results.count { it is ApiResult.Error }
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    refreshErrorMessage = if (failedCount > 0) {
                        "Couldn't refresh $failedCount ${if (failedCount == 1) "friend" else "friends"}."
                    } else {
                        null
                    }
                )
            }
        }
    }

    fun onMetricChange(metric: LeaderboardMetric) {
        _uiState.update { it.copy(selectedMetric = metric) }
    }

    fun onWindowChange(window: LeaderboardWindow) {
        _uiState.update { it.copy(selectedWindow = window) }
    }

    fun onAddFriendNicknameChange(value: String) {
        _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(nickname = value, error = null, success = false)) }
    }

    fun onAddFriendTokenChange(value: String) {
        _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(token = value, error = null)) }
    }

    fun onAddFriendConfirm() {
        val nickname = _uiState.value.addFriendForm.nickname.trim()
        val token = _uiState.value.addFriendForm.token.trim()
        if (nickname.isBlank()) {
            _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(error = "Please enter a nickname.")) }
            return
        }
        if (token.isBlank()) {
            _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(error = "Please enter an API token.")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(isValidating = true, error = null)) }
            val api = createFriendWaniKaniApi(token, json)
            val result = safeApiCall { api.getUser() }
            when (result) {
                is ApiResult.Success -> {
                    val entry = friendRepository.addFriend(nickname, token)
                    val refreshResult = friendStatsRepository.refreshFriend(entry)
                    _uiState.update {
                        it.copy(
                            addFriendForm = AddFriendFormState(isValidating = false, success = true),
                            refreshErrorMessage = if (refreshResult is ApiResult.Error) {
                                "Added $nickname, but couldn't fetch their stats yet."
                            } else {
                                it.refreshErrorMessage
                            }
                        )
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(addFriendForm = it.addFriendForm.copy(isValidating = false, error = result.message))
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
