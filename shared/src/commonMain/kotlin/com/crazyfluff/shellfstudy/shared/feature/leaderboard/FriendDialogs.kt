package com.crazyfluff.shellfstudy.shared.feature.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.components.AppTextInputDialog
import com.crazyfluff.shellfstudy.shared.designsystem.text.rememberPushUpTextFieldState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions

/**
 * Adding a friend: a nickname and their token, nothing else. Where to get a read-only token is
 * explained under the list while it's empty; here the only text under a field is an error.
 *
 * The nickname field is focused on open, Next moves to the token, and Done on the token submits,
 * so a pasted token can be added without reaching for the button.
 */
@Composable
internal fun AddFriendDialog(form: AddFriendFormState, actions: LeaderboardActions, onDismiss: () -> Unit) {
    val nicknameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { nicknameFocus.requestFocus() }

    AppTextInputDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a friend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Neither value is ever reset from outside while this dialog is open — it's torn
                // down and rebuilt fresh on next open — so a one-time initial value is enough.
                OutlinedTextField(
                    state = rememberPushUpTextFieldState(form.nickname, actions::onAddFriendNicknameChange),
                    label = { Text("Nickname") },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nicknameFocus)
                        .testTag(LeaderboardScreenTestTags.NICKNAME_FIELD)
                )
                OutlinedTextField(
                    state = rememberPushUpTextFieldState(form.token, actions::onAddFriendTokenChange),
                    label = { Text("API token") },
                    supportingText = form.error?.let { error -> { Text(error) } },
                    isError = form.error != null,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    // Tokens are pasted, never typed as words: no autocorrect "fixing" them.
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Done
                    ),
                    onKeyboardAction = { actions.onAddFriendConfirm() },
                    modifier = Modifier.fillMaxWidth().testTag(LeaderboardScreenTestTags.TOKEN_FIELD)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions::onAddFriendConfirm,
                enabled = !form.isValidating,
                modifier = Modifier.testTag(LeaderboardScreenTestTags.ADD_FRIEND_CONFIRM)
            ) {
                if (form.isValidating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Checking…")
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun EditNicknameDialog(initialNickname: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // Only read at Save time (never pushed up on every keystroke), and TextFieldState.text is
    // itself Compose-observable, so the enabled check below can read it directly.
    val nicknameFieldState = rememberTextFieldState(initialNickname)
    AppTextInputDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                state = nicknameFieldState,
                label = { Text("Nickname") },
                lineLimits = TextFieldLineLimits.SingleLine,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nicknameFieldState.text.toString()) },
                enabled = nicknameFieldState.text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
