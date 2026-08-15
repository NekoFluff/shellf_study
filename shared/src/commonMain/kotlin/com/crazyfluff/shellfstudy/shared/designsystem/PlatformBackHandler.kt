package com.crazyfluff.shellfstudy.shared.designsystem

import androidx.compose.runtime.Composable

/** Intercepts the system/gesture back action when [enabled]. iOS no-op: the back gesture is
 *  handled natively by SwiftUI/UIKit navigation. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
