package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.ui.window.PopupProperties

// The keyboard handover this avoids is Android's; iOS keeps Material's default menu popup.
internal actual fun menuPopupProperties(): PopupProperties = PopupProperties(focusable = true)
