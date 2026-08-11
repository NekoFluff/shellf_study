package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.core.data.model.DashboardSummary
import com.crazyfluff.shellfstudy.core.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.core.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.core.network.ReviewResultData
import com.crazyfluff.shellfstudy.core.network.ReviewSubmissionBody
import com.crazyfluff.shellfstudy.core.network.ReviewSubmissionRequest
import com.crazyfluff.shellfstudy.core.network.WaniKaniApi
import javax.inject.Inject
import javax.inject.Singleton

/** Account/session facade — user profile, dashboard summary counts, and review submission. */
@Singleton
class WaniKaniRepository @Inject constructor(
    private val api: WaniKaniApi,
    private val statsRepository: StatsRepository
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

    suspend fun submitReview(assignmentId: Long, grade: ReviewGrade): ApiResult<ReviewResultData> = safeApiCall {
        val result = api.submitReview(
            ReviewSubmissionRequest(
                ReviewSubmissionBody(
                    assignmentId = assignmentId,
                    incorrectMeaningAnswers = if (grade.meaningCorrect) 0 else 1,
                    incorrectReadingAnswers = if (grade.readingCorrect) 0 else 1
                )
            )
        ).data
        statsRepository.logReviewEvent(result)
        result
    }
}
