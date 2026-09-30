package com.crazyfluff.shellfstudy.shared.designsystem.window

import androidx.compose.runtime.Composable

// The keyboard handover this avoids is Android's; on iOS dialogs and sheets draw in the app's own
// layer, so there's no separate window to keep out of keyboard focus.
@Composable
actual fun KeepWindowOutOfKeyboardFocus() = Unit
