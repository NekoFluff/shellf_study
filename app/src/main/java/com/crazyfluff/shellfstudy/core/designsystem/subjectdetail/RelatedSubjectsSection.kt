package com.crazyfluff.shellfstudy.core.designsystem.subjectdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor

/**
 * A titled, wrapping grid of [SubjectTile]s — renders nothing when [subjects] is empty. The title
 * carries a small accent bar colored by the first subject's type (radical/kanji/vocabulary), so a
 * "Radicals" section reads blue-tinted and a "Used in" vocabulary section reads pink-tinted at a
 * glance, matching the same accent already used on each tile's border.
 */
@Composable
fun RelatedSubjectsSection(
    title: String,
    subjects: List<SubjectSummary>,
    onSubjectClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (subjects.isEmpty()) return

    val accent = subjectColor(subjects.first().subjectType)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(14.dp)
                    .background(accent, RoundedCornerShape(2.dp))
            )
            Text(text = title, style = MaterialTheme.typography.labelLarge)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            subjects.forEach { subject ->
                SubjectTile(subject = subject, onClick = onSubjectClick)
            }
        }
    }
}
