@file:Suppress("ForbiddenImport") // The wrappers every other file is pointed to instead.

package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import com.crazyfluff.shellfstudy.shared.designsystem.window.KeepWindowOutOfKeyboardFocus

/*
 * Material's dialog and sheet, for every screen to use instead of Material's own. Each one draws in
 * a window of its own on Android. A window without a text field stays out of keyboard focus, so
 * closing it can't flash the keyboard when the device's keyboard state is stuck (see
 * KeepWindowOutOfKeyboardFocus). detekt's ForbiddenImport
 * points everything else here, so a new dialog can't skip it by accident.
 *
 * There are two dialogs rather than one with a flag. Getting the choice wrong fails silently either
 * way (a flashing keyboard, or a text field whose keyboard never opens), so the call site should
 * name which kind of dialog it is.
 */

/** A dialog with no text field: confirmations, pickers. Its window never becomes the keyboard's target. */
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        // confirmButton is the one slot every AlertDialog composes, so the flag goes on from there.
        confirmButton = {
            KeepWindowOutOfKeyboardFocus()
            confirmButton()
        },
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        properties = properties
    )
}

/** A dialog with a text field. It stays a keyboard target, so typing in it brings up the keyboard. */
@Composable
fun AppTextInputDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        title = title,
        text = text,
        properties = properties
    )
}

/** Material's modal bottom sheet, kept out of keyboard focus. None of the app's sheets take text. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier, sheetState = sheetState) {
        KeepWindowOutOfKeyboardFocus()
        content()
    }
}
