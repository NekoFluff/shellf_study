package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.crazyfluff.shellfstudy.shared.util.formatAnswerList

/**
 * Renders a comma-joined candidate list (answer synonyms, auxiliary meanings) that summarizes to
 * "+N more" once [formatAnswerList] sees more than it wants to spell out, one line with an
 * ellipsis otherwise — tap to expand and see the rest.
 *
 * Item count alone isn't a reliable truncation signal: a handful of long entries can overflow the
 * single line even when [formatAnswerList] finds nothing left to summarize. Expandability latches
 * on the first time Compose's own text layout reports visual overflow too, not just on the
 * count-based [com.crazyfluff.shellfstudy.shared.util.AnswerListDisplay.hasMore] — otherwise a
 * truncated line renders with no way to reveal what got cut off. It stays latched (rather than
 * re-evaluating every layout pass) so the same tap target keeps working to collapse back down
 * once expanded, when the text no longer overflows.
 */
@Composable
fun ExpandableAnswerListText(
    joined: String,
    resetKey: Any?,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    expandedMaxHeight: Dp = Dp.Unspecified,
) {
    var isExpanded by remember(resetKey) { mutableStateOf(false) }
    var isTruncated by remember(resetKey) { mutableStateOf(false) }
    val display = formatAnswerList(joined, expanded = isExpanded)
    val canExpand = display.hasMore || isTruncated

    Text(
        text = if (prefix != null) "$prefix ${display.text}" else display.text,
        style = style,
        color = if (canExpand) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = if (isExpanded) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layoutResult -> if (layoutResult.hasVisualOverflow) isTruncated = true },
        modifier = modifier
            .then(
                if (isExpanded && expandedMaxHeight != Dp.Unspecified) {
                    Modifier.heightIn(max = expandedMaxHeight).verticalScroll(rememberScrollState())
                } else {
                    Modifier
                }
            )
            .then(if (canExpand) Modifier.clickable { isExpanded = !isExpanded } else Modifier)
    )
}
