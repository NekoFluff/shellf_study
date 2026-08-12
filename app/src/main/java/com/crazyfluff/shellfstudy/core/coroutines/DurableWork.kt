package com.crazyfluff.shellfstudy.core.coroutines

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs [block] on the receiving scope and awaits it — meant to be called on an [ApplicationScope]
 * scope, not a ViewModel's `viewModelScope`, for durability-critical writes (outbox enqueue,
 * session persistence) that must not be cancelled just because the user navigated away mid-write.
 * A ViewModel is cleared the instant the user navigates off its screen, and back-navigation is
 * never gated on grading, so a write parented to `viewModelScope` can be cancelled mid-flight.
 * Awaiting the join here (rather than fire-and-forget) still lets the caller's own suspend chain
 * observe completion; it's just no longer *cancellable* by leaving the screen.
 */
suspend fun CoroutineScope.runDurably(block: suspend CoroutineScope.() -> Unit) {
    launch(block = block).join()
}
