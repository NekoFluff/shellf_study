package com.crazyfluff.shellfstudy.shared.feature.review

import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.quiz.QuizItemProgress
import com.crazyfluff.shellfstudy.shared.quiz.QuizSession

/**
 * A review session in progress: its questions, and the one grade that is correct but not yet
 * committed.
 *
 * [pendingSubmissionAssignmentId] is the item whose last question was just answered correctly while
 * its feedback is still on screen. Submitting it to WaniKani waits for Continue, so an undo can still
 * retract it; at most one exists, because nothing can be graded while feedback is showing.
 */
internal data class ReviewSession(
    val quiz: QuizSession<ReviewItem> = QuizSession(),
    val pendingSubmissionAssignmentId: Long? = null
) {
    fun toPersisted(sessionActiveElapsedMs: Long): PersistedReviewSession = PersistedReviewSession(
        queue = quiz.persistedInFlight(),
        reserve = quiz.persistedReserve(),
        progress = quiz.persistedProgress(),
        totalQuestions = quiz.totalQuestions,
        sessionActiveElapsedMs = sessionActiveElapsedMs,
        answeredQuestions = quiz.persistedAnswers(),
        pendingSubmissionAssignmentId = pendingSubmissionAssignmentId
    )
}

/** The grade WaniKani is told about, and that the rank-change prediction is computed from. Carries the
 *  real wrong-answer counts rather than deriving them from the booleans: WaniKani's demotion rule is
 *  `ceil(incorrect / 2) * penalty`, so the count decides how far a missed item falls. */
internal fun QuizItemProgress<ReviewItem>.toReviewGrade(): ReviewGrade =
    ReviewGrade(
        meaningCorrect = !hadIncorrectMeaning,
        readingCorrect = !hadIncorrectReading,
        incorrectMeaning = incorrectMeaningAttempts,
        incorrectReading = incorrectReadingAttempts
    )
