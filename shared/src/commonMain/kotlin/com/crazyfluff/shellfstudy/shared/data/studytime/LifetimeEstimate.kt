package com.crazyfluff.shellfstudy.shared.data.studytime

/**
 * Roughly how long the whole WaniKani history took, from the counts WaniKani keeps (finished reviews,
 * lessons started) priced at the learner's average over every recorded session, or the defaults
 * before anything is recorded. Not the recent 14-day pace: a whole history shouldn't swing with
 * one fast or slow fortnight, or forget everything after a break.
 *
 * A review item's pace covers both of its questions, since kanji and vocabulary ask meaning and
 * reading. Radicals and kana-only vocabulary ask one, so they're priced at half.
 */
data class LifetimeEstimate(
    val twoQuestionReviews: Long,
    val oneQuestionReviews: Long,
    val lessons: Int,
    val reviewMsPerItem: Long,
    val lessonMsPerItem: Long
) {
    val oneQuestionReviewMs: Long get() = reviewMsPerItem / 2

    val reviewMs: Long get() = twoQuestionReviews * reviewMsPerItem + oneQuestionReviews * oneQuestionReviewMs

    val lessonMs: Long get() = lessons * lessonMsPerItem

    val totalMs: Long get() = reviewMs + lessonMs

    companion object {
        /** Null before the account has any history to price. */
        fun from(twoQuestionReviews: Long, oneQuestionReviews: Long, lessons: Int, pace: StudyPace): LifetimeEstimate? {
            if (twoQuestionReviews + oneQuestionReviews == 0L && lessons == 0) return null
            return LifetimeEstimate(
                twoQuestionReviews = twoQuestionReviews,
                oneQuestionReviews = oneQuestionReviews,
                lessons = lessons,
                reviewMsPerItem = pace.allTimeReviewMsPerItem ?: StudyTimeAggregator.DEFAULT_REVIEW_MS_PER_ITEM,
                lessonMsPerItem = pace.allTimeLessonMsPerItem ?: StudyTimeAggregator.DEFAULT_LESSON_MS_PER_ITEM
            )
        }
    }
}
