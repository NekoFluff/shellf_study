package com.crazyfluff.shellfstudy.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.navigation.ShellfStudyNavHost
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ShellfStudyApp() {
    val themeViewModel: ThemeViewModel = koinViewModel()
    val themeMode by themeViewModel.themeMode.collectAsState()
    ShellfStudyTheme(themeMode = themeMode) {
        ShellfStudyNavHost()
    }
}
