package com.crazyfluff.shellfstudy.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.crazyfluff.shellfstudy.core.data.ThemeMode
import com.crazyfluff.shellfstudy.core.designsystem.theme.ShellfStudyTheme

object SettingsScreenTestTags {
    const val BACK_BUTTON = "settings_back_button"
    const val LESSON_GOAL_DECREASE = "settings_lesson_goal_decrease"
    const val LESSON_GOAL_INCREASE = "settings_lesson_goal_increase"
    const val LESSON_GOAL_VALUE = "settings_lesson_goal_value"
    const val THEME_SYSTEM_OPTION = "settings_theme_system_option"
    const val THEME_LIGHT_OPTION = "settings_theme_light_option"
    const val THEME_DARK_OPTION = "settings_theme_dark_option"
    const val PITCH_ACCENT_TOGGLE = "settings_pitch_accent_toggle"
    const val AUTOPLAY_AUDIO_TOGGLE = "settings_autoplay_audio_toggle"
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    SettingsScreen(
        uiState = uiState,
        onDailyLessonGoalChange = viewModel::onDailyLessonGoalChange,
        onThemeModeChange = viewModel::onThemeModeChange,
        onShowPitchAccentChange = viewModel::onShowPitchAccentChange,
        onAutoplayPronunciationAudioChange = viewModel::onAutoplayPronunciationAudioChange,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onDailyLessonGoalChange: (Int) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onShowPitchAccentChange: (Boolean) -> Unit,
    onAutoplayPronunciationAudioChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(SettingsScreenTestTags.BACK_BUTTON)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp)
        ) {
            Text("Daily lesson goal", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = { onDailyLessonGoalChange(uiState.dailyLessonGoal - 1) },
                    enabled = uiState.dailyLessonGoal > 1,
                    modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_GOAL_DECREASE)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease daily lesson goal")
                }
                Text(
                    text = uiState.dailyLessonGoal.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_GOAL_VALUE)
                )
                IconButton(
                    onClick = { onDailyLessonGoalChange(uiState.dailyLessonGoal + 1) },
                    enabled = uiState.dailyLessonGoal < 99,
                    modifier = Modifier.testTag(SettingsScreenTestTags.LESSON_GOAL_INCREASE)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase daily lesson goal")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            ThemeOptionRow(
                label = "System default",
                mode = ThemeMode.SYSTEM,
                selected = uiState.themeMode,
                onSelect = onThemeModeChange,
                testTag = SettingsScreenTestTags.THEME_SYSTEM_OPTION
            )
            ThemeOptionRow(
                label = "Light",
                mode = ThemeMode.LIGHT,
                selected = uiState.themeMode,
                onSelect = onThemeModeChange,
                testTag = SettingsScreenTestTags.THEME_LIGHT_OPTION
            )
            ThemeOptionRow(
                label = "Dark",
                mode = ThemeMode.DARK,
                selected = uiState.themeMode,
                onSelect = onThemeModeChange,
                testTag = SettingsScreenTestTags.THEME_DARK_OPTION
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text("Vocabulary", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Show pitch accent")
                Switch(
                    checked = uiState.showPitchAccent,
                    onCheckedChange = onShowPitchAccentChange,
                    modifier = Modifier.testTag(SettingsScreenTestTags.PITCH_ACCENT_TOGGLE)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Auto-play pronunciation audio")
                Switch(
                    checked = uiState.autoplayPronunciationAudio,
                    onCheckedChange = onAutoplayPronunciationAudioChange,
                    modifier = Modifier.testTag(SettingsScreenTestTags.AUTOPLAY_AUDIO_TOGGLE)
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    mode: ThemeMode,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(mode) }
            .testTag(testTag)
            .padding(vertical = 8.dp)
    ) {
        RadioButton(selected = selected == mode, onClick = { onSelect(mode) })
        Spacer(modifier = Modifier.width(8.dp))
        Text(label)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ShellfStudyTheme {
        SettingsScreen(
            uiState = SettingsUiState(dailyLessonGoal = 15, themeMode = ThemeMode.SYSTEM),
            onDailyLessonGoalChange = {},
            onThemeModeChange = {},
            onShowPitchAccentChange = {},
            onAutoplayPronunciationAudioChange = {},
            onBack = {}
        )
    }
}
