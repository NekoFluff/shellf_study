package com.crazyfluff.shellfstudy.core.designsystem.quiz

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

/** A running "m:ss" clock counting up from [startTimeMs] — used for both the total-session and
 *  per-question timers on the Review/Lesson quiz screens (see [ElapsedTimeText]'s call sites).
 *  Ticks locally in the Composable rather than through a ViewModel's StateFlow — purely
 *  presentational, so it doesn't need a per-second state emission. */
@Composable
fun ElapsedTimeText(
    startTimeMs: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    color: Color = LocalContentColor.current
) {
    var elapsedSeconds by remember(startTimeMs) { mutableStateOf((System.currentTimeMillis() - startTimeMs) / 1000) }
    LaunchedEffect(startTimeMs) {
        while (true) {
            elapsedSeconds = (System.currentTimeMillis() - startTimeMs) / 1000
            delay(1000)
        }
    }
    Text(
        text = "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60),
        style = style,
        color = color,
        modifier = modifier
    )
}
