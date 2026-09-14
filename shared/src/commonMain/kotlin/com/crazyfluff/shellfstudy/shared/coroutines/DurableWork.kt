package com.crazyfluff.shellfstudy.shared.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

suspend fun CoroutineScope.runDurably(block: suspend CoroutineScope.() -> Unit) {
    launch(block = block).join()
}

/**
 * Like [runDurably], but chains each call onto the Job from the previous call, so writes to the
 * same durable store preserve launch order even on a multi-threaded scope — otherwise a slower
 * earlier write (e.g. grading's session-save, which does extra outbox/stats work first) can land
 * after a faster later one (e.g. completion's session-clear) and resurrect state the later write
 * meant to erase. Chaining on the previous Job (rather than a lock on the whole call) forces the
 * second write to wait for the first write's entire execution, including its own internal
 * suspensions — not just for a lock-acquisition race that could still be won by the wrong side.
 *
 * Registering into the chain (reading [previous] and replacing it with the newly launched Job) is
 * itself guarded by [registerMutex] — every current call site happens to invoke [run] from a
 * single-threaded scope, where a plain var read-then-write can't interleave, but two genuinely
 * concurrent callers on a multi-threaded scope could otherwise both read the same `prior` before
 * either writes `previous`, silently breaking the chain the class exists to build. The mutex only
 * protects that bookkeeping — the caller still suspends on its own `job.join()` outside the lock,
 * so registering a later call doesn't wait for an earlier one's `block()` to finish.
 */
class SerialDurableWork(private val scope: CoroutineScope) {
    private val registerMutex = Mutex()
    private var previous: Job? = null

    suspend fun run(block: suspend CoroutineScope.() -> Unit) {
        val job = registerMutex.withLock {
            val prior = previous
            scope.launch {
                prior?.join()
                block()
            }.also { previous = it }
        }
        job.join()
    }
}
