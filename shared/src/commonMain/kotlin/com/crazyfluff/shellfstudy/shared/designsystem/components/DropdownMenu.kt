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
 * A plain focusable menu window takes keyboard focus while it's open. When it closes, Android hands
 * keyboard control to a display-level fallback that can still hold a stale "keyboard visible" request
 * from whichever app last showed the keyboard, so the keyboard flashed up over the screen until the
 * app window took focus back and hid it. Keeping the menu out of keyboard focus (see
 * [menuPopupProperties]) leaves the app window as the target throughout, with nothing to hand over.
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
