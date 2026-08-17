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
}

private fun accuracyPercent(correct: Int, incorrect: Int): Int? {
    val total = correct + incorrect
    return if (total == 0) null else (correct * 100) / total
}
