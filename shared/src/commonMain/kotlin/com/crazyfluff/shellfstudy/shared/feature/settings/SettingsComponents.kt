package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * The settings screen's building blocks. Every row shares one padding and one title/subtitle
 * treatment, so a group reads as a single list whatever mix of controls it holds.
 *
 * Only settings uses these rows. That's why they are internal to the feature and not in
 * designsystem/. The group surface they sit on is designsystem's ListGroup, shared with Friends.
 * This file holds the shared row layout (padding, title and subtitle). The rows themselves are in
 * SettingsRows.kt, SettingsStepperRow.kt, SettingsChoiceRows.kt and HourPickerDialog.kt.
 */

internal val RowHorizontalPadding = 16.dp
internal val RowVerticalPadding = 12.dp
private val SubRowIndent = 32.dp
internal const val DISABLED_ALPHA = 0.38f

/** Title with an optional one-line-ish subtitle, dimmed together when the row is disabled. */
@Composable
internal fun RowText(title: String, subtitle: String?, enabled: Boolean, modifier: Modifier = Modifier) {
    val alpha = if (enabled) 1f else DISABLED_ALPHA
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
            )
        }
    }
}

internal fun Modifier.rowPadding(indent: Boolean) =
    padding(
        start = if (indent) SubRowIndent else RowHorizontalPadding,
        end = RowHorizontalPadding,
        top = RowVerticalPadding,
        bottom = RowVerticalPadding
    )
