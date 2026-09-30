package com.crazyfluff.shellfstudy.shared.designsystem.window

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

@Composable
actual fun KeepWindowOutOfKeyboardFocus() {
    val view = LocalView.current
    // A SideEffect, so it runs when this first composition is applied. Dialog and sheet content
    // is composed before the window is shown, so the flag is in place before the window can
    // take keyboard focus.
    SideEffect {
        view.enclosingDialogWindow()?.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
    }
}

/** The window of the dialog this view sits in. Compose's `DialogLayout` and Material's
 *  `ModalBottomSheetDialogLayout` both implement [DialogWindowProvider] for exactly this. */
private fun View.enclosingDialogWindow(): Window? =
    generateSequence<Any>(this) { (it as? View)?.parent }
        .filterIsInstance<DialogWindowProvider>()
        .firstOrNull()
        ?.window
