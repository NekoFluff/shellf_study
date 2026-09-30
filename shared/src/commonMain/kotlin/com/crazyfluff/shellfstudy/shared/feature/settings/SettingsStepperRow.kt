package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

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
