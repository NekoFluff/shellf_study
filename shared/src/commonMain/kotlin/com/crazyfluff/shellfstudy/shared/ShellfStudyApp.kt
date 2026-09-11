package com.crazyfluff.shellfstudy.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
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
    ShellfStudyTheme(themeMode = themeMode) {
        CompositionLocalProvider(LocalPronunciationAudioPlayer provides audioPlayer) {
            ShellfStudyNavHost()
        }
    }
}
