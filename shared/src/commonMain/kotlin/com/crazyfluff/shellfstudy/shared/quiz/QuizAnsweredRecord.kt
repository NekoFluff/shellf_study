package com.crazyfluff.shellfstudy.shared.quiz

/** A single graded answer, kept in-memory for the duration of a session — used to build the
 *  "slowest answers" summary. Shared shape for both Review and Lesson sessions. */
data class AnsweredQuestionRecord<T>(val item: T, val type: QuestionType, val isCorrect: Boolean, val elapsedMs: Long)

/** One of the slowest answers in a completed session's summary — same fields as
 *  [AnsweredQuestionRecord], just field-ordered to match the summary card's display order. */
data class SlowAnswer<T>(val item: T, val type: QuestionType, val elapsedMs: Long, val isCorrect: Boolean)
