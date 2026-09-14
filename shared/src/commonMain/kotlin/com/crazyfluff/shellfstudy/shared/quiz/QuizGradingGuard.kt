package com.crazyfluff.shellfstudy.shared.quiz

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Blocks a second concurrent grading submission while one is already in flight. Backed by a
 * [Mutex] (via non-suspending [Mutex.tryLock]/[Mutex.unlock]) rather than a plain boolean flag —
 * every current call site happens to invoke [launchIfIdle] from a single-threaded, Main-confined
 * scope, but a plain var's check-then-set is only atomic under that convention; a future caller
 * from a genuinely concurrent scope would silently reopen the double-submit race this class exists
 * to prevent. The Mutex makes the guarantee hold regardless of caller threading.
 */
class QuizGradingGuard(private val scope: CoroutineScope) {
    private val mutex = Mutex()

    fun launchIfIdle(block: suspend () -> Unit): Boolean {
        if (!mutex.tryLock()) return false
        scope.launch {
            try {
                block()
            } finally {
                mutex.unlock()
            }
        }
        return true
    }
}
