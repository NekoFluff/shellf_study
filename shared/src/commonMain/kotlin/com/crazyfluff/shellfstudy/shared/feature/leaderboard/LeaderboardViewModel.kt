package com.crazyfluff.shellfstudy.shared.feature.leaderboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.FriendAdded
import com.crazyfluff.shellfstudy.shared.data.FriendRepository
import com.crazyfluff.shellfstudy.shared.data.FriendStatsRepository
import com.crazyfluff.shellfstudy.shared.data.RosterWrite
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.safeApiCall
import com.crazyfluff.shellfstudy.shared.network.createFriendWaniKaniApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val status: AddFriendStatus = AddFriendStatus.Editing()
) {
    val isValidating: Boolean get() = status == AddFriendStatus.Validating
    val error: String? get() = (status as? AddFriendStatus.Editing)?.error
    val success: Boolean get() = status == AddFriendStatus.Added
}

sealed interface AddFriendStatus {
    /** Filling the form in, showing why the last attempt failed if it did. */
    data class Editing(val error: String? = null) : AddFriendStatus

    /** The friend's token is being checked against WaniKani. */
    data object Validating : AddFriendStatus

    /** Saved — the dialog closes. */
    data object Added : AddFriendStatus
}

data class LeaderboardUiState(
    /** The roster, in the order friends were added: the order the page lists them in. */
    val friends: List<FriendEntry> = emptyList(),
    /** False until the roster has been read. Before that, an empty [friends] means "not known yet",
     *  not "no friends", so the page holds back anything that depends on the count. */
    val isRosterLoaded: Boolean = false,
    /** Each friend's cached figures, by [FriendEntry.id]. A friend with no entry hasn't had their
     *  stats fetched yet (just added, or every fetch so far failed). */
    val statsByFriendId: Map<String, FriendStats> = emptyMap(),
    val isRefreshing: Boolean = false,
    val addFriendForm: AddFriendFormState = AddFriendFormState(),
    val refreshErrorMessage: String? = null
)

class LeaderboardViewModel(
    private val friendRepository: FriendRepository,
    private val friendStatsRepository: FriendStatsRepository,
    private val json: Json
) : ViewModel(), LeaderboardActions {

    private val _uiState = MutableStateFlow(LeaderboardUiState())

    // The leaderboard is the one place the repository joins the roster to the cached stats, so it's
    // read here for the per-friend figures. Its ranking isn't used: this page lists, it doesn't rank.
    val uiState: StateFlow<LeaderboardUiState> = combine(
        _uiState,
        friendRepository.friendsFlow,
        friendStatsRepository.observeLeaderboard()
    ) { state, friends, leaderboard ->
        state.copy(
            friends = friends,
            isRosterLoaded = true,
            statsByFriendId = leaderboard?.entries.orEmpty()
                .filterNot { it.isCurrentUser }
                .associateBy { it.friendEntryId }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LeaderboardUiState())

    override fun onRefresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, refreshErrorMessage = null) }
            // force = true: a pull-to-refresh is the user asking for current figures, so the TTL that
            // governs the dashboard's background refresh does not apply. The roster read, the fan-out
            // and the per-friend failure collection all belong to the repository — this method used to
            // re-implement them, which is how the two callers ended up disagreeing about whether a
            // friend that failed to refresh should be reported at all.
            val failures = friendStatsRepository.refreshAllIfStale(force = true)
            _uiState.update {
                it.copy(
                    isRefreshing = false,
                    refreshErrorMessage = if (failures.isEmpty()) {
                        null
                    } else {
                        "Couldn't refresh ${failures.size} ${if (failures.size == 1) "friend" else "friends"}."
                    }
                )
            }
        }
    }

    override fun onAddFriendNicknameChange(value: String) {
        _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(nickname = value, status = AddFriendStatus.Editing())) }
    }

    override fun onAddFriendTokenChange(value: String) {
        _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(token = value, status = AddFriendStatus.Editing())) }
    }

    override fun onAddFriendConfirm() {
        val nickname = _uiState.value.addFriendForm.nickname.trim()
        val token = _uiState.value.addFriendForm.token.trim()
        if (nickname.isBlank()) {
            _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(status = AddFriendStatus.Editing(error = "Please enter a nickname."))) }
            return
        }
        if (token.isBlank()) {
            _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(status = AddFriendStatus.Editing(error = "Please enter an API token."))) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(addFriendForm = it.addFriendForm.copy(status = AddFriendStatus.Validating)) }
            val api = createFriendWaniKaniApi(token, json)
            // Closed in a finally: this is a throw-away client for one validation call, and without
            // it every add-friend attempt leaks an engine's connection pool and dispatcher threads.
            val result = try {
                safeApiCall { api.getUser() }
            } finally {
                api.close()
            }
            when (result) {
                is ApiResult.Success -> when (val added = friendRepository.addFriend(nickname, token)) {
                    is FriendAdded.RosterUnreadable -> {
                        _uiState.update {
                            it.copy(
                                addFriendForm = it.addFriendForm.copy(
                                    status = AddFriendStatus.Editing(error = ROSTER_UNREADABLE_MESSAGE)
                                )
                            )
                        }
                    }
                    is FriendAdded.Saved -> {
                        val refreshResult = friendStatsRepository.refreshFriend(added.entry)
                        _uiState.update {
                            it.copy(
                                addFriendForm = AddFriendFormState(status = AddFriendStatus.Added),
                                refreshErrorMessage = if (refreshResult is ApiResult.Error) {
                                    "Added $nickname, but couldn't fetch their stats yet."
                                } else {
                                    it.refreshErrorMessage
                                }
                            )
                        }
                    }
                }
                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(addFriendForm = it.addFriendForm.copy(status = AddFriendStatus.Editing(error = result.message)))
                    }
                }
            }
        }
    }

    override fun onRemoveFriend(id: String) {
        viewModelScope.launch {
            when (friendRepository.removeFriend(id)) {
                // Only drop the cached stats once the roster write actually landed — otherwise the
                // friend would stay in the roster with their stats deleted behind them.
                RosterWrite.Saved -> friendStatsRepository.removeFriendCache(id)
                RosterWrite.RosterUnreadable ->
                    _uiState.update { it.copy(refreshErrorMessage = ROSTER_UNREADABLE_MESSAGE) }
            }
        }
    }

    override fun onEditNickname(id: String, nickname: String) {
        val trimmed = nickname.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            if (friendRepository.updateNickname(id, trimmed) is RosterWrite.RosterUnreadable) {
                _uiState.update { it.copy(refreshErrorMessage = ROSTER_UNREADABLE_MESSAGE) }
            }
        }
    }
}

/** Shown when a roster edit was refused because what is on disk can't be decoded — the edit was
 *  dropped rather than saved over the unreadable roster. */
private const val ROSTER_UNREADABLE_MESSAGE =
    "Couldn't save your friends list — the data already on this device can't be read."
