package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The destructive "abandon session" entry in a session's overflow menu.
 *
 * Three screens offer one — the dashboard for a running review or lesson session, and each of those
 * two screens for its own — and each had written the same error-tinted item out by hand. Only the
 * label and the test tag differ, so those are the parameters.
 *
 * The confirmation the entry opens is deliberately *not* here: `ConfirmationDialog` already covers
 * the shared shape, and the wording is per-context (`"...the batch you're on... are dropped"` on a
 * lesson screen is not true of a review), so it stays with the call site.
 */
@Composable
fun AbandonSessionMenuItem(label: String, testTag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    )
}
