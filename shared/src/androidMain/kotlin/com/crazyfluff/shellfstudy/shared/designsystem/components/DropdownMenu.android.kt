package com.crazyfluff.shellfstudy.shared.designsystem.components

import android.view.WindowManager
import androidx.compose.ui.window.PopupProperties

// Compose's own flags for a focusable popup (FLAG_WATCH_OUTSIDE_TOUCH), plus FLAG_ALT_FOCUSABLE_IM,
// which keeps the window from being the keyboard's target while still letting it take focus.
internal actual fun menuPopupProperties(): PopupProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
)
