package com.crazyfluff.shellfstudy.shared.designsystem.performance

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberJankStateReporter(): (Map<String, String>) -> Unit =
    // Stable across recompositions, so ReportJankState's DisposableEffect does not re-fire and
    // re-enter the tracker on every frame.
    remember { { tags: Map<String, String> -> JankStatsTracker.setState(tags) } }
