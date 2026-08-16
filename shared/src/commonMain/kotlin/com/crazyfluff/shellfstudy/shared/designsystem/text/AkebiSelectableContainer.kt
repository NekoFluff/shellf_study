package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Wraps [content] in a text-selection container. On Android, also adds a "Look up in Akebi"
 *  entry to the selection action menu via ACTION_PROCESS_TEXT. iOS no-op for the Akebi part. */
@Composable
expect fun AkebiSelectableContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit)
