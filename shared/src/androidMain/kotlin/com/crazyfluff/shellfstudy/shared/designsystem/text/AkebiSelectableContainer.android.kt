package com.crazyfluff.shellfstudy.shared.designsystem.text

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.foundation.text.selection.rememberSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private data object LookUpInAkebiMenuKey

@Composable
actual fun AkebiSelectableContainer(modifier: Modifier, content: @Composable () -> Unit) {
    val selectionState = rememberSelectionState()
    SelectionContainer(
        state = selectionState,
        modifier = modifier.lookUpInAkebiContextMenu(selectionState),
        content = content,
    )
}

@Composable
private fun Modifier.lookUpInAkebiContextMenu(selectionState: SelectionState): Modifier {
    val context = LocalContext.current
    return this.appendTextContextMenuComponents {
        val selectedText = selectionState.selectedTexts.joinToString(separator = "") { it.text }
        if (selectedText.isNotBlank()) {
            val intent = buildProcessTextIntent(selectedText)
            if (canHandleProcessText(context, intent)) {
                item(key = LookUpInAkebiMenuKey, label = "Look up in Akebi") {
                    context.startActivity(intent)
                    close()
                }
            }
        }
    }
}

private fun buildProcessTextIntent(text: String): Intent =
    Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

private fun canHandleProcessText(context: Context, intent: Intent): Boolean =
    intent.resolveActivity(context.packageManager) != null
