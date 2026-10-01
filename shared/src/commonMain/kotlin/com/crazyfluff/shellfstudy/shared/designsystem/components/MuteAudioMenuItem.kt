package com.crazyfluff.shellfstudy.shared.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * The "Mute audio" / "Unmute audio" entry in the dashboard's, lesson screen's and review screen's
 * overflow menus. Label and icon name the action a tap takes, not the current state.
 */
@Composable
fun MuteAudioMenuItem(muted: Boolean, testTag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(if (muted) "Unmute audio" else "Mute audio") },
        leadingIcon = {
            Icon(
                if (muted) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = null
            )
        },
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    )
}
