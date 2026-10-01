package com.crazyfluff.shellfstudy.shared.designsystem.window

import androidx.compose.runtime.Composable

/**
 * Keeps the dialog or sheet window this is composed in from becoming the keyboard's target.
 *
 * Call it from inside the window's own content. The design-system wrappers (`AppAlertDialog`,
 * `AppModalBottomSheet`) already do; nothing else should need to.
 *
 * Why: a window without a text field shouldn't be a keyboard target. Android's own `AlertDialog`
 * sets `FLAG_ALT_FOCUSABLE_IM` whenever it has no text editor; Compose's `Dialog` and Material's
 * `ModalBottomSheet` don't, so this sets it. The window stays focusable, so Back and tapping outside
 * still dismiss it.
 *
 * On a healthy device this has no visible effect. It matters when the device's keyboard state gets
 * stuck: the display-level keyboard target keeps a stale "visible" request, and whenever a focusable
 * window closes and hands keyboard control back to it, the keyboard flashes up until the app window
 * takes focus and hides it. Seen on a Galaxy S22 (Android 16, Sep 2026). Other apps flashed the same
 * way, and a reboot cleared it, so it was the device, not the app. If a flash is reported, reboot
 * first and check `adb shell dumpsys input_method` before changing code. See also `DropdownMenu`,
 * which does the same for menus.
 */
@Composable
expect fun KeepWindowOutOfKeyboardFocus()
