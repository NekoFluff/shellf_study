package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A single-select row of chips — the dashboard cards' "which metric / which window / which colour
 * mode" control.
 *
 * It existed twice, copied between the leaderboard card and the forecast card, including the
 * selected/unselected colour and border dance. Two copies of a widget that has to look identical on
 * two cards is one copy too many, and the styling is the part that has to agree.
 *
 * [label] is expected to be a stable reference (a top-level val, not a lambda literal at the call
 * site) so the row can skip recomposition when the selection changes without its options changing.
 */
@Composable
fun <T> PillSelector(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(option) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                // No border when selected: the filled container is the selection signal, and a
                // border on top of it reads as a second, disagreeing one.
                border = if (isSelected) {
                    null
                } else {
                    FilterChipDefaults.filterChipBorder(enabled = true, selected = false)
                },
                label = { Text(text = label(option), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}
