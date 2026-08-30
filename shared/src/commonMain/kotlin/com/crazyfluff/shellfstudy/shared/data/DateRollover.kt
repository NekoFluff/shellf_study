package com.crazyfluff.shellfstudy.shared.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** How long until [zone]'s next local midnight after [now]. */
internal fun durationUntilNextMidnight(now: Instant, zone: TimeZone): Duration {
    val today = now.toLocalDateTime(zone).date
    val nextMidnight = today.plus(1, DateTimeUnit.DAY).atTime(0, 0).toInstant(zone)
    return (nextMidnight - now).coerceAtLeast(Duration.ZERO)
}

/**
 * Emits the current local date immediately, then again every time local midnight passes.
 *
 * Combine this with a Room-backed Flow that computes something relative to "today" — a plain
 * `.map { Clock.System.todayIn(zone) }` over a DB flow only recomputes when a write happens to land
 * in the observed table, so a "today" value derived that way can go stale for as long as the app
 * runs without an unrelated write (surfacing as a stuck badge/streak/stat until the next write or an
 * app restart). Combining with this ticker instead makes the day roll over on its own.
 */
internal fun dailyRolloverTicks(): Flow<LocalDate> = flow {
    while (true) {
        // Re-read on every iteration rather than accepting a zone parameter fixed at flow
        // construction time, so a device timezone change while the app stays running is picked
        // up on the very next tick instead of being stuck until the process restarts.
        val zone = TimeZone.currentSystemDefault()
        emit(Clock.System.todayIn(zone))
        delay(durationUntilNextMidnight(Clock.System.now(), zone))
    }
}
