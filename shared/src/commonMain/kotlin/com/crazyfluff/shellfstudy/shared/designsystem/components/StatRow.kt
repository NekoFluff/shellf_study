package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Rounds to one decimal place via integer arithmetic (tenths of a percent) rather than Double
// formatting, since String.format/"%.1f" aren't available in commonMain.
fun formatPercentOneDecimal(count: Int, total: Int): String {
    if (total <= 0) return "0.0"
    val tenths = (count * 1000 + total / 2) / total
    return "${tenths / 10}.${tenths % 10}"
}

/** A color swatch, "label: count" and a "count/total" percentage — one row of a breakdown list.
 *  Shared by every card that lists out a chart's segments as text (e.g. ItemSpreadCard,
 *  ReviewForecastCard) so the swatch/label/percent layout lives in exactly one place. */
@Composable
fun StatRow(color: Color, label: String, count: Int, total: Int, modifier: Modifier = Modifier) {
    val percent = formatPercentOneDecimal(count, total)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$label: $count",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$percent%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
