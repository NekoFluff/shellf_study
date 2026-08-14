package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.core.data.model.DashboardSummary
import com.crazyfluff.shellfstudy.core.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.core.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.shared.network.ReviewResultData
import com.crazyfluff.shellfstudy.shared.network.ReviewSubmissionBody
import com.crazyfluff.shellfstudy.shared.network.ReviewSubmissionRequest
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import javax.inject.Inject
import javax.inject.Singleton

/** Account/session facade — user profile, dashboard summary counts, and review submission. */
@Singleton
class WaniKaniRepository @Inject constructor(
    private val api: WaniKaniApi
) {
    suspend fun fetchUser(): ApiResult<WaniKaniUser> = safeApiCall {
        val response = api.getUser()
        WaniKaniUser(
            username = response.data.username,
            level = response.data.level,
            profileUrl = response.data.profileUrl
        )
    }

    suspend fun fetchDashboardSummary(): ApiResult<DashboardSummary> = safeApiCall {
        val response = api.getSummary()
        DashboardSummary(
            lessonCount = response.data.availableLessonSubjectIds.size,
            reviewCount = response.data.availableReviewSubjectIds.size
        )
    }

    /** Network-only — no local DB side effects. Only called by [com.crazyfluff.shellfstudy.core.sync.OutboxSyncWorker];
     *  the UI path writes to the outbox instead (see [OutboxRepository]) and never calls this directly. */
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
