package com.crazyfluff.shellfstudy.shared.designsystem.performance

import androidx.compose.runtime.Composable

/**
 * The platform's jank-state reporter, or a no-op on platforms with no jank harness.
 *
 * Android forwards to `JankStatsTracker`; iOS returns `{}` and reporting becomes free. Following
 * [com.crazyfluff.shellfstudy.shared.designsystem.PlatformBackHandler]'s convention: shared screens
 * report unconditionally and the platform decides whether anything is listening.
 */
@Composable
expect fun rememberJankStateReporter(): (Map<String, String>) -> Unit
