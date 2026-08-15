package com.crazyfluff.shellfstudy.core.notifications

import com.crazyfluff.shellfstudy.shared.notifications.BacklogPolicy
import com.crazyfluff.shellfstudy.shared.notifications.WatermarkDecision
import com.crazyfluff.shellfstudy.shared.notifications.WatermarkPolicy
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

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
            cooldown = 6.hours
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
            cooldown = 6.hours
        )
        assertThat(result).isTrue()
    }

    @Test
    fun `BacklogPolicy withholds a repeat notification inside the cooldown window`() {
        val now = Instant.parse("2026-08-10T12:00:00Z")
        val result = BacklogPolicy.shouldNotify(
            currentCount = 60,
            threshold = 50,
            lastNotifiedAt = now.minus(2.hours),
            now = now,
            cooldown = 6.hours
        )
        assertThat(result).isFalse()
    }

    @Test
    fun `BacklogPolicy notifies again once the cooldown has elapsed`() {
        val now = Instant.parse("2026-08-10T12:00:00Z")
        val result = BacklogPolicy.shouldNotify(
            currentCount = 60,
            threshold = 50,
            lastNotifiedAt = now.minus(7.hours),
            now = now,
            cooldown = 6.hours
        )
        assertThat(result).isTrue()
    }
}
