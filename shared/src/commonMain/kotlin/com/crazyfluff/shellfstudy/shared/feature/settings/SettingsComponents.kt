package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonColors
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalEinkTheme
import com.crazyfluff.shellfstudy.shared.designsystem.time.formatHourShort
import kotlinx.coroutines.delay

/*
 * The settings screen's building blocks. Every row shares one padding and one title/subtitle
 * treatment, so a group reads as a single list whatever mix of controls it holds.
 *
 * Only settings uses these. That's why they are internal to the feature and not in designsystem/.
 */

private val RowHorizontalPadding = 16.dp
private val RowVerticalPadding = 12.dp
private val SubRowIndent = 32.dp
private const val DISABLED_ALPHA = 0.38f

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
private fun RowText(title: String, subtitle: String?, enabled: Boolean, modifier: Modifier = Modifier) {
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

private fun Modifier.rowPadding(indent: Boolean) =
    padding(
        start = if (indent) SubRowIndent else RowHorizontalPadding,
        end = RowHorizontalPadding,
        top = RowVerticalPadding,
        bottom = RowVerticalPadding
    )

/** A switch row. The whole row is the touch target, and [testTag] goes on the row, which carries
 *  the switch's toggle semantics. */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
    subtitle: String? = null,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(testTag)
            .rowPadding(indent = false)
    ) {
        RowText(title, subtitle, enabled, Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * The notifications "main switch": a prominent pill that gates every row after it. It's styled
 * differently from [SwitchRow] because its job is different. Turning it off silences everything
 * beneath it.
 */
@Composable
internal fun MainSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (checked) emphasisContainerColor() else MaterialTheme.colorScheme.surfaceVariant,
        border = einkBorder(),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
                .testTag(testTag)
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/**
 * A −/+ stepper. [range] disables whichever button would step past it.
 *
 * Holding a button repeats the step. With ranges like 1–99 lessons or 5–500 backlog, tapping one
 * step at a time was the slowest part of the old screen.
 */
@Composable
internal fun StepperRow(
    title: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    testTags: StepperRowTestTags,
    range: IntRange,
    step: Int = 1,
    subtitle: String? = null,
    indent: Boolean = false,
    valueLabel: (Int) -> String = { it.toString() }
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().rowPadding(indent)
    ) {
        RowText(title, subtitle, enabled = true, modifier = Modifier.weight(1f).padding(end = 8.dp))
        RepeatingIconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
            testTag = testTags.decrease
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease $title")
        }
        Text(
            text = valueLabel(value),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 48.dp).testTag(testTags.value),
            textAlign = TextAlign.Center
        )
        RepeatingIconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
            testTag = testTags.increase
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase $title")
        }
    }
}

private const val REPEAT_INITIAL_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 75L

/**
 * An [IconButton] that repeats [onClick] while held. A tap is still exactly one click.
 *
 * A press that has already repeated doesn't also click on release. If it did, the step after the
 * user let go would be one past where they stopped.
 */
@Composable
private fun RepeatingIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    testTag: String,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    val currentEnabled by rememberUpdatedState(enabled)
    // A plain holder, not snapshot state: it's only read in the click callback, never while drawing.
    val repeated = remember { booleanArrayOf(false) }

    LaunchedEffect(pressed) {
        if (!pressed) return@LaunchedEffect
        repeated[0] = false
        delay(REPEAT_INITIAL_DELAY_MS)
        while (currentEnabled) {
            repeated[0] = true
            currentOnClick()
            delay(REPEAT_INTERVAL_MS)
        }
    }

    IconButton(
        onClick = {
            if (!repeated[0]) onClick()
            repeated[0] = false
        },
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.testTag(testTag),
        content = content
    )
}

/** A row that opens something: a picker (with the current [value] shown trailing) or another screen. */
@Composable
internal fun NavRow(
    title: String,
    onClick: () -> Unit,
    testTag: String? = null,
    subtitle: String? = null,
    value: String? = null,
    enabled: Boolean = true,
    indent: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .rowPadding(indent)
    ) {
        RowText(title, subtitle, enabled, Modifier.weight(1f).padding(end = 16.dp))
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
            )
        }
    }
}

/** A read-only row: a fact about the app or account, with no control. */
@Composable
internal fun InfoRow(title: String, subtitle: String?, testTag: String) {
    RowText(
        title = title,
        subtitle = subtitle,
        enabled = true,
        modifier = Modifier.fillMaxWidth().testTag(testTag).rowPadding(indent = false)
    )
}

/** An action that can't be undone from this screen, like log out. It's drawn in the error colour
 *  with a leading icon, so it doesn't read as another setting. */
@Composable
internal fun DestructiveRow(title: String, icon: ImageVector, onClick: () -> Unit, testTag: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag)
            .rowPadding(indent = false)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
    }
}

/** On e-ink the stock selected-segment fill is a pale lavender that reads as unselected. There the
 *  selected segment is solid ink instead. */
@Composable
private fun segmentedButtonColors(): SegmentedButtonColors =
    if (LocalEinkTheme.current) {
        SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.onSurface,
            activeContentColor = MaterialTheme.colorScheme.surface
        )
    } else {
        SegmentedButtonDefaults.colors()
    }

/** One option of a [SingleChoiceRow]. */
internal data class ChoiceOption<T>(val value: T, val label: String, val testTag: String)

/**
 * A title with a single-choice segmented button under it. Used for small, fixed sets where every
 * option fits in one line: the theme and the review order.
 *
 * [subtitle] can depend on the selection, which lets a two-way choice explain the option that's
 * actually picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SingleChoiceRow(
    title: String,
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth().rowPadding(indent = false)) {
        RowText(title, subtitle, enabled = true)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = segmentedButtonColors(),
                    // No checkmark. The fill already marks the selection, and in a four-way
                    // row the icon costs the width the labels need.
                    icon = {},
                    label = { Text(option.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.testTag(option.testTag)
                )
            }
        }
    }
}

/** One independently toggled option of a [MultiChoiceRow]. */
internal data class ToggleOption(
    val label: String,
    val checked: Boolean,
    val onCheckedChange: (Boolean) -> Unit,
    val testTag: String
)

/**
 * A title with a multi-choice segmented button under it. It holds two settings that are really
 * one question with two answers: which timers to show, and which answer types to hold back.
 * Each segment still toggles its own setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MultiChoiceRow(
    title: String,
    options: List<ToggleOption>,
    subtitle: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth().rowPadding(indent = false)) {
        RowText(title, subtitle, enabled = true)
        MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    checked = option.checked,
                    onCheckedChange = option.onCheckedChange,
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    colors = segmentedButtonColors(),
                    label = { Text(option.label, maxLines = 1) },
                    modifier = Modifier.testTag(option.testTag)
                )
            }
        }
    }
}

/**
 * An hour picker: the 24 hours as a grid, where one tap picks and closes. It replaces a ±1 stepper
 * that could take twelve taps to cross the day.
 *
 * On a 12-hour clock the grid splits into AM and PM halves, so each cell only needs the bare
 * number.
 */
@Composable
internal fun HourPickerDialog(
    title: String,
    selectedHour: Int,
    is24h: Boolean,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (is24h) {
                    HourGrid(hours = 0 until 24, selectedHour, is24h, onSelect)
                } else {
                    HalfDayLabel("AM")
                    HourGrid(hours = 0 until 12, selectedHour, is24h, onSelect)
                    HalfDayLabel("PM", Modifier.padding(top = 8.dp))
                    HourGrid(hours = 12 until 24, selectedHour, is24h, onSelect)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun HalfDayLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

private const val HOURS_PER_GRID_ROW = 6

@Composable
private fun HourGrid(hours: IntRange, selectedHour: Int, is24h: Boolean, onSelect: (Int) -> Unit) {
    hours.chunked(HOURS_PER_GRID_ROW).forEach { rowHours ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            rowHours.forEach { hour ->
                HourCell(
                    hour = hour,
                    selected = hour == selectedHour.mod(24),
                    is24h = is24h,
                    onClick = { onSelect(hour) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HourCell(hour: Int, selected: Boolean, is24h: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(SettingsScreenTestTags.hourOptionTag(hour))
    ) {
        Text(
            text = formatHourShort(hour, is24h),
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        )
    }
}
