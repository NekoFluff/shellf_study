package com.crazyfluff.shellfstudy.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.crazyfluff.shellfstudy.shared.data.AppSettings
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.designsystem.LocalNotificationPermissionRequest
import com.crazyfluff.shellfstudy.shared.designsystem.rememberNotificationPermissionRequest
import com.crazyfluff.shellfstudy.shared.designsystem.settings.LocalDisplaySettings
import com.crazyfluff.shellfstudy.shared.designsystem.settings.toDisplaySettings
import com.crazyfluff.shellfstudy.shared.designsystem.text.LocalShareText
import com.crazyfluff.shellfstudy.shared.designsystem.text.rememberShareText
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.LocalOpenSubjectDetail
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheetHost
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.rememberSubjectDetailSheetState
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.LocalPronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.navigation.ShellfStudyNavHost
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ShellfStudyApp() {
    val themeViewModel: ThemeViewModel = koinViewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    // Provided here, at the root, so any reading's play button can dispatch without a callback
    // threaded down through every screen in between — see LocalPronunciationAudioPlayer.
    val audioPlayer: PronunciationAudioPlayer = koinInject()
    // Provided here for the same reason as audioPlayer below: these are read by leaves deep inside
    // every screen, and threading nine flags through each of them is what this replaces. AppSettings'
    // defaults match SettingsRepository's DataStore defaults, so the first frame before the flow's
    // initial emission renders production defaults rather than everything switched off.
    val settingsRepository: SettingsRepository = koinInject()
    val appSettings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val displaySettings = remember(appSettings) { appSettings.toDisplaySettings() }
    val shareText = rememberShareText()
    // The app's one browse-detail sheet. Mounted here rather than per screen because the host has no
    // per-screen state: see LocalOpenSubjectDetail. The Box is what lets the sheet overlay the active
    // screen's own Scaffold, including the system navigation-bar inset its handle pads for.
    val subjectDetailSheetState = rememberSubjectDetailSheetState()
    val openSubjectDetail: (Long) -> Unit =
        remember(subjectDetailSheetState) { { id: Long -> subjectDetailSheetState.show(id) } }
    ShellfStudyTheme(themeMode = themeMode) {
        CompositionLocalProvider(
            LocalPronunciationAudioPlayer provides audioPlayer,
            LocalDisplaySettings provides displaySettings,
            LocalShareText provides shareText,
            LocalOpenSubjectDetail provides openSubjectDetail,
            // A factory, not a bound trigger: the trigger a caller needs depends on its own result
            // callback. This one just defers to the platform implementation.
            LocalNotificationPermissionRequest provides { onResult ->
                rememberNotificationPermissionRequest(onResult)
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ShellfStudyNavHost()
                SubjectDetailSheetHost(subjectDetailSheetState)
            }
        }
    }
}
