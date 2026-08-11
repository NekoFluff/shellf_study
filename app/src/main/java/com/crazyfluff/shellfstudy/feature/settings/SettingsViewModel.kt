package com.crazyfluff.shellfstudy.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.data.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val dailyLessonGoal: Int = 15,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showPitchAccent: Boolean = true,
    val autoplayPronunciationAudio: Boolean = true
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = settingsRepository.settings
        .map {
            SettingsUiState(
                dailyLessonGoal = it.dailyLessonGoal,
                themeMode = it.themeMode,
                showPitchAccent = it.showPitchAccent,
                autoplayPronunciationAudio = it.autoplayPronunciationAudio
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun onDailyLessonGoalChange(goal: Int) {
        viewModelScope.launch { settingsRepository.setDailyLessonGoal(goal) }
    }

    fun onThemeModeChange(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun onShowPitchAccentChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowPitchAccent(enabled) }
    }

    fun onAutoplayPronunciationAudioChange(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoplayPronunciationAudio(enabled) }
    }
}
