package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.DashboardSummary
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.shared.network.MAX_WANIKANI_LEVEL
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
            id = response.data.id,
            username = response.data.username,
            level = response.data.level,
            maxLevelGranted = response.data.subscription?.maxLevelGranted ?: MAX_WANIKANI_LEVEL
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
     *  writes to the outbox instead and never calls this directly. Posts the grade's real wrong-answer
     *  counts, which WaniKani's demotion rule (`ceil(incorrect / 2) * penalty`) depends on — sending a
     *  collapsed 0/1 would under-report every item missed more than once.
     *
     *  [createdAt] is when the review was actually done; null lets WaniKani use the request time. */
    suspend fun submitReview(
        assignmentId: Long,
        grade: ReviewGrade,
        createdAt: String? = null
    ): ApiResult<ReviewResultData> = safeApiCall {
        api.submitReview(
            ReviewSubmissionRequest(
                ReviewSubmissionBody(
                    assignmentId = assignmentId,
                    incorrectMeaningAnswers = grade.incorrectMeaning,
                    incorrectReadingAnswers = grade.incorrectReading,
                    createdAt = createdAt
                )
            )
        ).data
    }
}
