package com.crazyfluff.shellfstudy.shared.feature.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.model.friendRosterIndex
import com.crazyfluff.shellfstudy.shared.designsystem.components.ListGroup
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.shared.designsystem.friends.FriendDetailsSheet
import com.crazyfluff.shellfstudy.shared.designsystem.friends.FriendDetailsSubject
import com.crazyfluff.shellfstudy.shared.designsystem.theme.leaderboardUserColor
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LeaderboardRoute(
    onBack: () -> Unit,
    viewModel: LeaderboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LeaderboardScreen(uiState = uiState, actions = viewModel, onBack = onBack)
}

/** The friend a panel or dialog is open for: the roster entry plus what the list showed for it. */
private data class OpenFriend(val entry: FriendEntry, val subject: FriendDetailsSubject)

/**
 * Friends: adding them, and renaming or removing them. Rankings are the dashboard card's job, so
 * this page is the roster in the order friends were added, with each friend's details a tap away.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    uiState: LeaderboardUiState,
    actions: LeaderboardActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var openFriend by remember { mutableStateOf<OpenFriend?>(null) }
    var friendToRemove by remember { mutableStateOf<FriendEntry?>(null) }
    var friendToRename by remember { mutableStateOf<FriendEntry?>(null) }

    LaunchedEffect(uiState.addFriendForm.success) {
        if (uiState.addFriendForm.success) showAddFriendDialog = false
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddFriendDialog = true },
                        modifier = Modifier.testTag(LeaderboardScreenTestTags.ADD_FRIEND_BUTTON)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add a friend")
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = actions::onRefresh,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Always the list, even with no friends (then it's just the "Add a friend" row). A
            // separate empty page flashed on every open: the roster is read asynchronously, so
            // the first frame always saw an empty list.
            FriendsContent(
                uiState = uiState,
                onOpenFriend = { entry, color ->
                    val subject = FriendDetailsSubject(entry.nickname, color, uiState.statsByFriendId[entry.id])
                    openFriend = OpenFriend(entry, subject)
                },
                onAddFriend = { showAddFriendDialog = true }
            )
        }
    }

    FriendDialogs(
        uiState = uiState,
        actions = actions,
        showAddFriendDialog = showAddFriendDialog,
        onDismissAdd = { showAddFriendDialog = false },
        openFriend = openFriend,
        onCloseFriend = { openFriend = null },
        friendToRemove = friendToRemove,
        onRemoveChange = { friendToRemove = it },
        friendToRename = friendToRename,
        onRenameChange = { friendToRename = it }
    )
}

/** Everything the page opens over itself: the add dialog, a friend's panel, and the rename and
 *  remove dialogs the panel leads to. */
@Suppress("LongParameterList")
@Composable
private fun FriendDialogs(
    uiState: LeaderboardUiState,
    actions: LeaderboardActions,
    showAddFriendDialog: Boolean,
    onDismissAdd: () -> Unit,
    openFriend: OpenFriend?,
    onCloseFriend: () -> Unit,
    friendToRemove: FriendEntry?,
    onRemoveChange: (FriendEntry?) -> Unit,
    friendToRename: FriendEntry?,
    onRenameChange: (FriendEntry?) -> Unit
) {
    if (showAddFriendDialog) {
        AddFriendDialog(form = uiState.addFriendForm, actions = actions, onDismiss = onDismissAdd)
    }
    openFriend?.let { open ->
        FriendDetailsSheet(
            subject = open.subject,
            window = LeaderboardWindow.WEEK,
            onDismiss = onCloseFriend,
            onRename = { onCloseFriend(); onRenameChange(open.entry) },
            onRemove = { onCloseFriend(); onRemoveChange(open.entry) }
        )
    }
    friendToRemove?.let { entry ->
        ConfirmationDialog(
            title = "Remove ${entry.nickname}?",
            text = "They'll disappear from your leaderboard. You can add them again with their token.",
            confirmLabel = "Remove",
            onConfirm = {
                actions.onRemoveFriend(entry.id)
                onRemoveChange(null)
            },
            onDismiss = { onRemoveChange(null) },
            confirmButtonTestTag = LeaderboardScreenTestTags.REMOVE_CONFIRM
        )
    }
    friendToRename?.let { entry ->
        EditNicknameDialog(
            initialNickname = entry.nickname,
            onConfirm = { nickname ->
                actions.onEditNickname(entry.id, nickname)
                onRenameChange(null)
            },
            onDismiss = { onRenameChange(null) }
        )
    }
}

@Composable
private fun FriendsContent(
    uiState: LeaderboardUiState,
    onOpenFriend: (FriendEntry, Color) -> Unit,
    onAddFriend: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        uiState.refreshErrorMessage?.let { message -> item(key = "error") { ErrorBanner(message) } }
        item(key = "friends") {
            ListGroup(title = "Friends") {
                uiState.friends.forEachIndexed { index, friend ->
                    // Roster order is the colour order, the same one the dashboard card uses.
                    val color = leaderboardUserColor(friendRosterIndex(index))
                    FriendRow(friend.id, friend.nickname, uiState.statsByFriendId[friend.id], color) {
                        onOpenFriend(friend, color)
                    }
                }
                AddFriendRow(onClick = onAddFriend)
            }
        }
        if (uiState.isRosterLoaded) {
            item(key = "footer") { FriendsFooter(friendCount = uiState.friends.size) }
        }
    }
}

@Composable
private fun FriendsFooter(friendCount: Int) {
    Text(
        text = if (friendCount == 0) {
            TOKEN_HELP
        } else {
            "${friendCount.friendsLabel()} · pull down to refresh everyone's stats"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
    )
}

private fun Int.friendsLabel(): String = "$this ${if (this == 1) "friend" else "friends"}"

/** Shown under the list until the first friend is added: how to get the token the add dialog asks for. */
private const val TOKEN_HELP =
    "Friends appear on your dashboard's leaderboard. To add one, ask them to open " +
        "wanikani.com/settings/personal_access_tokens, generate a token with no boxes ticked (that " +
        "makes it read-only), and send it to you. It's stored encrypted on this device."
