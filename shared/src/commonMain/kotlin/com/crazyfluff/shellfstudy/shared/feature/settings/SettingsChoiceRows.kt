package com.crazyfluff.shellfstudy.shared.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.theme.segmentedButtonColors

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
