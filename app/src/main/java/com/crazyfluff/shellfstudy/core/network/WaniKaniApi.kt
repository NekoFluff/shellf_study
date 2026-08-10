package com.crazyfluff.shellfstudy.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Retrofit interface for the WaniKani API v2 (https://docs.api.wanikani.com/20170710/). */
interface WaniKaniApi {

    @GET("user")
    suspend fun getUser(): WkSingleResponse<UserData>

    @GET("summary")
    suspend fun getSummary(): WkSingleResponse<SummaryData>

    @GET("assignments")
    suspend fun getAssignments(
        @Query("immediately_available_for_review") immediatelyAvailableForReview: Boolean? = null,
        @Query("immediately_available_for_lessons") immediatelyAvailableForLessons: Boolean? = null,
        @Query("started_after") startedAfter: String? = null,
        @Query("ids") ids: List<Long>? = null
    ): WkCollectionResponse<AssignmentData>

    @GET("subjects")
    suspend fun getSubjects(@Query("ids") ids: List<Long>): WkCollectionResponse<SubjectData>

    @POST("reviews")
    suspend fun submitReview(@Body request: ReviewSubmissionRequest): WkResourceItem<ReviewResultData>

    @PUT("assignments/{id}/start")
    suspend fun startAssignment(
        @Path("id") assignmentId: Long,
        @Body request: StartAssignmentRequest = StartAssignmentRequest()
    ): WkResourceItem<AssignmentData>
}
