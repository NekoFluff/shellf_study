package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop

/**
 * A [TextFieldState] seeded from [initialValue] that pushes every edit — but not its own initial
 * seed value — up to [onValueChange] via [snapshotFlow]. The common shape for a field that's owned
 * locally rather than driven by `value`/`onValueChange` directly, so it goes through Compose's
 * modern text-input pipeline (a state-based `OutlinedTextField`/`OutlinedSecureTextField`/
 * `BasicTextField`) instead of the legacy CoreTextField value/onValueChange path, whose IME
 * cursor-anchor bookkeeping has a framework crash (see `LegacyCursorAnchorInfoBuilder`).
 *
 * Only fits a field whose value is never reset from outside after creation (a one-time initial
 * value is enough, so there's no need for continuous two-way sync) — a field that must also accept
 * external resets (e.g. a persistent search box with a clear button) still needs its own additional
 * reverse-sync effect on top of this.
 */
@Composable
fun rememberPushUpTextFieldState(initialValue: String, onValueChange: (String) -> Unit): TextFieldState {
    val fieldState = rememberTextFieldState(initialValue)
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.drop(1).collect(onValueChange)
    }
    return fieldState
}
