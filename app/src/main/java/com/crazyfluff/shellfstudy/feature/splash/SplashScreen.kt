package com.crazyfluff.shellfstudy.feature.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.koin.androidx.compose.koinViewModel
import com.crazyfluff.shellfstudy.shared.designsystem.theme.ShellfStudyTheme
import com.crazyfluff.shellfstudy.shared.feature.splash.SplashDestination
import com.crazyfluff.shellfstudy.shared.feature.splash.SplashViewModel

object SplashScreenTestTags {
    const val LOADING_INDICATOR = "splash_loading_indicator"
}

@Composable
fun SplashRoute(
    onNavigateToAuth: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    viewModel: SplashViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.destination) {
        when (uiState.destination) {
            SplashDestination.AUTH -> onNavigateToAuth()
            SplashDestination.DASHBOARD -> onNavigateToDashboard()
            null -> Unit
        }
    }

    SplashScreen()
}

/** Deliberately minimal — no title/copy/link — so a returning user never sees a flash of login UI. */
@Composable
fun SplashScreen() {
    Box(modifier = Modifier.fillMaxSize()) {
        CircularProgressIndicator(
            modifier = Modifier
                .align(Alignment.Center)
                .testTag(SplashScreenTestTags.LOADING_INDICATOR)
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun SplashScreenPreview() {
    ShellfStudyTheme {
        SplashScreen()
    }
}
