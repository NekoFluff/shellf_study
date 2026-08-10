package com.crazyfluff.shellfstudy.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

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
        @Query("updated_after") updatedAfter: String? = null,
        @Query("ids") ids: List<Long>? = null,
        @Query("levels") levels: List<Int>? = null,
        @Query("subject_types") subjectTypes: List<String>? = null,
        @Query("subject_ids") subjectIds: List<Long>? = null,
        @Query("unlocked") unlocked: Boolean? = null,
        @Query("started") started: Boolean? = null,
        @Query("burned") burned: Boolean? = null,
        @Query("hidden") hidden: Boolean? = null
    ): WkCollectionResponse<AssignmentData>

    /** Follows an assignments collection's `pages.next_url` — see [core.network.collectAllPages]. */
    @GET
    suspend fun getAssignmentsPage(@Url url: String): WkCollectionResponse<AssignmentData>

    @GET("level_progressions")
    suspend fun getLevelProgressions(): WkCollectionResponse<LevelProgressionData>

    @GET("subjects")
    suspend fun getSubjects(
        @Query("ids") ids: List<Long>? = null,
        @Query("levels") levels: List<Int>? = null,
        @Query("types") types: List<String>? = null,
        @Query("updated_after") updatedAfter: String? = null
    ): WkCollectionResponse<SubjectData>

    /** Follows a subjects collection's `pages.next_url` — see [core.network.collectAllPages]. */
    @GET
    suspend fun getSubjectsPage(@Url url: String): WkCollectionResponse<SubjectData>

    @GET("spaced_repetition_systems")
    suspend fun getSpacedRepetitionSystems(
        @Query("ids") ids: List<Long>? = null,
        @Query("updated_after") updatedAfter: String? = null
    ): WkCollectionResponse<SpacedRepetitionSystemData>

    @GET("review_statistics")
    suspend fun getReviewStatistics(
        @Query("updated_after") updatedAfter: String? = null
    ): WkCollectionResponse<ReviewStatisticData>

    /** Follows a review_statistics collection's `pages.next_url` — see [core.network.collectAllPages]. */
    @GET
    suspend fun getReviewStatisticsPage(@Url url: String): WkCollectionResponse<ReviewStatisticData>

    @GET("study_materials")
    suspend fun getStudyMaterials(
        @Query("updated_after") updatedAfter: String? = null
    ): WkCollectionResponse<StudyMaterialData>

    /** Follows a study_materials collection's `pages.next_url` — see [core.network.collectAllPages]. */
    @GET
    suspend fun getStudyMaterialsPage(@Url url: String): WkCollectionResponse<StudyMaterialData>

    // GET /v2/reviews is deliberately not implemented: it's deprecated and always returns an empty
    // array of data (confirmed against the docs) — WaniKani no longer stores individual review
    // history server-side.

    @POST("reviews")
    suspend fun submitReview(@Body request: ReviewSubmissionRequest): WkResourceItem<ReviewResultData>

    @PUT("assignments/{id}/start")
    suspend fun startAssignment(
        @Path("id") assignmentId: Long,
        @Body request: StartAssignmentRequest = StartAssignmentRequest()
    ): WkResourceItem<AssignmentData>
}
