package com.crazyfluff.shellfstudy.shared.quiz

/**
 * One item's progress through a quiz session.
 *
 * Wrong attempts are counts, not flags: WaniKani's demotion rule is `ceil(incorrect / 2) * penalty`,
 * so the count handed to it (and to the rank-change prediction) changes the resulting stage, and an
 * undo reverts exactly the attempt it undoes rather than every memory of being wrong.
 */
data class QuizItemProgress<T>(
    val item: T,
    val meaningDone: Boolean = false,
    val readingDone: Boolean = false,
    val incorrectMeaningAttempts: Int = 0,
    val incorrectReadingAttempts: Int = 0
) {
    val hadIncorrectMeaning: Boolean get() = incorrectMeaningAttempts > 0
    val hadIncorrectReading: Boolean get() = incorrectReadingAttempts > 0
    val hasAnyProgress: Boolean get() = meaningDone || readingDone || hadIncorrectMeaning || hadIncorrectReading

    fun isDone(type: QuestionType): Boolean = when (type) {
        QuestionType.MEANING -> meaningDone
        QuestionType.READING -> readingDone
    }

    fun withDone(type: QuestionType, done: Boolean): QuizItemProgress<T> = when (type) {
        QuestionType.MEANING -> copy(meaningDone = done)
        QuestionType.READING -> copy(readingDone = done)
    }

    /** One more wrong attempt at [type] — or, with a negative [by], that many fewer, never below zero. */
    fun withIncorrectAttempt(type: QuestionType, by: Int = 1): QuizItemProgress<T> = when (type) {
        QuestionType.MEANING -> copy(incorrectMeaningAttempts = (incorrectMeaningAttempts + by).coerceAtLeast(0))
        QuestionType.READING -> copy(incorrectReadingAttempts = (incorrectReadingAttempts + by).coerceAtLeast(0))
    }
}
