package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.crazyfluff.shellfstudy.shared.data.audio.LevelAudioRow
import com.crazyfluff.shellfstudy.shared.data.audio.LevelAudioStatus
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioState
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioVoices
import com.crazyfluff.shellfstudy.shared.designsystem.components.ListGroup
import com.crazyfluff.shellfstudy.shared.designsystem.dialog.ConfirmationDialog
import com.crazyfluff.shellfstudy.shared.designsystem.theme.einkBorder
import com.crazyfluff.shellfstudy.shared.designsystem.theme.emphasisContainerColor
import kotlin.math.roundToLong
import org.koin.compose.viewmodel.koinViewModel

object OfflineAudioTestTags {
    const val BACK_BUTTON = "offline_audio_back_button"
    const val SUMMARY = "offline_audio_summary"
    const val DOWNLOAD_CURRENT_LEVEL_ROW = "offline_audio_download_current_level_row"
    const val DOWNLOAD_UNLOCKED_ROW = "offline_audio_download_unlocked_row"
    const val DOWNLOAD_NOW_ROW = "offline_audio_download_now_row"
    const val AUTO_CURRENT_LEVEL_TOGGLE = "offline_audio_auto_current_level_toggle"
    const val WIFI_ONLY_TOGGLE = "offline_audio_wifi_only_toggle"
    const val DELETE_ALL_ROW = "offline_audio_delete_all_row"
    const val DELETE_ALL_CONFIRM_BUTTON = "offline_audio_delete_all_confirm_button"
    fun voicesOptionTag(voices: OfflineAudioVoices) = "offline_audio_voices_${voices.name.lowercase()}_option"
    fun levelRowTag(level: Int) = "offline_audio_level_${level}_row"
    fun levelDownloadTag(level: Int) = "offline_audio_level_${level}_download"
    fun levelDeleteTag(level: Int) = "offline_audio_level_${level}_delete"
}

/** What [OfflineAudioScreen] can ask for — implemented by [OfflineAudioViewModel]. */
interface OfflineAudioActions {
    fun onAutoDownloadCurrentLevelChange(enabled: Boolean)
    fun onWifiOnlyChange(enabled: Boolean)
    fun onVoicesChange(voices: OfflineAudioVoices)
    fun onDownloadLevel(level: Int)
    fun onDownloadCurrentLevel()
    fun onDownloadUnlockedLevels()
    fun onDownloadNow()
    fun onDeleteLevel(level: Int)
    fun onDeleteAll()
}

@Composable
fun OfflineAudioRoute(onBack: () -> Unit, viewModel: OfflineAudioViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    OfflineAudioScreen(state = state, actions = viewModel, onBack = onBack)
}

/**
 * Settings → Offline audio. Pronunciation clips are kept on the device once downloaded (or once
 * played), so reviews and lessons have sound with no connection. Downloads are chosen by level: the
 * current one automatically, any others by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineAudioScreen(state: OfflineAudioState, actions: OfflineAudioActions, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Offline audio") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(OfflineAudioTestTags.BACK_BUTTON)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (state.levels.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 16.dp)
        ) {
            SummaryCard(state)
            DownloadGroup(state, actions)
            LevelsGroup(state.levels, actions)
            StorageGroup(state, actions)
        }
    }
}

@Composable
private fun SummaryCard(state: OfflineAudioState) {
    val downloading = state.levels.firstNotNullOfOrNull { row ->
        (row.status as? LevelAudioStatus.Downloading)?.let { row.level to it }
    }
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = emphasisContainerColor(),
        border = einkBorder(),
        modifier = Modifier.fillMaxWidth().testTag(OfflineAudioTestTags.SUMMARY)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Text(
                text = "${levelsLabel(state.downloadedLevels)} downloaded",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "${formatBytes(state.totalBytes)} · ${clipsLabel(state.totalClips)} on this device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (downloading != null) {
                val (level, progress) = downloading
                Text(
                    text = "Downloading level $level — ${progress.done} of ${progress.total}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                LinearProgressIndicator(
                    progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DownloadGroup(state: OfflineAudioState, actions: OfflineAudioActions) {
    val prefs = state.preferences
    val currentLevel = prefs.currentLevel
    val isWaiting = state.levels.none { it.status is LevelAudioStatus.Downloading } &&
        state.levels.any { it.status is LevelAudioStatus.Queued }
    ListGroup(title = "Download") {
        if (isWaiting) {
            NavRow(
                title = "Download now",
                subtitle = "Some levels are waiting for a connection" + if (prefs.wifiOnly) " (Wi-Fi only)" else "",
                onClick = actions::onDownloadNow,
                testTag = OfflineAudioTestTags.DOWNLOAD_NOW_ROW
            )
        }
        NavRow(
            title = "Download your current level",
            subtitle = currentLevel?.let { "Level $it" } ?: "Available once your level has synced",
            onClick = actions::onDownloadCurrentLevel,
            enabled = currentLevel != null,
            testTag = OfflineAudioTestTags.DOWNLOAD_CURRENT_LEVEL_ROW
        )
        NavRow(
            title = "Download all unlocked levels",
            subtitle = currentLevel?.let { if (it == 1) "Level 1" else "Levels 1–$it" }
                ?: "Available once your level has synced",
            onClick = actions::onDownloadUnlockedLevels,
            enabled = currentLevel != null,
            testTag = OfflineAudioTestTags.DOWNLOAD_UNLOCKED_ROW
        )
        SwitchRow(
            title = "Keep your current level downloaded",
            subtitle = "Downloads each new level as you reach it",
            checked = prefs.autoDownloadCurrentLevel,
            onCheckedChange = actions::onAutoDownloadCurrentLevelChange,
            testTag = OfflineAudioTestTags.AUTO_CURRENT_LEVEL_TOGGLE
        )
        SwitchRow(
            title = "Download on Wi-Fi only",
            checked = prefs.wifiOnly,
            onCheckedChange = actions::onWifiOnlyChange,
            testTag = OfflineAudioTestTags.WIFI_ONLY_TOGGLE
        )
        SingleChoiceRow(
            title = "Voices to download",
            subtitle = when (prefs.voices) {
                OfflineAudioVoices.ALL -> "Every voice, so each tap can play a different one"
                OfflineAudioVoices.FEMALE -> "Female voices only — about half the space"
                OfflineAudioVoices.MALE -> "Male voices only — about half the space"
            },
            options = listOf(
                OfflineAudioVoices.ALL to "All",
                OfflineAudioVoices.FEMALE to "Female",
                OfflineAudioVoices.MALE to "Male"
            ).map { (voices, label) -> ChoiceOption(voices, label, OfflineAudioTestTags.voicesOptionTag(voices)) },
            selected = prefs.voices,
            onSelect = actions::onVoicesChange
        )
    }
}

@Composable
private fun LevelsGroup(levels: List<LevelAudioRow>, actions: OfflineAudioActions) {
    ListGroup(title = "Levels") {
        levels.forEach { row -> LevelRow(row, actions) }
    }
}

@Composable
private fun LevelRow(row: LevelAudioRow, actions: OfflineAudioActions) {
    val status = row.status
    val enabled = status !is LevelAudioStatus.Locked
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(OfflineAudioTestTags.levelRowTag(row.level))
            .padding(start = RowHorizontalPadding, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        RowText(
            title = "Level ${row.level}",
            subtitle = statusLabel(status),
            enabled = enabled,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )
        when (status) {
            LevelAudioStatus.Locked -> Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
                modifier = Modifier.padding(12.dp)
            )
            is LevelAudioStatus.Downloading -> CircularProgressIndicator(
                progress = { if (status.total == 0) 0f else status.done.toFloat() / status.total },
                strokeWidth = 2.dp,
                modifier = Modifier.padding(14.dp).size(20.dp)
            )
            is LevelAudioStatus.NotDownloaded, is LevelAudioStatus.Partial -> IconButton(
                onClick = { actions.onDownloadLevel(row.level) },
                modifier = Modifier.testTag(OfflineAudioTestTags.levelDownloadTag(row.level))
            ) {
                Icon(Icons.Filled.Download, contentDescription = null)
            }
            is LevelAudioStatus.Queued, is LevelAudioStatus.Downloaded -> IconButton(
                onClick = { actions.onDeleteLevel(row.level) },
                modifier = Modifier.testTag(OfflineAudioTestTags.levelDeleteTag(row.level))
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
            }
        }
    }
}

@Composable
private fun StorageGroup(state: OfflineAudioState, actions: OfflineAudioActions) {
    var showConfirm by remember { mutableStateOf(false) }
    ListGroup(title = "Storage") {
        DestructiveRow(
            title = "Delete all downloaded audio",
            icon = Icons.Filled.DeleteForever,
            onClick = { showConfirm = true },
            testTag = OfflineAudioTestTags.DELETE_ALL_ROW
        )
    }
    if (showConfirm) {
        ConfirmationDialog(
            title = "Delete all downloaded audio?",
            text = "This frees ${formatBytes(state.totalBytes)} and stops every level downloading, including your " +
                "current one. Audio will download again as you play it while online.",
            confirmLabel = "Delete",
            onConfirm = {
                showConfirm = false
                actions.onDeleteAll()
            },
            onDismiss = { showConfirm = false },
            confirmButtonTestTag = OfflineAudioTestTags.DELETE_ALL_CONFIRM_BUTTON
        )
    }
}

internal fun statusLabel(status: LevelAudioStatus): String = when (status) {
    LevelAudioStatus.Locked -> "Not included in your subscription"
    is LevelAudioStatus.NotDownloaded -> "${clipsLabel(status.clips)} to download"
    is LevelAudioStatus.Queued ->
        if (status.stored > 0) "Waiting to download · ${status.stored} of ${status.clips}" else "Waiting to download"
    is LevelAudioStatus.Downloading -> "Downloading · ${status.done} of ${status.total}"
    is LevelAudioStatus.Partial -> "${status.stored} of ${status.clips} clips · ${formatBytes(status.bytes)}"
    is LevelAudioStatus.Downloaded -> "Downloaded · ${formatBytes(status.bytes)}"
}

private fun levelsLabel(count: Int) = if (count == 1) "1 level" else "$count levels"

private fun clipsLabel(count: Int) = if (count == 1) "1 clip" else "$count clips"

private const val BYTES_PER_UNIT = 1024.0
private const val TENTHS = 10

/** "0 KB", "840 KB", "3.4 MB" — one decimal for megabytes, whole kilobytes below that. */
internal fun formatBytes(bytes: Long): String {
    val kilobytes = bytes / BYTES_PER_UNIT
    if (kilobytes < BYTES_PER_UNIT) return "${kilobytes.roundToLong()} KB"
    val tenthsOfMegabytes = (kilobytes / BYTES_PER_UNIT * TENTHS).roundToLong()
    return "${tenthsOfMegabytes / TENTHS}.${tenthsOfMegabytes % TENTHS} MB"
}
