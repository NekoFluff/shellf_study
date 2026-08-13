package com.crazyfluff.shellfstudy.core.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Draws [segments] (color to count) as contiguous proportional-width rectangles filling the
 *  DrawScope's bounds — shared by every multi-segment progress bar in the app so the
 *  proportional-width math lives in exactly one place. */
fun DrawScope.drawProportionalSegments(segments: List<Pair<Color, Int>>) {
    val total = segments.sumOf { it.second }.coerceAtLeast(1)
    var xOffset = 0f
    segments.forEach { (color, count) ->
        val segmentWidth = size.width * (count.toFloat() / total)
        drawRect(color = color, topLeft = Offset(xOffset, 0f), size = Size(segmentWidth, size.height))
        xOffset += segmentWidth
    }
}

/** A horizontal bar of contiguous, proportionally-sized [segments] (color to count). Optionally
 *  marks [thresholdFraction] (0f-1f, position along the bar's own total) with a thin vertical
 *  line — e.g. a goal the segments are progressing toward, distinct from the segments themselves. */
@Composable
fun SegmentedBar(
    segments: List<Pair<Color, Int>>,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    thresholdFraction: Float? = null,
    thresholdColor: Color = Color.White
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
    ) {
        drawProportionalSegments(segments)
        if (thresholdFraction != null) {
            val x = size.width * thresholdFraction.coerceIn(0f, 1f)
            drawLine(
                color = thresholdColor,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
