package com.crazyfluff.shellfstudy.shared.coroutines

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SerialDurableWorkTest {

    @Test
    fun slowFirstCallStillCompletesBeforeFastSecondCall() = runTest {
        val queue = SerialDurableWork(this)
        val recorded = mutableListOf<String>()

        // Mirrors the real bug: a slow first write (e.g. grading's session-save, which does extra
        // outbox/stats work first) is issued, then a fast second write (e.g. completion's clear) is
        // issued immediately after, without waiting for the first to finish. On a bare
        // applicationScope.launch (no chaining), the fast write can complete first.
        launch { queue.run { delay(100); recorded.add("A") } }
        launch { queue.run { recorded.add("B") } }

        advanceUntilIdle()

        assertEquals(listOf("A", "B"), recorded)
    }

    @Test
    fun runSuspendsCallerUntilBlockCompletes() = runTest {
        val queue = SerialDurableWork(this)
        var ran = false

        queue.run {
            delay(50)
            ran = true
        }

        assertEquals(true, ran)
    }

    @Test
    fun manySequentialCallsPreserveOrder() = runTest {
        val queue = SerialDurableWork(this)
        val recorded = mutableListOf<Int>()

        // Later calls have progressively shorter delays, so without chaining they'd finish in
        // reverse order — this pins down that the chain isn't just "happens to work for two".
        (1..5).forEach { i ->
            launch { queue.run { delay((5 - i) * 20L); recorded.add(i) } }
        }

        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3, 4, 5), recorded)
    }
}
