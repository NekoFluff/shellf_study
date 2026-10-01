@file:Suppress("ForbiddenImport") // The wrapper every other file is pointed to instead.

package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu as MaterialDropdownMenu
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.window.PopupProperties

/**
 * Material's dropdown menu, with a popup that never becomes the keyboard's target. Use it instead of
 * `androidx.compose.material3.DropdownMenu` for every menu.
 *
 * A plain focusable menu window takes keyboard focus while it's open. Keeping it out of keyboard
 * focus (see [menuPopupProperties]) leaves the app window as the target throughout, so closing the
 * menu never hands keyboard control anywhere. That matters when the device's keyboard state is
 * stuck, which otherwise flashes the keyboard as the menu closes (see `KeepWindowOutOfKeyboardFocus`).
 */
@Composable
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MenuDefaults.shape,
    content: @Composable ColumnScope.() -> Unit
) {
    MaterialDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = menuPopupProperties(),
        shape = shape,
        content = content
    )
}

/** Focusable, so Back and taps outside still dismiss the menu, but kept out of keyboard focus. */
internal expect fun menuPopupProperties(): PopupProperties
