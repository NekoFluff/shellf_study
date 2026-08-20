package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Six-slot color palette for per-user identity colors — shared by the dashboard's leaderboard rows
 *  and race-chart user lines, and by the full leaderboard screen's friend avatars. Index into it
 *  with `palette[index % palette.size]` so it wraps for more than six users. */
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
