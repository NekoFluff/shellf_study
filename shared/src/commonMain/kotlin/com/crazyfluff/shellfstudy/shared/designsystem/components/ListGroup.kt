package com.crazyfluff.shellfstudy.shared.designsystem.components

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
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.designsystem.theme.einkBorder
import com.crazyfluff.shellfstudy.shared.designsystem.theme.groupContainerColor

/** Horizontal inset of a [ListGroup]'s title, matching the start padding of the rows inside it. */
val ListGroupTitleInset = 16.dp

/**
 * A titled group of rows on one rounded surface, the building block of the grouped-list screens
 * (Settings, Friends). The title sits above the surface in the primary colour, and [title] can be
 * null for a group that needs no heading.
 */
@Composable
fun ListGroup(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = ListGroupTitleInset, bottom = 8.dp)
            )
        }
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
