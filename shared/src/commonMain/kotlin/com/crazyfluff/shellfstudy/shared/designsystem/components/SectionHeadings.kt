package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The subject detail page's plain section heading — `titleMedium` SemiBold, nothing else. One
 * component so "Stats", "Meaning mnemonic", "Reading mnemonic" and "Context sentences" cannot drift
 * apart: they are all the same heading at the same weight, and only the content under them differs.
 */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(text = title, modifier = modifier, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

/**
 * A section heading for the tile-grid sections ("Radicals", "Kanji", "Visually similar", "Used in"),
 * with a short colour bar beside it. [accent] is the subject's own type colour (see `subjectColor`) —
 * the same tint the tiles themselves carry, so the bar reads as a marker for that kind of section
 * rather than as decoration on a heading. The prose sections deliberately use the plain [SectionTitle]
 * instead: a coloured chip in front of every heading made them compete with the content, and the bar
 * only means something where there are coloured tiles to match.
 */
@Composable
fun SectionAccentHeader(title: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(14.dp)
                .background(accent, RoundedCornerShape(2.dp))
        )
        Text(text = title, style = MaterialTheme.typography.labelLarge)
    }
}
