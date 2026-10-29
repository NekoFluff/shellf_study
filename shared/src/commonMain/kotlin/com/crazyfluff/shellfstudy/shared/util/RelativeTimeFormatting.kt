package com.crazyfluff.shellfstudy.shared.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.time.Clock

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/** "3 minutes ago" / "2 hours ago" / "5 days ago" style relative timestamp, as of now. For text on
 *  screen, use [rememberRelativeTime], which keeps it current. */
fun formatRelativeTime(millis: Long?): String = formatRelativeTime(millis, Clock.System.now().toEpochMilliseconds())

/** [millis] relative to [nowMillis]. A timestamp in the future (a skewed clock) reads as "just now". */
fun formatRelativeTime(millis: Long?, nowMillis: Long): String {
    if (millis == null) return "an earlier time"
    val minutesAgo = (nowMillis - millis).coerceAtLeast(0) / MINUTE_MS
    return when {
        minutesAgo < 1 -> "just now"
        minutesAgo < 60 -> "$minutesAgo minute${if (minutesAgo == 1L) "" else "s"} ago"
        minutesAgo < 60 * 24 -> {
            val hoursAgo = minutesAgo / 60
            "$hoursAgo hour${if (hoursAgo == 1L) "" else "s"} ago"
        }
        else -> {
            val daysAgo = minutesAgo / (60 * 24)
            "$daysAgo day${if (daysAgo == 1L) "" else "s"} ago"
        }
    }
}

/**
 * How long until [formatRelativeTime] of [millis] next reads differently: the next minute mark in the
 * first hour, the next hour mark in the first day, the next day mark after that.
 */
fun msUntilRelativeTimeChanges(millis: Long, nowMillis: Long): Long {
    val elapsed = nowMillis - millis
    val unit = when {
        elapsed < HOUR_MS -> MINUTE_MS
        elapsed < DAY_MS -> HOUR_MS
        else -> DAY_MS
    }
    val nextChangeAt = (elapsed.coerceAtLeast(0) / unit + 1) * unit
    return (nextChangeAt - elapsed).coerceAtLeast(1)
}

/**
 * [formatRelativeTime] for text on screen, kept current: it re-reads the clock exactly when the text
 * would change, so "4 minutes ago" turns into "5 minutes ago" on time, and sleeps in between. It only
 * runs while the screen is at least started, so it never wakes up in the background, and a label that
 * leaves the composition (a dashboard card scrolled away) stops with it. Each change recomposes only
 * the caller reading the returned text.
 */
@Composable
fun rememberRelativeTime(millis: Long?, clock: Clock = Clock.System): String {
    var nowMillis by remember(millis) { mutableLongStateOf(clock.now().toEpochMilliseconds()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(millis, lifecycle, clock) {
        if (millis == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // Re-read on every (re)start too: coming back from the background, the text may be due.
                nowMillis = clock.now().toEpochMilliseconds()
                delay(msUntilRelativeTimeChanges(millis, nowMillis))
            }
        }
    }
    return formatRelativeTime(millis, nowMillis)
}
