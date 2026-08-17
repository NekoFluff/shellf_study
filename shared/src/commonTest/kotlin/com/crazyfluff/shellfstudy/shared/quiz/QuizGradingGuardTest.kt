package com.crazyfluff.shellfstudy.shared.quiz

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)

class QuizGradingGuardTest {

    @Test
    fun firstCall_executesBlockAndReturnsTrue() = runTest {
        val guard = QuizGradingGuard(this)
        var ran = false

        val launched = guard.launchIfIdle { ran = true }
        advanceUntilIdle()

        assertTrue(launched)
        assertTrue(ran)
    }

    @Test
    fun secondCallWhileFirstIsRunning_returnsFalseAndSkipsBlock() = runTest {
        val guard = QuizGradingGuard(this)
        val latch = CompletableDeferred<Unit>()
        var secondRan = false

        guard.launchIfIdle { latch.await() } // first call is paused on the latch
        val secondResult = guard.launchIfIdle { secondRan = true } // second call while first is busy

        assertFalse(secondResult, "expected launchIfIdle to return false when guard is busy")
        assertFalse(secondRan, "expected second block not to have run")

        latch.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun afterFirstCompletes_guardIsIdleAgain_andNextCallExecutes() = runTest {
        val guard = QuizGradingGuard(this)
        val latch = CompletableDeferred<Unit>()

        guard.launchIfIdle { latch.await() }
        latch.complete(Unit)
        advanceUntilIdle() // first block finishes, guard resets

        var thirdRan = false
        val thirdResult = guard.launchIfIdle { thirdRan = true }
        advanceUntilIdle()

        assertTrue(thirdResult, "expected launchIfIdle to return true after guard became idle again")
        assertTrue(thirdRan)
    }
}
