package com.crazyfluff.shellfstudy.core.notifications

import java.time.Duration
import java.time.Instant

/** Decision produced by [WatermarkPolicy.decide] for count-based notifications (reviews, lessons). */
sealed interface WatermarkDecision {
    /** [currentCount] rose past [lastNotifiedCount] — a genuinely new, unnotified batch exists. */
    data class Notify(val delta: Int, val newWatermark: Int) : WatermarkDecision

    /** [currentCount] dropped below [lastNotifiedCount] (the user did the work) — measure fresh next time. */
    data class ResetWatermark(val newWatermark: Int) : WatermarkDecision

    data object NoChange : WatermarkDecision
}

/**
 * Debounces "count of pending items increased" notifications so the same unaddressed batch
 * doesn't re-notify on every sync — only a genuine increase past what was last notified fires.
 */
object WatermarkPolicy {
    fun decide(currentCount: Int, lastNotifiedCount: Int): WatermarkDecision = when {
        currentCount > lastNotifiedCount ->
            WatermarkDecision.Notify(delta = currentCount - lastNotifiedCount, newWatermark = currentCount)
        currentCount < lastNotifiedCount -> WatermarkDecision.ResetWatermark(newWatermark = currentCount)
        else -> WatermarkDecision.NoChange
    }
}

/**
 * Cooldown-gated backlog warning: "still above threshold" has no natural watermark the way an
 * increasing count does, so this instead rate-limits how often the warning can repeat.
 */
object BacklogPolicy {
    fun shouldNotify(
        currentCount: Int,
        threshold: Int,
        lastNotifiedAt: Instant?,
        now: Instant,
        cooldown: Duration
    ): Boolean {
        if (currentCount < threshold) return false
        if (lastNotifiedAt == null) return true
        return Duration.between(lastNotifiedAt, now) >= cooldown
    }
}
