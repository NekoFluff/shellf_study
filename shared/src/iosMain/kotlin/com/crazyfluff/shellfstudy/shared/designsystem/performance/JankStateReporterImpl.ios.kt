package com.crazyfluff.shellfstudy.shared.designsystem.performance

import androidx.compose.runtime.Composable

@Composable
actual fun rememberJankStateReporter(): (Map<String, String>) -> Unit = {
    // No jank harness on iOS yet — JankStats is backed by the Android platform FrameMetrics API.
    // Reporting is a no-op rather than absent so shared screens carry no platform branching; see the
    // expect declaration.
}

@Composable
actual fun rememberCompositionRecorder(): (String, Long) -> Unit = { _, _ ->
    // No harness on iOS; see the expect declaration.
}

@Composable
actual fun rememberCompositionTimingEnabled(): Boolean = false
