package com.crazyfluff.shellfstudy.shared.data.model

import kotlin.time.Instant

/** Per-subject accuracy/streak breakdown — meaning and reading are tracked separately by
 *  WaniKani, so this deliberately doesn't collapse them into one overall percentage. */
data class SubjectReviewStats(
    val meaningCorrect: Int,
    val meaningIncorrect: Int,
    val meaningCurrentStreak: Int,
    val meaningMaxStreak: Int,
    val readingCorrect: Int,
    val readingIncorrect: Int,
    val readingCurrentStreak: Int,
    val readingMaxStreak: Int,
    val lastReviewedAt: Instant?
) {
    /** False until the first review is submitted — correct/incorrect are all zero at that point,
     *  which would otherwise misread as a 0% accuracy score rather than "no data yet". */
    val hasBeenReviewed: Boolean
        get() = meaningCorrect + meaningIncorrect + readingCorrect + readingIncorrect > 0

    val meaningAccuracyPercent: Int? get() = accuracyPercent(meaningCorrect, meaningIncorrect)
    val readingAccuracyPercent: Int? get() = accuracyPercent(readingCorrect, readingIncorrect)

    /** Meaning's figures as one value, or null when meaning has never been reviewed. The five are
     *  only ever meaningful together — they come from the same row — so the stats card takes this
     *  rather than five separately-nullable ints it would have to re-check as a group. */
    val meaningStats: QuestionTypeStats?
        get() = questionTypeStats(meaningCorrect, meaningIncorrect, meaningCurrentStreak, meaningMaxStreak)

    /** Reading's figures as one value, or null when reading has never been reviewed. */
    val readingStats: QuestionTypeStats?
        get() = questionTypeStats(readingCorrect, readingIncorrect, readingCurrentStreak, readingMaxStreak)
}

/** One question type's review record: accuracy, the counts behind it, and the two streaks. */
data class QuestionTypeStats(
    val correct: Int,
    val incorrect: Int,
    val accuracyPercent: Int,
    val currentStreak: Int,
    val bestStreak: Int
)

private fun accuracyPercent(correct: Int, incorrect: Int): Int? {
    val total = correct + incorrect
    return if (total == 0) null else (correct * 100) / total
}

/** Null when this question type has no attempts yet — a 0% score would misread as "always wrong"
 *  rather than "not asked", which is the same distinction [SubjectReviewStats.hasBeenReviewed] draws
 *  for the pair. */
private fun questionTypeStats(
    correct: Int,
    incorrect: Int,
    currentStreak: Int,
    maxStreak: Int
): QuestionTypeStats? {
    val accuracy = accuracyPercent(correct, incorrect) ?: return null
    return QuestionTypeStats(
        correct = correct,
        incorrect = incorrect,
        accuracyPercent = accuracy,
        currentStreak = currentStreak,
        bestStreak = maxStreak
    )
}
