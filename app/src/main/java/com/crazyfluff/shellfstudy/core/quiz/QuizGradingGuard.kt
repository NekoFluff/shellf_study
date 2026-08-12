package com.crazyfluff.shellfstudy.core.quiz

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Rejects a second rapid submission before the first's suspend work (grading, optimistic SRS
 * write, outbox enqueue) has had a chance to land feedback in state — otherwise both submissions
 * race the caller's grading logic, double the queue mutation, and the loser's stale rank change
 * flashes on screen before the winner's. [isGrading] is a plain field, not state-flow-backed, so
 * the check itself can never race the flag it's guarding.
 */
class QuizGradingGuard(private val scope: CoroutineScope) {
    private var isGrading = false

    /** Launches [block] on [scope] unless a previous launch is still in flight; returns whether it
     *  actually launched. */
    fun launchIfIdle(block: suspend () -> Unit): Boolean {
        if (isGrading) return false
        isGrading = true
        scope.launch {
            try {
                block()
            } finally {
                isGrading = false
            }
        }
        return true
    }
}
