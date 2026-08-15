package com.crazyfluff.shellfstudy.shared.quiz

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class QuizGradingGuard(private val scope: CoroutineScope) {
    private var isGrading = false

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
