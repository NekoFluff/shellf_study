package com.crazyfluff.shellfstudy.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
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
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.LocalPronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.designsystem.text.LocalShareText
import com.crazyfluff.shellfstudy.shared.designsystem.text.rememberShareText
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.LocalOpenSubjectDetail
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailSheetHost
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.rememberSubjectDetailSheetState
import com.crazyfluff.shellfstudy.shared.navigation.ShellfStudyNavHost
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The app's single Compose root, used by both platforms — Android's `MainActivity.setContent` and
 * iOS's `MainViewController`.
 *
 * It is one composable on purpose, and that is load-bearing rather than tidiness. This used to be
 * duplicated: `MainActivity` hand-mirrored this file, and the two drifted the moment an ambient value
 * was added to only one of them. Four of them were provided here and nowhere else, so on Android they
 * silently fell back to their defaults — the reading pitch-accent hint stopped appearing (its gate
 * defaulted to "off" while the stored setting said otherwise), and tapping a subject opened nothing
 * (the opener defaulted to a no-op). Nothing failed to compile, and every existing test passed,
 * because each test that cared provided the local itself.
 *
 * Anything app-wide goes here, once. `ArchitectureConventionsTest` fails if one of these five `Local`s
 * is provided anywhere else, or if a second `ShellfStudyNavHost` call site appears.
 */
@Composable
fun ShellfStudyApp(
    pendingDestination: String? = null,
    onPendingDestinationConsumed: () -> Unit = {}
) {
    val themeViewModel: ThemeViewModel = koinViewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    // Provided here, at the root, so any reading's play button can dispatch without a callback
    // threaded down through every screen in between — see LocalPronunciationAudioPlayer.
    val audioPlayer: PronunciationAudioPlayer = koinInject()
    // AppSettings' defaults match SettingsRepository's DataStore defaults, so the first frame before
    // the flow's initial emission renders production defaults rather than everything switched off.
    val settingsRepository: SettingsRepository = koinInject()
    val appSettings by settingsRepository.settings.collectAsState(initial = AppSettings())
    val displaySettings = remember(appSettings) { appSettings.toDisplaySettings() }
    val shareText = rememberShareText()
    // The app's one browse-detail sheet. Mounted here rather than per screen because the host has no
    // per-screen state: see LocalOpenSubjectDetail.
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
            Surface(modifier = Modifier.fillMaxSize()) {
                // The Box is what lets the detail sheet overlay the active screen's own Scaffold,
                // including the system navigation-bar inset its handle pads for.
                Box(modifier = Modifier.fillMaxSize()) {
                    ShellfStudyNavHost(
                        pendingDestination = pendingDestination,
                        onPendingDestinationConsumed = onPendingDestinationConsumed
                    )
                    SubjectDetailSheetHost(subjectDetailSheetState)
                }
            }
        }
    }
}
