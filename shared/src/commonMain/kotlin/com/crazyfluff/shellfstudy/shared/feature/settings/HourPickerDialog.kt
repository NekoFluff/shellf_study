package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.components.AppAlertDialog
import com.crazyfluff.shellfstudy.shared.designsystem.time.HOURS_PER_DAY
import com.crazyfluff.shellfstudy.shared.designsystem.time.formatHourShort

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
    AppAlertDialog(
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
                    selected = hour == selectedHour.mod(HOURS_PER_DAY),
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
