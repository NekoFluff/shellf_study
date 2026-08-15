package com.crazyfluff.shellfstudy.shared.designsystem.quiz

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
    var elapsedMs by remember(startTimeMs) { mutableStateOf(System.currentTimeMillis() - startTimeMs) }
    LaunchedEffect(startTimeMs) {
        while (true) {
            elapsedMs = System.currentTimeMillis() - startTimeMs
            delay(1000)
        }
    }
    Text(
        text = formatElapsedClock(elapsedMs),
        style = style,
        color = color,
        modifier = modifier
    )
}

/** Same "m:ss" clock format [ElapsedTimeText] ticks with — shared so a frozen elapsed time (e.g.
 *  the per-question timer once an answer's been submitted) reads identically to the live version
 *  it replaces, instead of jarringly changing format the instant it stops ticking. */
fun formatElapsedClock(elapsedMs: Long): String {
    val elapsedSeconds = elapsedMs / 1000
    return "%d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60)
}

/** Like [ElapsedTimeText], but for a clock that can pause — the total session timer, which should
 *  exclude time spent away from the session (app backgrounded, or navigated off-screen) rather than
 *  count straight through it. Ticks up from [baseElapsedMs] while [segmentStartMs] is non-null (the
 *  session is actively being viewed), and freezes at [baseElapsedMs] once it goes null. See
 *  ReviewViewModel/LessonViewModel's activeElapsedMs/activeSegmentStartMs, which this mirrors
 *  exactly, and AppForegroundTracker, which drives the pause/resume transitions. */
@Composable
fun PausableElapsedTimeText(
    baseElapsedMs: Long,
    segmentStartMs: Long?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    color: Color = LocalContentColor.current
) {
    var elapsedMs by remember(baseElapsedMs, segmentStartMs) {
        mutableStateOf(baseElapsedMs + (segmentStartMs?.let { System.currentTimeMillis() - it } ?: 0L))
    }
    LaunchedEffect(baseElapsedMs, segmentStartMs) {
        if (segmentStartMs == null) return@LaunchedEffect
        while (true) {
            elapsedMs = baseElapsedMs + (System.currentTimeMillis() - segmentStartMs)
            delay(1000)
        }
    }
    Text(
        text = formatElapsedClock(elapsedMs),
        style = style,
        color = color,
        modifier = modifier
    )
}
