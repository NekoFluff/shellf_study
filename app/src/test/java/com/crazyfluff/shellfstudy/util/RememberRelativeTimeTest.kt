package com.crazyfluff.shellfstudy.util

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.util.rememberRelativeTime
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Instant

private const val TAG = "relative_time"
private const val MINUTE = 60_000L
private const val SYNCED_AT = 1_000_000_000_000L

@RunWith(AndroidJUnit4::class)
class RememberRelativeTimeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Wall-clock time the test moves by hand, counting how often it's read. */
    private class FakeClock(var nowMillis: Long) : Clock {
        var reads = 0
        override fun now(): Instant {
            reads++
            return Instant.fromEpochMilliseconds(nowMillis)
        }
    }

    private class TestLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    /** Moves the wall clock and the composition's own clock (which drives the helper's waits) together. */
    private fun advance(clock: FakeClock, ms: Long) {
        clock.nowMillis += ms
        composeTestRule.mainClock.advanceTimeBy(ms)
    }

    private fun label() = composeTestRule.onNodeWithTag(TAG)

    @Test
    fun theTextChangesWhenItShould_andTheClockIsLeftAloneInBetween() {
        val clock = FakeClock(SYNCED_AT + 4 * MINUTE + 30_000)
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Text(rememberRelativeTime(SYNCED_AT, clock), Modifier.testTag(TAG))
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        label().assertTextEquals("4 minutes ago")
        val readsWhileWaiting = clock.reads

        advance(clock, 29_000)
        label().assertTextEquals("4 minutes ago")
        assertThat(clock.reads).isEqualTo(readsWhileWaiting)

        advance(clock, 1_000)
        label().assertTextEquals("5 minutes ago")
    }

    @Test
    fun nothingWakesUpInTheBackground_andTheTextCatchesUpOnReturn() {
        val clock = FakeClock(SYNCED_AT)
        val owner = TestLifecycleOwner()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                Text(rememberRelativeTime(SYNCED_AT, clock), Modifier.testTag(TAG))
            }
        }
        composeTestRule.mainClock.advanceTimeByFrame()
        label().assertTextEquals("just now")

        composeTestRule.runOnIdle { owner.registry.currentState = Lifecycle.State.CREATED }
        val readsWhenBackgrounded = clock.reads
        advance(clock, 30 * MINUTE)
        assertThat(clock.reads).isEqualTo(readsWhenBackgrounded)
        label().assertTextEquals("just now")

        composeTestRule.runOnIdle { owner.registry.currentState = Lifecycle.State.RESUMED }
        composeTestRule.mainClock.advanceTimeByFrame()
        label().assertTextEquals("30 minutes ago")
    }
}
