package com.crazyfluff.shellfstudy.shared.designsystem.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.model.SrsCounts
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.designsystem.components.AppModalBottomSheet
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalEinkTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.einkBorder
import com.crazyfluff.shellfstudy.shared.designsystem.theme.srsStageColor
import com.crazyfluff.shellfstudy.shared.util.formatRelativeTime
import kotlin.math.roundToInt

/*
 * A person's details panel, opened from a row on the Friends page (where they can be renamed and
 * removed) and from the dashboard's leaderboard card (read-only). One copy here so the two can't
 * drift apart.
 */

object FriendDetailsTestTags {
    const val SHEET = "friend_details_sheet"
    const val SRS_ROW = "friend_details_srs_row"
    const val UPDATED = "friend_details_updated"
    const val RENAME_BUTTON = "friend_details_rename"
    const val REMOVE_BUTTON = "friend_details_remove"
}

private val DefaultAvatarSize = 40.dp
private val TileSpacing = 8.dp
private const val PERCENT = 100

/** A person's initial on their roster colour, the same avatar the dashboard card draws. */
@Composable
fun FriendAvatar(name: String, color: Color, size: Dp = DefaultAvatarSize) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).clip(CircleShape).background(color)
    ) {
        Text(
            text = (name.firstOrNull() ?: '?').uppercaseChar().toString(),
            color = Color.White,
            style = if (size > DefaultAvatarSize) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.Bold
        )
    }
}

/** Who the panel is about. [stats] is null for a friend whose stats haven't loaded. */
data class FriendDetailsSubject(
    val nickname: String,
    val color: Color,
    val stats: FriendStats?
)

/**
 * The panel. Rename and remove are shown only when [onRename] and [onRemove] are given, which is on
 * the Friends page. The counts cover [window], so opened from the dashboard card they match the
 * window the card is showing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendDetailsSheet(
    subject: FriendDetailsSubject,
    window: LeaderboardWindow,
    onDismiss: () -> Unit,
    onRename: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            // Scrollable: a sheet's content isn't, and with every tile present on a small screen
            // or at a large font size the SRS row and the buttons would be cut off below it.
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .testTag(FriendDetailsTestTags.SHEET)
        ) {
            SheetHeader(subject)
            val stats = subject.stats
            if (stats == null) {
                Text(
                    text = "Pull down on the Friends page to try fetching their stats again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // One column at the tiles' own 8dp spacing, so the SRS row sits as close to the
                // last tile as the tiles sit to each other.
                Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
                    StatGrid(stats, window)
                    stats.srsCounts?.let { SrsRow(it) }
                }
            }
            if (onRename != null || onRemove != null) {
                SheetActions(onRename, onRemove)
            }
        }
    }
}

@Composable
private fun SheetHeader(subject: FriendDetailsSubject) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FriendAvatar(subject.nickname, subject.color, size = 56.dp)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(subject.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            // Name, then the level on its own line, then who they are on WaniKani and how fresh
            // the figures are.
            val stats = subject.stats
            Text(
                text = stats?.let(::levelLine) ?: "Stats not loaded yet",
                style = MaterialTheme.typography.bodyMedium
            )
            val details = listOfNotNull(
                stats?.username?.takeUnless { stats.isCurrentUser || it.isBlank() },
                stats?.fetchedAtMillis?.let { "Updated ${formatRelativeTime(it)}" }
            )
            if (details.isNotEmpty()) {
                Text(
                    text = details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(FriendDetailsTestTags.UPDATED)
                )
            }
        }
    }
}

/** "Level 11 for 12 days", or just "Level 11" when the timeline doesn't say when it started. Time
 *  on the level lives here, next to the level it's about, rather than as a tile of its own. */
private fun levelLine(stats: FriendStats): String {
    val days = stats.daysOnCurrentLevel ?: return "Level ${stats.level}"
    return "Level ${stats.level} for $days ${if (days == 1) "day" else "days"}"
}

private fun LeaderboardWindow.phrase(): String = when (this) {
    LeaderboardWindow.WEEK -> "this week"
    LeaderboardWindow.MONTH -> "this month"
    LeaderboardWindow.YEAR -> "this year"
    LeaderboardWindow.ALL_TIME -> "all time"
}

/** Two columns of figures. Tiles for figures this person doesn't have yet are left out, and an odd
 *  one out at the end spans the row rather than leaving half of it empty. */
@Composable
private fun StatGrid(stats: FriendStats, window: LeaderboardWindow) {
    val tiles = buildList {
        add("Lessons today" to stats.learned.today.toString())
        add("Lessons ${window.phrase()}" to stats.learned.forWindow(window).toString())
        // Unless the window already is the month, which the tile above then says.
        if (window != LeaderboardWindow.MONTH) add("Lessons this month" to stats.learned.month.toString())
        add("Burned ${window.phrase()}" to stats.burned.forWindow(window).toString())
        stats.reviewAccuracy?.let { add("Review accuracy" to "${(it * PERCENT).roundToInt()}%") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(TileSpacing)) {
        tiles.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(TileSpacing)) {
                pair.forEach { (label, value) -> StatTile(label, value, Modifier.weight(1f)) }
            }
        }
    }
}

/** The sheet itself is surfaceContainerLow, so tiles step up to stand out from it. On e-ink, where
 *  those roles aren't grayscale, a border does that job instead. */
@Composable
private fun tileColor(): Color =
    if (LocalEinkTheme.current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerHighest

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(shape = RoundedCornerShape(12.dp), color = tileColor(), border = einkBorder(), modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Where their started items sit on WaniKani's SRS, in the stage colours the dashboard's item
 *  spread card uses. The labels are WaniKani-style abbreviations: five columns across the panel
 *  leave no room for "Enlightened" at larger font sizes. */
@Composable
private fun SrsRow(counts: SrsCounts) {
    val groups = listOf(
        Triple("Appr", counts.apprentice, SrsStage.APPRENTICE_1),
        Triple("Guru", counts.guru, SrsStage.GURU_1),
        Triple("Mast", counts.master, SrsStage.MASTER),
        Triple("Enl", counts.enlightened, SrsStage.ENLIGHTENED),
        Triple("Burn", counts.burned, SrsStage.BURNED)
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tileColor(),
        border = einkBorder(),
        modifier = Modifier.fillMaxWidth().testTag(FriendDetailsTestTags.SRS_ROW)
    ) {
        Row(modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
            groups.forEach { (label, count, stage) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(srsStageColor(stage))
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetActions(onRename: (() -> Unit)?, onRemove: (() -> Unit)?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        onRename?.let { rename ->
            OutlinedButton(onClick = rename, modifier = Modifier.testTag(FriendDetailsTestTags.RENAME_BUTTON)) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Rename")
            }
        }
        onRemove?.let { remove ->
            TextButton(
                onClick = remove,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag(FriendDetailsTestTags.REMOVE_BUTTON)
            ) {
                Icon(Icons.Default.PersonRemove, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text("Remove")
            }
        }
    }
}
