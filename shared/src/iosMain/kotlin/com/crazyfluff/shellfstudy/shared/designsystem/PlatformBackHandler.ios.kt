package com.crazyfluff.shellfstudy.shared.designsystem

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS back navigation is handled natively by UINavigationController / SwiftUI NavigationStack.
}
