package com.crazyfluff.shellfstudy.core.designsystem.theme

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
    SrsStage.BURNED -> themeAwareColor(
        default = if (LocalDarkTheme.current) BurnedDark else SrsStageColors.Burned,
        einkValue = EinkStageColors.Burned
    )
}

@Composable
fun RankChangeChip(rankChange: RankChange, modifier: Modifier = Modifier) {
    val color = srsStageColor(rankChange.to)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = if (rankChange.isRankUp) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = if (rankChange.isRankUp) "Rank up" else "Rank down",
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = rankChange.to.displayName,
            color = color,
            style = MaterialTheme.typography.labelLarge
        )
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
