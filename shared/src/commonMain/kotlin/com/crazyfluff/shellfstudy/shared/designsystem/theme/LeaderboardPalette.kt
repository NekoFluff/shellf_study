package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/**
 * The one place a participant's identity color comes from — called with
 * [com.crazyfluff.shellfstudy.shared.data.model.FriendStats.rosterIndex], the same value on every
 * surface that colors a user (the dashboard's leaderboard rows, both race charts' lines, dots and
 * legends, and the full leaderboard screen's avatars). Deriving the color from roster position
 * rather than list position is what keeps a user's color identical across those surfaces: they sort
 * their entries differently, filter them differently, and re-sort on every metric/window change.
 *
 * Wraps for rosters larger than the palette, so the first and seventh participant share a color.
 * Six slots is exactly self + five friends, i.e. the number of rows the dashboard shows.
 */
@Composable
fun leaderboardUserColor(rosterIndex: Int): Color {
    val palette = rememberLeaderboardUserPalette()
    return palette[rosterIndex.mod(palette.size)]
}

/**
 * Six-slot palette behind [leaderboardUserColor] — first three slots reuse the app's three subject
 * colors, the rest are chosen to stay distinguishable from them and from each other.
 *
 * Remembered because this is called per row, per chart line and per legend entry: the list was
 * rebuilt on every one of those calls, several dozen allocations per frame while a chart animates.
 */
@Composable
private fun rememberLeaderboardUserPalette(): List<Color> {
    val isEink = LocalEinkTheme.current
    val kanji = kanjiColor()
    val radical = radicalColor()
    val vocabulary = vocabularyColor()
    return remember(isEink, kanji, radical, vocabulary) {
        listOf(
            kanji,
            radical,
            vocabulary,
            if (isEink) EinkExtraColors.Slot4 else Color(0xFFE65100),
            if (isEink) EinkExtraColors.Slot5 else Color(0xFF00695C),
            if (isEink) EinkExtraColors.Slot6 else Color(0xFF1565C0),
        )
    }
}
