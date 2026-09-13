package com.crazyfluff.shellfstudy.shared.data.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Guards the fusion the stats card depends on: each question type's five figures are one value or
 * nothing, so the card can render from a single null check instead of five that must agree.
 *
 * The distinction being preserved is per-type, not per-subject: a subject whose meaning has been
 * reviewed but whose reading has not must still report "no reviews yet" for reading alone.
 */
class SubjectReviewStatsTest {

    @Test
    fun meaningStatsCarryTheAccuracyCountsAndBothStreaksTogether() {
        val stats = reviewStats(meaningCorrect = 9, meaningIncorrect = 1, meaningCurrentStreak = 3, meaningMaxStreak = 5)

        assertEquals(
            QuestionTypeStats(correct = 9, incorrect = 1, accuracyPercent = 90, currentStreak = 3, bestStreak = 5),
            stats.meaningStats
        )
    }

    @Test
    fun aQuestionTypeWithNoAttemptsHasNoStatsRatherThanAZeroPercentScore() {
        val stats = reviewStats(meaningCorrect = 0, meaningIncorrect = 0)

        assertNull(stats.meaningStats)
        assertNull(stats.readingStats)
    }

    @Test
    fun oneQuestionTypeCanHaveStatsWhileTheOtherDoesNot() {
        val stats = reviewStats(meaningCorrect = 4, meaningIncorrect = 0, readingCorrect = 0, readingIncorrect = 0)

        assertEquals(100, stats.meaningStats?.accuracyPercent)
        assertNull(stats.readingStats)
    }

    private fun reviewStats(
        meaningCorrect: Int = 0,
        meaningIncorrect: Int = 0,
        meaningCurrentStreak: Int = 0,
        meaningMaxStreak: Int = 0,
        readingCorrect: Int = 0,
        readingIncorrect: Int = 0,
        readingCurrentStreak: Int = 0,
        readingMaxStreak: Int = 0
    ) = SubjectReviewStats(
        meaningCorrect = meaningCorrect,
        meaningIncorrect = meaningIncorrect,
        meaningCurrentStreak = meaningCurrentStreak,
        meaningMaxStreak = meaningMaxStreak,
        readingCorrect = readingCorrect,
        readingIncorrect = readingIncorrect,
        readingCurrentStreak = readingCurrentStreak,
        readingMaxStreak = readingMaxStreak,
        lastReviewedAt = null
    )
}
