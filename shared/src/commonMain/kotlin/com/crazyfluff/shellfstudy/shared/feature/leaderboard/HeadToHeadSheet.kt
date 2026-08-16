package com.crazyfluff.shellfstudy.shared.feature.leaderboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.designsystem.theme.kanjiColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.radicalColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadToHeadSheet(
    friend: FriendStats,
    self: FriendStats,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "You vs. ${friend.nickname}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Lv. ${self.level}  vs.  Lv. ${friend.level}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            val selfColor = kanjiColor()
            val friendColor = radicalColor()

            RadarChart(
                self = self,
                friend = friend,
                selfColor = selfColor,
                friendColor = friendColor,
                modifier = Modifier.size(240.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Win/loss chips per axis — use ALL_TIME window for the head-to-head comparison
            val axes = LeaderboardMetric.entries
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                axes.forEach { metric ->
                    val (selfVal, friendVal) = normalizedValues(self, friend, metric, LeaderboardWindow.ALL_TIME)
                    val outcome = when {
                        selfVal > friendVal + 0.05f -> "You're ahead"
                        friendVal > selfVal + 0.05f -> "${friend.nickname} leads"
                        else -> "Tied"
                    }
                    val label = when (metric) {
                        LeaderboardMetric.LEARNED -> "Lessons"
                        LeaderboardMetric.LEVEL -> "Level"
                        LeaderboardMetric.BURNED -> "Burned"
                        LeaderboardMetric.ACCURACY -> "Accuracy"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        SuggestionChip(
                            onClick = {},
                            label = { Text(outcome, style = MaterialTheme.typography.labelMedium) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = when {
                                    outcome.startsWith("You") ->
                                        MaterialTheme.colorScheme.primaryContainer
                                    outcome.startsWith("Tied") ->
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else ->
                                        MaterialTheme.colorScheme.errorContainer
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarChart(
    self: FriendStats,
    friend: FriendStats,
    selfColor: Color,
    friendColor: Color,
    modifier: Modifier = Modifier
) {
    val axes = LeaderboardMetric.entries
    val axisCount = axes.size
    val gridSteps = 4

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val selfFill = selfColor.copy(alpha = 0.25f)
    val friendFill = friendColor.copy(alpha = 0.15f)

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(cx, cy) * 0.85f

        fun axisOffset(index: Int, r: Float): Offset {
            val angle = (2 * PI / axisCount) * index - PI / 2
            return Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat())
        }

        // Grid rings
        for (step in 1..gridSteps) {
            val r = radius * step / gridSteps
            val gridPath = Path()
            for (i in 0 until axisCount) {
                val pt = axisOffset(i, r)
                if (i == 0) gridPath.moveTo(pt.x, pt.y) else gridPath.lineTo(pt.x, pt.y)
            }
            gridPath.close()
            drawPath(gridPath, gridColor, style = Stroke(width = 1.dp.toPx()))
        }

        // Axis spokes
        for (i in 0 until axisCount) {
            val outer = axisOffset(i, radius)
            drawLine(gridColor, Offset(cx, cy), outer, strokeWidth = 1.dp.toPx())
        }

        // Self/friend polygons — normalized against each other per axis
        val selfPath = Path()
        val friendPath = Path()
        axes.forEachIndexed { i, metric ->
            val (selfV, friendV) = normalizedValues(self, friend, metric, LeaderboardWindow.ALL_TIME)
            val sr = radius * selfV
            val fr = radius * friendV
            val spt = axisOffset(i, sr)
            val fpt = axisOffset(i, fr)
            if (i == 0) { selfPath.moveTo(spt.x, spt.y); friendPath.moveTo(fpt.x, fpt.y) }
            else { selfPath.lineTo(spt.x, spt.y); friendPath.lineTo(fpt.x, fpt.y) }
        }
        selfPath.close(); friendPath.close()

        drawPath(friendPath, friendFill, style = Fill)
        drawPath(friendPath, friendColor, style = Stroke(width = 2.dp.toPx()))
        drawPath(selfPath, selfFill, style = Fill)
        drawPath(selfPath, selfColor, style = Stroke(width = 2.dp.toPx()))
    }
}

private fun normalizedValues(
    self: FriendStats,
    friend: FriendStats,
    metric: LeaderboardMetric,
    window: LeaderboardWindow
): Pair<Float, Float> = when (metric) {
    LeaderboardMetric.LEARNED -> {
        val sv = self.learned.forWindow(window).toFloat()
        val fv = friend.learned.forWindow(window).toFloat()
        val max = maxOf(sv, fv, 1f)
        (sv / max) to (fv / max)
    }
    LeaderboardMetric.LEVEL -> {
        val max = maxOf(self.level, friend.level, 1).toFloat()
        (self.level / max) to (friend.level / max)
    }
    LeaderboardMetric.BURNED -> {
        val sv = self.burned.forWindow(window).toFloat()
        val fv = friend.burned.forWindow(window).toFloat()
        val max = maxOf(sv, fv, 1f)
        (sv / max) to (fv / max)
    }
    LeaderboardMetric.ACCURACY -> {
        val sv = self.reviewAccuracy.coerceAtLeast(0f)
        val fv = friend.reviewAccuracy.coerceAtLeast(0f)
        val max = maxOf(sv, fv, 0.01f)
        (sv / max) to (fv / max)
    }
}
