package com.crazyfluff.shellfstudy.shared.designsystem.window

import androidx.compose.runtime.Composable

/**
 * Keeps the dialog or sheet window this is composed in from becoming the keyboard's target.
 *
 * Call it from inside the window's own content. The design-system wrappers (`AppAlertDialog`,
 * `AppModalBottomSheet`) already do; nothing else should need to.
 *
 * Why: on Android, a focusable dialog or sheet window takes keyboard focus while it's open. When it
 * closes, the system hands keyboard control to a display-level fallback that can still hold an
 * earlier "keyboard visible" request, and re-shows the keyboard until the app window takes focus
 * back and hides it. Confirmed on a Galaxy S22 (Android 16): the keyboard stayed up for about 0.6 s
 * after a sheet was dismissed. Android's own `AlertDialog` avoids this by setting
 * `FLAG_ALT_FOCUSABLE_IM` whenever it has no text editor; Compose's `Dialog` and Material's
 * `ModalBottomSheet` don't, so this sets it. The window stays focusable, so Back and tapping
 * outside still dismiss it. See also `DropdownMenu`, which fixes the same handover for menus.
 */
@Composable
expect fun KeepWindowOutOfKeyboardFocus()
