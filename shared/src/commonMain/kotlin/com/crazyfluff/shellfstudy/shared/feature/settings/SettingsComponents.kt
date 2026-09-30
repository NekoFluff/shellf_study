package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalEinkTheme

/*
 * The settings screen's building blocks. Every row shares one padding and one title/subtitle
 * treatment, so a group reads as a single list whatever mix of controls it holds.
 *
 * Only settings uses these. That's why they are internal to the feature and not in designsystem/.
 * This file holds the shared layout (group surface, row padding, title and subtitle). The rows
 * themselves are in SettingsRows.kt, SettingsStepperRow.kt, SettingsChoiceRows.kt and
 * HourPickerDialog.kt.
 */

internal val RowHorizontalPadding = 16.dp
internal val RowVerticalPadding = 12.dp
private val SubRowIndent = 32.dp
internal const val DISABLED_ALPHA = 0.38f

/** The fill behind a settings group. On e-ink the tonal container colours come from the stock light
 *  scheme and render as a faint lavender, so there the group is plain surface and a border marks
 *  its edge instead. */
@Composable
private fun groupContainerColor(): Color =
    if (LocalEinkTheme.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow

/** Same reasoning as [groupContainerColor], for the Daily plan card and the notifications main switch. */
@Composable
internal fun emphasisContainerColor(): Color =
    if (LocalEinkTheme.current) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer

@Composable
internal fun einkBorder(): BorderStroke? =
    if (LocalEinkTheme.current) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null

/** A titled group of rows on one rounded surface. */
@Composable
internal fun SettingsGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = RowHorizontalPadding, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = groupContainerColor(),
            border = einkBorder(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

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
