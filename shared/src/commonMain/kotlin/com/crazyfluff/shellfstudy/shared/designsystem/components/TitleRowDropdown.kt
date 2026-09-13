package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A compact "current value + chevron" trigger sitting in a card's title row, opening a menu of
 * [options] with the selected one ticked. Generic over the option type so the dashboard's leaderboard
 * and review-forecast cards can share it — they previously carried near-identical private copies, and
 * the two had already drifted apart: one trigger was a [androidx.compose.material3.TextButton], the
 * other a plain clickable [Row].
 *
 * The [Row] is the one that survived, for a measured reason worth recording: Material3 enforces a
 * ~40dp minimum button height (well above this trigger's own label+chevron size), which inflated the
 * title row it sits in — the title, vertically centred against that taller sibling, ended up with
 * several extra dp of dead space below it that no amount of shrinking the following `Spacer` could
 * remove, because the space was inside the row rather than in the spacer.
 *
 * The cost of that trade is that the trigger contributes no height of its own, so a host whose title
 * row carries little vertical padding has to supply the title's inset itself or the title will sit
 * hard against the card's top edge. [ReviewForecastCard][com.crazyfluff.shellfstudy.shared.feature.dashboard.ReviewForecastCard]
 * gets it from its enclosing `Column(padding(16.dp))`; `LeaderboardCard` pads each row individually
 * (its rows are full-bleed) and so states the top inset explicitly. That is not incidental — the
 * leaderboard card silently lost it when its trigger stopped being a `TextButton`.
 *
 * Consequently the trigger is also shorter than Material's 48.dp touch-target guidance (~24.dp), which
 * both hosts currently accept rather than pay the extra row height. Revisit it and the header insets
 * together if either card's header is re-tuned.
 *
 * [contentDescription] is a parameter because the two hosts word it differently ("Change time window"
 * / "Change forecast window"), and it is the only part of the trigger a screen reader announces.
 */
@Composable
fun <T> TitleRowDropdown(
    selected: T,
    options: List<T>,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier.clickable(onClick = { expanded = true }),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labelOf(selected),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    trailingIcon = if (option == selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}
