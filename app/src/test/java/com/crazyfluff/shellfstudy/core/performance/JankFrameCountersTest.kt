package com.crazyfluff.shellfstudy.core.performance

import com.crazyfluff.shellfstudy.shared.designsystem.performance.JankStatsTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Covers the frame accounting behind the jank harness's SUMMARY line.
 *
 * The frame timing itself comes from JankStats and cannot be exercised off-device, but the arithmetic
 * on top of it can — and that arithmetic is the part a reader has to trust when comparing two builds.
 * A percentile that silently reports the wrong frame, or a percentage that divides by zero on the
 * first window, would send someone optimising the wrong thing.
 *
 * `FrameCounters` is internal, so this reaches it through the tracker's package.
 */
class JankFrameCountersTest {

    private fun counters(window: Int = 10) = JankStatsTracker.FrameCounters(window)

    @Test
    fun `reports a summary only once the window is full`() {
        val counters = counters(window = 3)

        assertThat(counters.record(1_000_000, "dashboard")).isFalse()
        assertThat(counters.record(1_000_000, "dashboard")).isFalse()
        assertThat(counters.record(1_000_000, "dashboard")).isTrue()
    }

    /**
     * Percentiles use a floor-index (nearest-rank) convention on the sorted durations: p50 of ten
     * values is the sixth, not the average of the fifth and sixth. That is deliberate for this
     * purpose — it reports a duration that a real frame actually took, rather than an interpolated
     * value no frame ever had. The trade is a slight underestimate at the high percentiles, which is
     * the safe direction for a harness whose job is to decide whether work is still needed.
     */
    @Test
    fun `percentiles are taken from the sorted durations`() {
        val counters = counters(window = 10)
        (1..10).forEach { counters.record(it * 1_000_000L, "dashboard") }

        val summary = counters.summaryAndReset()

        assertThat(summary).contains("frames=10")
        assertThat(summary).contains("p50=5.0ms")
        assertThat(summary).contains("p90=9.0ms")
        assertThat(summary).contains("p99=9.0ms")
        assertThat(summary).contains("max=10.0ms")
    }

    /**
     * The two budgets the harness reports against: 8.3 ms (120 Hz) and 16.7 ms (60 Hz). A frame
     * exactly on a boundary is counted as over it, so the numbers are an upper bound on smoothness
     * rather than a flattering lower one.
     */
    @Test
    fun `counts frames over each refresh-rate budget`() {
        val counters = counters(window = 4)
        counters.record(5_000_000, "dashboard")    // under both
        counters.record(10_000_000, "dashboard")   // over 120 Hz only
        counters.record(20_000_000, "dashboard")   // over both
        counters.record(100_000_000, "dashboard")  // over both

        val summary = counters.summaryAndReset()

        assertThat(summary).contains("over8ms=75%")   // 3 of 4
        assertThat(summary).contains("over17ms=50%")  // 2 of 4
    }

    @Test
    fun `attributes stalled frames and frame counts to their screen`() {
        val counters = counters(window = 10)
        counters.record(5_000_000, "dashboard")
        counters.record(50_000_000, "dashboard")
        counters.record(5_000_000, "review")
        counters.record(50_000_000, "review")

        val summary = counters.summaryAndReset()

        assertThat(summary).contains("stalled={dashboard=1, review=1}")
        assertThat(summary).contains("byScreen={dashboard=2, review=2}")
    }

    /** A window with no frames must not divide by zero or invent a percentile. */
    @Test
    fun `an empty window summarises without dividing by zero`() {
        val summary = counters(window = 10).summaryAndReset()

        assertThat(summary).isEqualTo("SUMMARY frames=0")
    }

    /** The counters reset, so a second window is independent of the first. */
    @Test
    fun `resets between windows`() {
        val counters = counters(window = 2)
        counters.record(50_000_000, "dashboard")
        counters.record(50_000_000, "dashboard")
        counters.summaryAndReset()

        counters.record(5_000_000, "review")
        val second = counters.summaryAndReset()

        assertThat(second).contains("frames=1")
        assertThat(second).contains("over8ms=0%")
        assertThat(second).doesNotContain("dashboard")
    }

    /**
     * Recording past the window's capacity must not overrun the buffer — the window is a preallocated
     * array, so this is the one place an off-by-one would corrupt rather than merely misreport.
     */
    @Test
    fun `does not overrun when more frames arrive than the window holds`() {
        val counters = counters(window = 3)
        repeat(10) { counters.record(1_000_000, "dashboard") }

        val summary = counters.summaryAndReset()

        // Every frame is counted even though only the window's worth of durations were retained.
        assertThat(summary).contains("frames=10")
        assertThat(summary).contains("max=1.0ms")
    }
}
