package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioManager
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioState
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioVoices
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Settings → Offline audio: which levels' pronunciation clips are kept on the device. */
class OfflineAudioViewModel(
    private val offlineAudioManager: OfflineAudioManager
) : ViewModel(), OfflineAudioActions {

    /** Empty [OfflineAudioState.levels] until the catalog of clips has loaded. */
    val uiState: StateFlow<OfflineAudioState> = offlineAudioManager.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OfflineAudioState())

    init {
        viewModelScope.launch { offlineAudioManager.prepare() }
    }

    override fun onAutoDownloadCurrentLevelChange(enabled: Boolean) =
        launch { updateSettings(autoDownloadCurrentLevel = enabled) }

    override fun onWifiOnlyChange(enabled: Boolean) = launch { updateSettings(wifiOnly = enabled) }

    override fun onVoicesChange(voices: OfflineAudioVoices) = launch { updateSettings(voices = voices) }

    override fun onDownloadLevel(level: Int) = launch { download(listOf(level)) }

    override fun onDownloadCurrentLevel() {
        val current = uiState.value.preferences.currentLevel ?: return
        launch { download(listOf(current)) }
    }

    /** Every level up to the user's own. */
    override fun onDownloadUnlockedLevels() {
        val current = uiState.value.preferences.currentLevel ?: return
        launch { download((1..current).toList()) }
    }

    /** Starts any download that is waiting — after a lost connection, typically. */
    override fun onDownloadNow() = launch { resume() }

    override fun onDeleteLevel(level: Int) = launch { deleteLevel(level) }

    override fun onDeleteAll() = launch { deleteAll() }

    private fun launch(block: suspend OfflineAudioManager.() -> Unit) {
        viewModelScope.launch { offlineAudioManager.block() }
    }
}
