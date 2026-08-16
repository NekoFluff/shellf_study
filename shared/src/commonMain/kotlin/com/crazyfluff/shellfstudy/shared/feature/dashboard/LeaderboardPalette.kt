package com.crazyfluff.shellfstudy.shared.feature.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crazyfluff.shellfstudy.shared.designsystem.theme.EinkExtraColors
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalEinkTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.kanjiColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.radicalColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.vocabularyColor

/** Six-slot color palette shared by the leaderboard rows and the race chart user lines. */
@Composable
fun leaderboardUserPalette(): List<Color> {
    val isEink = LocalEinkTheme.current
    return listOf(
        kanjiColor(),
        radicalColor(),
        vocabularyColor(),
        if (isEink) EinkExtraColors.Slot4 else Color(0xFFE65100),
        if (isEink) EinkExtraColors.Slot5 else Color(0xFF00695C),
        if (isEink) EinkExtraColors.Slot6 else Color(0xFF1565C0),
    )
}
