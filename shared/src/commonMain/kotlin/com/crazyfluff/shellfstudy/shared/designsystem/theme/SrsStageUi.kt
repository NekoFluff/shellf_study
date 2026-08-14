package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import kotlinx.coroutines.delay

/** Shared with [RankChangeChip]'s call site in ReviewScreen so the chip's internal color-morph
 *  animation and the caller's enter transition can't drift out of sync with each other. */
const val RankChangeChipEnterDurationMs = 250
private const val RankChangeChipShimmerDurationMs = 400

@Composable
fun srsStageColor(stage: SrsStage): Color = when (stage) {
    SrsStage.LOCKED -> themeAwareColor(SrsStageColors.Locked, EinkStageColors.Locked)
    SrsStage.APPRENTICE_1, SrsStage.APPRENTICE_2, SrsStage.APPRENTICE_3, SrsStage.APPRENTICE_4 ->
        themeAwareColor(SrsStageColors.Apprentice, EinkStageColors.Apprentice)
    SrsStage.GURU_1, SrsStage.GURU_2 -> themeAwareColor(SrsStageColors.Guru, EinkStageColors.Guru)
    SrsStage.MASTER -> themeAwareColor(SrsStageColors.Master, EinkStageColors.Master)
    SrsStage.ENLIGHTENED -> themeAwareColor(SrsStageColors.Enlightened, EinkStageColors.Enlightened)
    SrsStage.BURNED -> themeAwareColor(
        default = if (LocalDarkTheme.current) BurnedDark else SrsStageColors.Burned,
        einkValue = EinkStageColors.Burned
    )
}

@Composable
fun RankChangeChip(rankChange: RankChange, modifier: Modifier = Modifier) {
    val fromColor = srsStageColor(rankChange.from)
    val toColor = srsStageColor(rankChange.to)
    val shape = RoundedCornerShape(50)

    // Keyed on rankChange so a brand-new rank change (rather than a recomposition of the same one)
    // restarts every animation below from the start.
    val colorProgress = remember(rankChange) { Animatable(0f) }
    val iconScale = remember(rankChange) { Animatable(0.4f) }
    val shimmerProgress = remember(rankChange) { Animatable(0f) }

    LaunchedEffect(rankChange) {
        colorProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = RankChangeChipEnterDurationMs, easing = FastOutSlowInEasing)
        )
    }
    // Delayed until after AnimatedVisibility's own enter transition finishes, so this never scales
    // as part of that transition — a prior scale+overshoot combined with the transition itself
    // visibly clipped against AnimatedVisibility's clip-to-bounds (see ReviewScreen.kt). A scale
    // applied to already-settled, fully visible content has no such bound to clip against.
    LaunchedEffect(rankChange) {
        delay(RankChangeChipEnterDurationMs.toLong())
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }
    if (rankChange.isRankUp) {
        LaunchedEffect(rankChange) {
            shimmerProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = RankChangeChipShimmerDurationMs, easing = LinearEasing)
            )
        }
    }

    val color = lerp(fromColor, toColor, colorProgress.value)

    Box(modifier = modifier.clip(shape)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(color.copy(alpha = 0.15f), shape)
                .border(1.dp, color, shape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = if (rankChange.isRankUp) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = if (rankChange.isRankUp) "Rank up" else "Rank down",
                tint = color,
                modifier = Modifier.size(16.dp).scale(iconScale.value)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = rankChange.to.displayName,
                color = color,
                style = MaterialTheme.typography.labelLarge
            )
        }
        // A translucent brightness sweep, promotions only — reads on the grayscale e-ink palette as
        // a highlight rather than relying on hue, and demotions stay visually muted by comparison.
        if (rankChange.isRankUp) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        val bandWidth = size.width * 0.35f
                        val startX = -bandWidth + shimmerProgress.value * (size.width + bandWidth * 2)
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                                start = Offset(startX, 0f),
                                end = Offset(startX + bandWidth, size.height)
                            )
                        )
                    }
            )
        }
    }
}

/** A subject's current SRS stage as a standalone stat — same pill styling as [RankChangeChip], but
 *  without the up/down arrow (there's no "from" stage to compare against). */
@Composable
fun SrsStageChip(stage: SrsStage, modifier: Modifier = Modifier) {
    val color = srsStageColor(stage)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = stage.displayName,
            color = color,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
