package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.crazyfluff.shellfstudy.shared.data.model.Leaderboard
import com.crazyfluff.shellfstudy.shared.designsystem.theme.SubjectTypeColors

private val raceChartPalette = listOf(
    SubjectTypeColors.Kanji,
    SubjectTypeColors.Radical,
    SubjectTypeColors.Vocabulary,
    Color(0xFFE65100),
    Color(0xFF00695C),
    Color(0xFF1565C0)
)

@Composable
fun RaceChartCard(
    leaderboard: Leaderboard,
    modifier: Modifier = Modifier
) {
    val usersWithTimeline = leaderboard.entries.filter { it.levelTimeline.isNotEmpty() }
    if (usersWithTimeline.isEmpty()) return

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Level Race",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Days on WaniKani vs. level reached",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            val maxDays = usersWithTimeline.maxOf { user ->
                user.levelTimeline.maxOf { it.daysSinceStart }
            }.coerceAtLeast(1)
            val maxLevel = 60
            val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            val onSurface = MaterialTheme.colorScheme.onSurface

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val w = size.width
                val h = size.height

                // Horizontal grid lines at levels 10, 20, 30, 40, 50
                for (lvl in listOf(10, 20, 30, 40, 50)) {
                    val y = h - (lvl.toFloat() / maxLevel) * h
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }

                usersWithTimeline.forEachIndexed { idx, user ->
                    val color = raceChartPalette.getOrElse(idx) { raceChartPalette.last() }
                    val strokeWidth = if (user.isCurrentUser) 3.dp.toPx() else 1.5.dp.toPx()

                    if (user.levelTimeline.size == 1) {
                        val pt = user.levelTimeline.first()
                        val x = (pt.daysSinceStart.toFloat() / maxDays) * w
                        val y = h - (pt.level.toFloat() / maxLevel) * h
                        drawCircle(color, radius = 4.dp.toPx(), center = Offset(x, y))
                    } else {
                        val path = Path()
                        user.levelTimeline.forEachIndexed { pIdx, pt ->
                            val x = (pt.daysSinceStart.toFloat() / maxDays) * w
                            val y = h - (pt.level.toFloat() / maxLevel) * h
                            if (pIdx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path, color,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        // Endpoint dot
                        val last = user.levelTimeline.last()
                        val ex = (last.daysSinceStart.toFloat() / maxDays) * w
                        val ey = h - (last.level.toFloat() / maxLevel) * h
                        drawCircle(color, radius = 4.dp.toPx(), center = Offset(ex, ey))
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Legend
            usersWithTimeline.forEachIndexed { idx, user ->
                val color = raceChartPalette.getOrElse(idx) { raceChartPalette.last() }
                RaceChartLegendRow(
                    color = color,
                    label = user.nickname,
                    isCurrentUser = user.isCurrentUser
                )
            }
        }
    }
}

@Composable
private fun RaceChartLegendRow(
    color: Color,
    label: String,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(10.dp)) { drawCircle(color) }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
            fontSize = 12.sp
        )
    }
}
