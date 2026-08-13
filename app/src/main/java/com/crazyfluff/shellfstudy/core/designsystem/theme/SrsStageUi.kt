package com.crazyfluff.shellfstudy.core.designsystem.theme

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.data.model.RankChange
import com.crazyfluff.shellfstudy.core.data.model.SrsStage

@Composable
fun srsStageColor(stage: SrsStage): Color = when (stage) {
    SrsStage.LOCKED -> themeAwareColor(SrsStageColors.Locked, EinkStageColors.Locked)
    SrsStage.APPRENTICE_1, SrsStage.APPRENTICE_2, SrsStage.APPRENTICE_3, SrsStage.APPRENTICE_4 ->
        themeAwareColor(SrsStageColors.Apprentice, EinkStageColors.Apprentice)
    SrsStage.GURU_1, SrsStage.GURU_2 -> themeAwareColor(SrsStageColors.Guru, EinkStageColors.Guru)
    SrsStage.MASTER -> themeAwareColor(SrsStageColors.Master, EinkStageColors.Master)
    SrsStage.ENLIGHTENED -> themeAwareColor(SrsStageColors.Enlightened, EinkStageColors.Enlightened)
    SrsStage.BURNED -> themeAwareColor(SrsStageColors.Burned, EinkStageColors.Burned)
}

@Composable
fun RankChangeChip(rankChange: RankChange, modifier: Modifier = Modifier) {
    val fromColor = srsStageColor(rankChange.from)
    val toColor = srsStageColor(rankChange.to)
    // Animatable directly, rather than animateColorAsState — that composable is itself a
    // remember{Animatable}+LaunchedEffect wrapper, so driving a separate mutableStateOf just to
    // hand it a "target" would be animating through a proxy for no benefit. animateTo also lets
    // this start immediately from fromColor without the extra recomposition animateColorAsState
    // needs to notice its targetValue changed.
    val color = remember(rankChange) { Animatable(fromColor) }
    LaunchedEffect(rankChange) { color.animateTo(toColor, tween(durationMillis = 500)) }
    val animatedColor = color.value

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(animatedColor.copy(alpha = 0.15f))
            .border(1.dp, animatedColor, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (rankChange.isRankUp) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = if (rankChange.isRankUp) "Rank up" else "Rank down",
            tint = animatedColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = rankChange.to.displayName,
            color = animatedColor,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
