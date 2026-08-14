package com.crazyfluff.shellfstudy.core.quiz

/** Shared between the lesson quiz and the review quiz — both ask a subject's meaning and/or reading. */
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
