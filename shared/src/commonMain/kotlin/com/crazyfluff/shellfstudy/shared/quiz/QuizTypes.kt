package com.crazyfluff.shellfstudy.shared.quiz

enum class QuestionType { MEANING, READING }

val QuestionType.label: String get() = when (this) {
    QuestionType.MEANING -> "meaning"
    QuestionType.READING -> "reading"
}

data class AnswerFeedback(
    val isCorrect: Boolean,
    val correctAnswer: String,
    val wasCloseMatch: Boolean = false,
    val answerCount: Int = 1
)
