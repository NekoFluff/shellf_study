package com.crazyfluff.shellfstudy.shared.data.studytime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LifetimeEstimateTest {
    @Test
    fun pricesTwoQuestionReviewsAtThePaceAndOneQuestionReviewsAtHalf() {
        val estimate = LifetimeEstimate.from(
            twoQuestionReviews = 100,
            oneQuestionReviews = 40,
            lessons = 10,
            pace = StudyPace(reviewMsPerItem = 12_000L, lessonMsPerItem = 90_000L)
        )!!

        assertEquals(100 * 12_000L + 40 * 6_000L, estimate.reviewMs)
        assertEquals(10 * 90_000L, estimate.lessonMs)
        assertEquals(estimate.reviewMs + estimate.lessonMs, estimate.totalMs)
    }

    @Test
    fun fallsBackToTheDefaultPacesWithoutRecordedOnes() {
        val estimate = LifetimeEstimate.from(
            twoQuestionReviews = 3, oneQuestionReviews = 2, lessons = 1, pace = StudyPace()
        )!!

        assertEquals(StudyTimeAggregator.DEFAULT_REVIEW_MS_PER_ITEM, estimate.reviewMsPerItem)
        assertEquals(StudyTimeAggregator.DEFAULT_REVIEW_MS_PER_ITEM / 2, estimate.oneQuestionReviewMs)
        assertEquals(StudyTimeAggregator.DEFAULT_LESSON_MS_PER_ITEM, estimate.lessonMs)
    }

    @Test
    fun isAbsentBeforeThereIsAnyHistory() {
        assertNull(
            LifetimeEstimate.from(twoQuestionReviews = 0, oneQuestionReviews = 0, lessons = 0, pace = StudyPace())
        )
    }
}
