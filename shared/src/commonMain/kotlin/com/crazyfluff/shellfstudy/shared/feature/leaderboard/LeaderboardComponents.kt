package com.crazyfluff.shellfstudy.shared.feature.leaderboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.designsystem.friends.FriendAvatar
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalEinkTheme
import com.crazyfluff.shellfstudy.shared.designsystem.theme.einkBorder

private val RowPadding = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
private val AvatarSize = 40.dp

/** One friend on the roster. Tapping opens their details, where they can be renamed or removed. */
@Composable
internal fun FriendRow(id: String, nickname: String, stats: FriendStats?, color: Color, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(LeaderboardScreenTestTags.friendRow(id))
            .then(RowPadding)
    ) {
        FriendAvatar(nickname, color)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(nickname, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stats?.let { "${it.username} · Level ${it.level}" }
                    ?: "Stats not loaded yet · pull down to retry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The list's own "Add a friend" row, so adding sits where the friends are and not only in the top bar. */
@Composable
internal fun AddFriendRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(LeaderboardScreenTestTags.ADD_FRIEND_ROW)
            .then(RowPadding)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(AvatarSize).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = "Add a friend",
            style = MaterialTheme.typography.bodyLarge,
            // E-ink's primary is a mid gray, too faint for the one action label in the list.
            color = if (LocalEinkTheme.current) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

/** A refresh or roster problem, shown as a banner at the top of the list instead of a stray red line. */
@Composable
internal fun ErrorBanner(message: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        border = einkBorder(),
        modifier = Modifier.fillMaxWidth().testTag(LeaderboardScreenTestTags.ERROR_BANNER)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = RowPadding) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
        }
    }
}
