package com.crazyfluff.shellfstudy.shared.designsystem.dialog

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.crazyfluff.shellfstudy.shared.designsystem.components.AppAlertDialog

/** Generic destructive-action confirmation — shared by every screen that needs a "are you sure"
 *  prompt before an irreversible action, rather than each rolling its own dialog. */
@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonTestTag: String? = null,
    dismissLabel: String = "Cancel"
) {
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = confirmButtonTestTag?.let { Modifier.testTag(it) } ?: Modifier
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}
