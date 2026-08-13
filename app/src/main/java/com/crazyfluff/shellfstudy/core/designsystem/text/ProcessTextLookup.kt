package com.crazyfluff.shellfstudy.core.designsystem.text

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.modifier.appendTextContextMenuComponents
import androidx.compose.foundation.text.selection.SelectionState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

private data object LookUpInAkebiMenuKey

fun buildProcessTextIntent(text: String): Intent =
    Intent(Intent.ACTION_PROCESS_TEXT)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
        .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

fun canHandleProcessText(context: Context, intent: Intent): Boolean =
    intent.resolveActivity(context.packageManager) != null

/** Adds a "Look up in Akebi" entry to this hierarchy's text-selection menu, via ACTION_PROCESS_TEXT. */
@Composable
fun Modifier.lookUpInAkebiContextMenu(selectionState: SelectionState): Modifier {
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
