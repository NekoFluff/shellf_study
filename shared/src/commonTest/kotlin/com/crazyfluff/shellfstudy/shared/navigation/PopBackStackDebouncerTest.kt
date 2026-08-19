package com.crazyfluff.shellfstudy.shared.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.TestTimeSource
import kotlin.time.Duration.Companion.milliseconds

class PopBackStackDebouncerTest {

    @Test
    fun firstPop_isNeverSuppressed() {
        val debouncer = PopBackStackDebouncer(timeSource = TestTimeSource())
        assertFalse(debouncer.shouldSuppress("Review"))
    }

    @Test
    fun rapidSecondTap_onSameStillVisibleButton_isSuppressed() {
        // Regression test: a real second click event dispatched against a button whose screen
        // hasn't recomposed away yet reads the *destination* the first tap already landed on
        // (currentBackStackEntry updates synchronously), not the screen the user still sees.
        val timeSource = TestTimeSource()
        val debouncer = PopBackStackDebouncer(timeSource = timeSource)

        assertFalse(debouncer.shouldSuppress("Review"))
        debouncer.recordPop("Dashboard")

        timeSource += 50.milliseconds
        assertTrue(debouncer.shouldSuppress("Dashboard"))
    }

    @Test
    fun unrelatedPop_fromADifferentScreen_isNotSuppressed_evenWithinTheWindow() {
        // The bug this replaced: a global time-only debounce swallowed a legitimate pop on one
        // screen shortly after an unrelated pop elsewhere. A genuinely different screen's own pop
        // reads its own current route, not the destination the previous pop landed on.
        val timeSource = TestTimeSource()
        val debouncer = PopBackStackDebouncer(timeSource = timeSource)

        assertFalse(debouncer.shouldSuppress("Settings"))
        debouncer.recordPop("Dashboard")

        timeSource += 50.milliseconds
        assertFalse(debouncer.shouldSuppress("Review"))
    }

    @Test
    fun samePop_afterTheDebounceWindowElapses_isNotSuppressed() {
        val timeSource = TestTimeSource()
        val debouncer = PopBackStackDebouncer(timeSource = timeSource, window = 500.milliseconds)

        debouncer.recordPop("Dashboard")
        timeSource += 600.milliseconds

        assertFalse(debouncer.shouldSuppress("Dashboard"))
    }

    @Test
    fun thirdTap_afterASuppressedSecondTap_isStillSuppressed() {
        // A suppressed call must not itself reset the debounce window/route.
        val timeSource = TestTimeSource()
        val debouncer = PopBackStackDebouncer(timeSource = timeSource)

        debouncer.recordPop("Dashboard")
        timeSource += 50.milliseconds
        assertTrue(debouncer.shouldSuppress("Dashboard"))

        timeSource += 50.milliseconds
        assertTrue(debouncer.shouldSuppress("Dashboard"))
    }
}
