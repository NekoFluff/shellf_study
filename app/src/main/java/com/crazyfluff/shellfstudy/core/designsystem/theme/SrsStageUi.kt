package com.crazyfluff.shellfstudy.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
