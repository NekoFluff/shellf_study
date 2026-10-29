package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.components.AppModalBottomSheet
import com.crazyfluff.shellfstudy.shared.util.rememberRelativeTime
import kotlinx.coroutines.delay

/*
 * How the dashboard reports syncing. Routine activity is a thin line under the top bar and a chip
 * under the greeting, neither of which moves the cards; banners are left for what needs attention.
 * The chip opens a sheet that says, for each thing that syncs, where it stands.
 */

/**
 * A sync pass usually takes well under a second, too short to register; holding the indicator this
 * long after the work stops is what makes it visible at all.
 */
private const val SYNC_INDICATOR_HOLD_MS = 700L

/** [active], kept true for [holdMs] after it turns false. */
@Composable
private fun rememberHeld(active: Boolean, holdMs: Long = SYNC_INDICATOR_HOLD_MS): Boolean {
    var shown by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        if (active) {
            shown = true
        } else {
            delay(holdMs)
            shown = false
        }
    }
    return shown
}

/** An indeterminate line along the top of the content while anything syncs. Overlays the list. */
@Composable
internal fun SyncProgressLine(isSyncing: Boolean, modifier: Modifier = Modifier) {
    if (rememberHeld(isSyncing)) {
        LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(2.dp)
                .testTag(DashboardScreenTestTags.SYNC_PROGRESS_LINE)
        )
    }
}

@Composable
internal fun SyncStatusChip(state: SyncChipState, onClick: () -> Unit) {
    val showSyncing = rememberHeld(state == SyncChipState.Syncing)
    val lastSyncedAtMillis = (state as? SyncChipState.Synced)?.lastSyncedAtMillis
    val syncedAgo = rememberRelativeTime(lastSyncedAtMillis)
    val label = when {
        showSyncing -> "Syncing…"
        state == SyncChipState.Problem -> "Sync issue"
        lastSyncedAtMillis != null -> "Synced $syncedAgo"
        else -> "Sync status"
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            when {
                showSyncing -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                state == SyncChipState.Problem -> Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
                else -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        },
        modifier = Modifier.testTag(DashboardScreenTestTags.SYNC_CHIP)
    )
}

private enum class SyncTone { Busy, Ok, Problem, Neutral }

/** Where each kind of sync stands: WaniKani data, answers on their way to WaniKani, other devices. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SyncStatusSheet(
    uiState: DashboardUiState,
    onRefresh: () -> Unit,
    onRetryDeviceSync: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    // Opens fully: it's short, and half-open would hide the last row on a small screen.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    AppModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .testTag(DashboardScreenTestTags.SYNC_SHEET)
                .padding(start = 24.dp, end = 16.dp, bottom = 24.dp)
        ) {
            Text(
                "Sync status",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            WaniKaniRow(uiState, onRefresh)
            AnswersRow(uiState)
            if (uiState.deviceSync.isAvailable) {
                DevicesRow(uiState.deviceSync, onRetryDeviceSync, onOpenSettings = { onDismiss(); onOpenSettings() })
            }
        }
    }
}

@Composable
private fun WaniKaniRow(uiState: DashboardUiState, onRefresh: () -> Unit) {
    val refreshing = uiState.fetchState is DashboardFetch.InFlight
    val updatedAgo = rememberRelativeTime(uiState.lastSyncedAtMillis)
    val (tone, detail) = when {
        refreshing -> SyncTone.Busy to "Updating…"
        uiState.syncBlockedOnAuth -> SyncTone.Problem to "Paused: WaniKani rejected your API token"
        uiState.isShowingCachedData -> SyncTone.Problem to "Couldn't reach WaniKani. Showing data from $updatedAgo"
        uiState.lastSyncedAtMillis != null -> SyncTone.Ok to "Updated $updatedAgo"
        else -> SyncTone.Neutral to "Not updated yet"
    }
    SyncSheetRow(
        title = "WaniKani",
        detail = detail,
        tone = tone,
        testTag = DashboardScreenTestTags.SYNC_SHEET_WANIKANI_ROW
    ) {
        TextButton(
            onClick = onRefresh,
            enabled = !refreshing,
            modifier = Modifier.testTag(DashboardScreenTestTags.SYNC_SHEET_REFRESH_BUTTON)
        ) { Text("Refresh") }
    }
}

@Composable
private fun AnswersRow(uiState: DashboardUiState) {
    val pending = uiState.pendingSyncCount
    val (tone, detail) = when {
        uiState.syncBlockedOnAuth -> SyncTone.Problem to "Can't send: WaniKani rejected your API token"
        pending > 0 -> SyncTone.Neutral to "$pending waiting to send"
        else -> SyncTone.Ok to "All sent to WaniKani"
    }
    SyncSheetRow(
        title = "Reviews & lessons",
        detail = detail,
        tone = tone,
        testTag = DashboardScreenTestTags.SYNC_SHEET_ANSWERS_ROW
    )
}

@Composable
private fun DevicesRow(deviceSync: DeviceSyncState, onRetry: () -> Unit, onOpenSettings: () -> Unit) {
    val status = deviceSync.status
    val syncedAgo = rememberRelativeTime(status.lastSyncedAtMs)
    val (tone, detail) = when {
        !deviceSync.enabled -> SyncTone.Neutral to "Off"
        status.isSyncing -> SyncTone.Busy to "Syncing…"
        status.lastPassFailed -> SyncTone.Problem to "Couldn't sync. Will try again"
        status.lastSyncedAtMs != null -> SyncTone.Ok to "Synced $syncedAgo"
        else -> SyncTone.Neutral to "Waiting for the first sync"
    }
    SyncSheetRow(
        title = "Study time & settings across devices",
        detail = detail,
        tone = tone,
        testTag = DashboardScreenTestTags.SYNC_SHEET_DEVICES_ROW
    ) {
        when {
            !deviceSync.enabled -> TextButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag(DashboardScreenTestTags.SYNC_SHEET_DEVICES_SETTINGS_BUTTON)
            ) { Text("Turn on") }
            status.lastPassFailed && !status.isSyncing -> TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag(DashboardScreenTestTags.SYNC_SHEET_DEVICES_RETRY_BUTTON)
            ) { Text("Retry") }
        }
    }
}

@Composable
private fun SyncSheetRow(
    title: String,
    detail: String,
    tone: SyncTone,
    testTag: String,
    action: @Composable () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().testTag(testTag).padding(vertical = 8.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(24.dp)) {
            when (tone) {
                SyncTone.Busy -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                SyncTone.Ok -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                SyncTone.Problem -> Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                SyncTone.Neutral -> Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (tone == SyncTone.Problem) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        action()
    }
}
