package com.crazyfluff.shellfstudy.shared.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

suspend fun CoroutineScope.runDurably(block: suspend CoroutineScope.() -> Unit) {
    launch(block = block).join()
}

/**
 * Like [runDurably], but chains each call onto the Job from the previous call, so writes to the
 * same durable store preserve launch order even on a multi-threaded scope — otherwise a slower
 * earlier write (e.g. grading's session-save, which does extra outbox/stats work first) can land
 * after a faster later one (e.g. completion's session-clear) and resurrect state the later write
 * meant to erase. Chaining on the previous Job (rather than a lock) forces the second write to
 * wait for the first write's entire execution, including its own internal suspensions — not just
 * for a lock-acquisition race that could still be won by the wrong side.
 */
class SerialDurableWork(private val scope: CoroutineScope) {
    private var previous: Job? = null

    suspend fun run(block: suspend CoroutineScope.() -> Unit) {
        val prior = previous
        val job = scope.launch {
            prior?.join()
            block()
        }
        previous = job
        job.join()
    }
}
