package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor

/** A tappable related-subject card: glyph + one meaning + one reading, used in related-subject grids and search results. */
@Composable
fun SubjectTile(
    subject: SubjectSummary,
    onClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = subjectColor(subject.subjectType)
    Surface(
        onClick = { onClick(subject.subjectId) },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
        modifier = modifier.width(88.dp).testTag("subject_tile_${subject.subjectId}")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SubjectGlyph(
                characters = subject.characters,
                characterImageUrl = subject.characterImageUrl,
                subjectType = subject.subjectType,
                size = 32.dp
            )
            Text(
                text = subject.meanings.firstOrNull().orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subject.readings.isNotEmpty()) {
                Text(
                    text = subject.readings.first(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
