package com.crazyfluff.shellfstudy.shared.feature.leaderboard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.FriendEntry
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardRoute(
    onBack: () -> Unit,
    viewModel: LeaderboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    LeaderboardScreen(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::onRefresh,
        onAddFriendNicknameChange = viewModel::onAddFriendNicknameChange,
        onAddFriendTokenChange = viewModel::onAddFriendTokenChange,
        onAddFriendConfirm = viewModel::onAddFriendConfirm,
        onRemoveFriend = viewModel::onRemoveFriend
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    uiState: LeaderboardUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onAddFriendNicknameChange: (String) -> Unit,
    onAddFriendTokenChange: (String) -> Unit,
    onAddFriendConfirm: () -> Unit,
    onRemoveFriend: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var friendToDelete by remember { mutableStateOf<FriendEntry?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Friends") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddFriendDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add friend")
            }
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.padding(paddingValues)
        ) {
            if (uiState.friends.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No friends yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap + to add a friend's read-only API token.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 80.dp)
                ) {
                    uiState.friends.forEachIndexed { index, friend ->
                        if (index > 0) HorizontalDivider()
                        FriendManagementRow(
                            friend = friend,
                            onDelete = { friendToDelete = friend }
                        )
                    }
                }
            }
        }
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            nickname = uiState.addFriendNickname,
            token = uiState.addFriendToken,
            isValidating = uiState.addFriendValidating,
            error = uiState.addFriendError,
            onNicknameChange = onAddFriendNicknameChange,
            onTokenChange = onAddFriendTokenChange,
            onConfirm = {
                onAddFriendConfirm()
                if (uiState.addFriendError == null && !uiState.addFriendValidating) {
                    showAddFriendDialog = false
                }
            },
            onDismiss = { showAddFriendDialog = false }
        )
    }

    val entry = friendToDelete
    if (entry != null) {
        ConfirmationDialog(
            title = "Remove ${entry.nickname}?",
            text = "Their stats will be removed from your leaderboard.",
            confirmLabel = "Remove",
            onConfirm = { onRemoveFriend(entry.id); friendToDelete = null },
            onDismiss = { friendToDelete = null }
        )
    }
}

@Composable
private fun FriendManagementRow(
    friend: FriendEntry,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = friend.nickname,
                style = MaterialTheme.typography.bodyLarge
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove ${friend.nickname}",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun AddFriendDialog(
    nickname: String,
    token: String,
    isValidating: Boolean,
    error: String?,
    onNicknameChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a friend") },
        text = {
            Column {
                Text(
                    text = "Enter a nickname and their WaniKani read-only API token.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onNicknameChange,
                    label = { Text("Nickname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = onTokenChange,
                    label = { Text("API token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isValidating) {
                if (isValidating) {
                    CircularProgressIndicator(modifier = Modifier.height(16.dp).width(16.dp))
                } else {
                    Text("Validate & Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
