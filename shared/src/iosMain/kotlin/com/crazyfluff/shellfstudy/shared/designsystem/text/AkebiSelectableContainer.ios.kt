package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun AkebiSelectableContainer(modifier: Modifier, content: @Composable () -> Unit) {
    SelectionContainer(modifier = modifier, content = content)
}
