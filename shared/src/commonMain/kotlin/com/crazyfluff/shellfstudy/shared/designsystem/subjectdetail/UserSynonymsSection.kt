package com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.ApiResult
import com.crazyfluff.shellfstudy.shared.data.UserSynonymsStore
import com.crazyfluff.shellfstudy.shared.designsystem.components.AppTextInputDialog
import kotlinx.coroutines.launch

/**
 * Where the user's own synonyms are read and edited, provided once at the app root (see
 * [com.crazyfluff.shellfstudy.shared.ShellfStudyApp]) so every view of a subject's meaning gets the
 * same editable row without a callback threaded down through each screen. Nullable on purpose, like
 * [LocalPronunciationAudioPlayer]: a test or preview that provides none gets no row, rather than one
 * whose buttons do nothing.
 */
val LocalUserSynonyms = staticCompositionLocalOf<UserSynonymsStore?> { null }

object UserSynonymsTestTags {
    const val ROW = "user_synonyms_row"
    const val ADD_CHIP = "user_synonyms_add_chip"
    const val FIELD = "user_synonyms_field"
    const val CONFIRM = "user_synonyms_confirm"
    const val ERROR = "user_synonyms_error"

    fun chip(synonym: String) = "user_synonyms_chip_$synonym"
    fun remove(synonym: String) = "user_synonyms_remove_$synonym"
}

/**
 * The user's synonyms for [subjectId] as chips, each removable with its ✕, followed by a
 * "+ Synonym" chip that adds one. Sits under the meaning wherever a subject's meaning is shown — the detail sheet
 * from every screen, and the lesson study card — and is hidden wherever the meaning is.
 *
 * Edits show at once and are rolled back with the reason underneath if WaniKani refuses them (see
 * [com.crazyfluff.shellfstudy.shared.data.StudyMaterialRepository]). Removal asks nothing: the chip
 * is one tap from coming back, and a confirmation would cost every deliberate removal a second tap.
 */
@Composable
fun UserSynonymsSection(subjectId: Long, modifier: Modifier = Modifier) {
    val store = LocalUserSynonyms.current ?: return
    val synonyms by remember(store, subjectId) { store.observeSynonyms(subjectId) }
        .collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var error by remember(subjectId) { mutableStateOf<String?>(null) }
    var adding by remember(subjectId) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().testTag(UserSynonymsTestTags.ROW)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            synonyms.forEach { synonym ->
                SynonymChip(synonym) {
                    error = null
                    scope.launch {
                        val result = store.removeSynonym(subjectId, synonym)
                        if (result is ApiResult.Error) error = result.message
                    }
                }
            }
            AssistChip(
                onClick = {
                    error = null
                    adding = true
                },
                label = { Text("Synonym") },
                leadingIcon = {
                    Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(AssistChipDefaults.IconSize))
                },
                modifier = Modifier.testTag(UserSynonymsTestTags.ADD_CHIP)
            )
        }
        error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag(UserSynonymsTestTags.ERROR)
            )
        }
    }

    if (adding) {
        AddSynonymDialog(
            onAdd = { text -> store.addSynonym(subjectId, text) },
            onDismiss = { adding = false }
        )
    }
}

/** One of the user's synonyms; its ✕ calls [onRemove]. */
@Composable
private fun SynonymChip(synonym: String, onRemove: () -> Unit) {
    InputChip(
        selected = false,
        onClick = {},
        label = { Text(synonym) },
        trailingIcon = {
            Icon(
                Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .testTag(UserSynonymsTestTags.remove(synonym))
            )
        },
        modifier = Modifier.testTag(UserSynonymsTestTags.chip(synonym))
    )
}

/**
 * One field, focused on open; Done or Add saves and closes. A refusal stays in the dialog under the
 * field, with the typed text kept, so it can be corrected rather than retyped.
 */
@Composable
private fun AddSynonymDialog(onAdd: suspend (String) -> ApiResult<Unit>, onDismiss: () -> Unit) {
    val field = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val submit: () -> Unit = submit@{
        val text = field.text.toString()
        if (saving || text.isBlank()) return@submit
        saving = true
        error = null
        scope.launch {
            when (val result = onAdd(text)) {
                is ApiResult.Success -> onDismiss()
                is ApiResult.Error -> {
                    error = result.message
                    saving = false
                }
            }
        }
    }

    AppTextInputDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a synonym") },
        text = {
            OutlinedTextField(
                state = field,
                label = { Text("Meaning") },
                supportingText = error?.let { message -> { Text(message) } },
                isError = error != null,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done
                ),
                onKeyboardAction = { submit() },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .testTag(UserSynonymsTestTags.FIELD)
            )
        },
        confirmButton = {
            TextButton(
                onClick = submit,
                enabled = !saving && field.text.isNotBlank(),
                modifier = Modifier.testTag(UserSynonymsTestTags.CONFIRM)
            ) {
                if (saving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                }
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
