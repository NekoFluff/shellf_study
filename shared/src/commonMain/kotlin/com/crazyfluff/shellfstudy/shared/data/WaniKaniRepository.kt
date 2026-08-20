package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.DashboardSummary
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.shared.network.ReviewResultData
import com.crazyfluff.shellfstudy.shared.network.ReviewSubmissionBody
import com.crazyfluff.shellfstudy.shared.network.ReviewSubmissionRequest
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi

/** Account/session facade — user profile, dashboard summary counts, and review submission. */
class WaniKaniRepository(
    private val api: WaniKaniApi
) {
    suspend fun fetchUser(): ApiResult<WaniKaniUser> = safeApiCall {
        val response = api.getUser()
        WaniKaniUser(
            username = response.data.username,
            level = response.data.level
        )
    }

    suspend fun fetchDashboardSummary(): ApiResult<DashboardSummary> = safeApiCall {
        val response = api.getSummary()
        DashboardSummary(
            lessonCount = response.data.availableLessonSubjectIds.size,
            reviewCount = response.data.availableReviewSubjectIds.size
        )
    }

    /** Network-only — no local DB side effects. Only called by the outbox sync worker; the UI path
     *  writes to the outbox instead and never calls this directly. */
    suspend fun submitReview(assignmentId: Long, grade: ReviewGrade): ApiResult<ReviewResultData> = safeApiCall {
        api.submitReview(
            ReviewSubmissionRequest(
                ReviewSubmissionBody(
                    assignmentId = assignmentId,
                    incorrectMeaningAnswers = if (grade.meaningCorrect) 0 else 1,
                    incorrectReadingAnswers = if (grade.readingCorrect) 0 else 1
                )
            )
        ).data
    }
}
