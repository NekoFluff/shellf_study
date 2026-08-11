package com.crazyfluff.shellfstudy.core.notifications

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import org.junit.Test

class NotificationPoliciesTest {

    @Test
    fun `WatermarkPolicy notifies when count rises past last-notified watermark`() {
        val decision = WatermarkPolicy.decide(currentCount = 8, lastNotifiedCount = 5)
        assertThat(decision).isEqualTo(WatermarkDecision.Notify(delta = 3, newWatermark = 8))
    }

    @Test
    fun `WatermarkPolicy resets watermark when count drops`() {
        val decision = WatermarkPolicy.decide(currentCount = 2, lastNotifiedCount = 8)
        assertThat(decision).isEqualTo(WatermarkDecision.ResetWatermark(newWatermark = 2))
    }

    @Test
    fun `WatermarkPolicy is a no-op when count is unchanged`() {
        val decision = WatermarkPolicy.decide(currentCount = 5, lastNotifiedCount = 5)
        assertThat(decision).isEqualTo(WatermarkDecision.NoChange)
    }

    @Test
    fun `BacklogPolicy does not notify below threshold`() {
        val result = BacklogPolicy.shouldNotify(
            currentCount = 10,
            threshold = 50,
            lastNotifiedAt = null,
            now = Instant.parse("2026-08-10T12:00:00Z"),
            cooldown = Duration.ofHours(6)
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `BacklogPolicy notifies first time above threshold with no prior notification`() {
        val result = BacklogPolicy.shouldNotify(
            currentCount = 60,
            threshold = 50,
            lastNotifiedAt = null,
            now = Instant.parse("2026-08-10T12:00:00Z"),
            cooldown = Duration.ofHours(6)
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `BacklogPolicy withholds a repeat notification inside the cooldown window`() {
        val now = Instant.parse("2026-08-10T12:00:00Z")
        val result = BacklogPolicy.shouldNotify(
            currentCount = 60,
            threshold = 50,
            lastNotifiedAt = now.minus(Duration.ofHours(2)),
            now = now,
            cooldown = Duration.ofHours(6)
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `BacklogPolicy notifies again once the cooldown has elapsed`() {
        val now = Instant.parse("2026-08-10T12:00:00Z")
        val result = BacklogPolicy.shouldNotify(
            currentCount = 60,
            threshold = 50,
            lastNotifiedAt = now.minus(Duration.ofHours(7)),
            now = now,
            cooldown = Duration.ofHours(6)
        )
        assertThat(result).isTrue()
    }
}
