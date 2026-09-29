package com.crazyfluff.shellfstudy.core.performance

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.designsystem.performance.LocalCompositionClock
import com.crazyfluff.shellfstudy.shared.designsystem.performance.LocalCompositionRecorder
import com.crazyfluff.shellfstudy.shared.designsystem.performance.LocalCompositionTimingEnabled
import com.crazyfluff.shellfstudy.shared.designsystem.performance.TimedComposition
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the per-card composition timing behind the jank harness's COMPOSITION line.
 *
 * The measurement is driven by [LocalCompositionClock] rather than a real clock, so these assert exact
 * figures instead of bounds around wall-clock noise. That matters here more than usual: the first
 * version of this instrumentation read the clock in a `Modifier.composed` factory and reported from a
 * `SideEffect`, which fires after the *whole* composition commits — so each card's figure was "time
 * from this card until the end of the pass", every card absorbing everything composed after it. Three
 * cards that each burned 20 ms reported 61 ms, 41 ms and 21 ms, and the resulting ranking was just
 * tree order. A deterministic clock makes that failure mode a failing assertion rather than a
 * plausible-looking number.
 *
 * The skipping test is the other half: a `Modifier.composed` element has no `equals`, so a modifier
 * chain containing one is never equal to the previous composition's chain. Every card carrying one
 * recomposed whenever its parent did — the instrumentation inflating what it measured.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CompositionTimingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val recordings = mutableListOf<Pair<String, Long>>()

    /** A clock the test advances by hand, so a "duration" is exactly what the test spent. */
    private class FakeClock(var nowNanos: Long = 0) {
        val read: () -> Long = { nowNanos }
    }

    @Test
    fun `each timed composable reports its own composition and not the work that follows it`() {
        val clock = FakeClock()

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalCompositionTimingEnabled provides true,
                LocalCompositionClock provides clock.read,
                LocalCompositionRecorder provides { name, nanos -> recordings += name to nanos }
            ) {
                Column {
                    TimedComposition("first") {
                        clock.nowNanos += 20_000_000
                        Text("first")
                    }
                    TimedComposition("second") {
                        clock.nowNanos += 5_000_000
                        Text("second")
                    }
                }
            }
        }

        composeTestRule.waitForIdle()

        assertThat(recordings).containsExactly(
            "first" to 20_000_000L,
            "second" to 5_000_000L
        ).inOrder()
    }

    @Test
    fun `records nothing while timing is disabled`() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalCompositionRecorder provides { name, nanos -> recordings += name to nanos }
            ) {
                Column {
                    TimedComposition("card") { Text("card") }
                }
            }
        }

        composeTestRule.waitForIdle()

        assertThat(recordings).isEmpty()
    }

    /**
     * The default clock has to be `commonMain`-safe: this file used `System.nanoTime()`, which does not
     * exist on Kotlin/Native, and the JVM suite could not see it — the iOS and metadata compiles could.
     * Composing with no clock provided exercises the real [kotlin.time.TimeSource.Monotonic] default.
     */
    @Test
    fun `the default clock reports a positive duration`() {
        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalCompositionTimingEnabled provides true,
                LocalCompositionRecorder provides { name, nanos -> recordings += name to nanos }
            ) {
                Column {
                    TimedComposition("card") { Text("card") }
                }
            }
        }

        composeTestRule.waitForIdle()

        assertThat(recordings.map { it.first }).containsExactly("card")
        assertThat(recordings.single().second).isGreaterThan(0L)
    }

    /**
     * Timing must be invisible to skipping, whether or not it is switched on. The old modifier could
     * not be: `Modifier.composed`'s unkeyed element has no `equals`, so the chain it produced differed
     * on every parent recomposition and the card recomposed with it — measured as `composed=1->2`
     * against `plain=1->1` for an unaffected card.
     */
    @Test
    fun `a timed composable is still skipped when the parent recomposes and its own inputs do not change`() {
        var tick by mutableIntStateOf(0)
        val counters = Counters()

        composeTestRule.setContent {
            Column {
                val pass = tick
                Text("pass $pass")
                PlainCard(modifier = Modifier.fillMaxWidth(), counter = counters.plain)
                TimedComposition("card") {
                    PlainCard(modifier = Modifier.fillMaxWidth(), counter = counters.timed)
                }
            }
        }

        composeTestRule.waitForIdle()
        val plainBefore = counters.plain.compositions
        val timedBefore = counters.timed.compositions

        tick++
        composeTestRule.waitForIdle()

        assertThat(counters.plain.compositions).isEqualTo(plainBefore)
        assertThat(counters.timed.compositions).isEqualTo(timedBefore)
    }
}

/** A stable holder, so the modifier is the only parameter that could make [PlainCard] unskippable. */
@Stable
private class Counters {
    val plain = Counter()
    val timed = Counter()
}

@Stable
private class Counter {
    var compositions = 0
}

@Composable
private fun PlainCard(modifier: Modifier, counter: Counter) {
    counter.compositions++
    Box(modifier)
}
