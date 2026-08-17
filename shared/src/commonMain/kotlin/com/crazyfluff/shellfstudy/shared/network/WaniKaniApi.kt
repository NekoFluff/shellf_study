package com.crazyfluff.shellfstudy.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

private fun <T> HttpRequestBuilder.parameterList(key: String, values: List<T>?) {
    values?.forEach { parameter(key, it) }
}

/**
 * Ktor client for the WaniKani API v2 (https://docs.api.wanikani.com/20170710/). [baseUrl] is
 * overridable so tests can point this at a local mock server; production callers use the default.
 */
class WaniKaniApi(
    private val httpClient: HttpClient,
    private val baseUrl: String = WANIKANI_BASE_URL
) {

    suspend fun getUser(): WkSingleResponse<UserData> =
        httpClient.get("${baseUrl}user").body()

    suspend fun getSummary(): WkSingleResponse<SummaryData> =
        httpClient.get("${baseUrl}summary").body()

    suspend fun getAssignments(
        immediatelyAvailableForReview: Boolean? = null,
        immediatelyAvailableForLessons: Boolean? = null,
        startedAfter: String? = null,
        updatedAfter: String? = null,
        ids: List<Long>? = null,
        levels: List<Int>? = null,
        subjectTypes: List<String>? = null,
        subjectIds: List<Long>? = null,
        unlocked: Boolean? = null,
        started: Boolean? = null,
        burned: Boolean? = null,
        hidden: Boolean? = null
    ): WkCollectionResponse<AssignmentData> = httpClient.get("${baseUrl}assignments") {
        parameter("immediately_available_for_review", immediatelyAvailableForReview)
        parameter("immediately_available_for_lessons", immediatelyAvailableForLessons)
        parameter("started_after", startedAfter)
        parameter("updated_after", updatedAfter)
        parameterList("ids", ids)
        parameterList("levels", levels)
        parameterList("subject_types", subjectTypes)
        parameterList("subject_ids", subjectIds)
        parameter("unlocked", unlocked)
        parameter("started", started)
        parameter("burned", burned)
        parameter("hidden", hidden)
    }.body()

    /** Follows an assignments collection's `pages.next_url` — see [collectAllPages]. */
    suspend fun getAssignmentsPage(url: String): WkCollectionResponse<AssignmentData> =
        httpClient.get(url).body()

    suspend fun getLevelProgressions(): WkCollectionResponse<LevelProgressionData> =
        httpClient.get("${baseUrl}level_progressions").body()

    suspend fun getSubjects(
        ids: List<Long>? = null,
        levels: List<Int>? = null,
        types: List<String>? = null,
        updatedAfter: String? = null
    ): WkCollectionResponse<SubjectData> = httpClient.get("${baseUrl}subjects") {
        parameterList("ids", ids)
        parameterList("levels", levels)
        parameterList("types", types)
        parameter("updated_after", updatedAfter)
    }.body()

    /** Follows a subjects collection's `pages.next_url` — see [collectAllPages]. */
    suspend fun getSubjectsPage(url: String): WkCollectionResponse<SubjectData> =
        httpClient.get(url).body()

    suspend fun getSpacedRepetitionSystems(
        ids: List<Long>? = null,
        updatedAfter: String? = null
    ): WkCollectionResponse<SpacedRepetitionSystemData> =
        httpClient.get("${baseUrl}spaced_repetition_systems") {
            parameterList("ids", ids)
            parameter("updated_after", updatedAfter)
        }.body()

    suspend fun getReviewStatistics(
        updatedAfter: String? = null
    ): WkCollectionResponse<ReviewStatisticData> =
        httpClient.get("${baseUrl}review_statistics") {
            parameter("updated_after", updatedAfter)
        }.body()

    /** Follows a review_statistics collection's `pages.next_url` — see [collectAllPages]. */
    suspend fun getReviewStatisticsPage(url: String): WkCollectionResponse<ReviewStatisticData> =
        httpClient.get(url).body()

    // GET /v2/reviews is deliberately not implemented: it's deprecated and always returns an empty
    // array of data (confirmed against the docs) — WaniKani no longer stores individual review
    // history server-side.

    suspend fun submitReview(request: ReviewSubmissionRequest): WkResourceItem<ReviewResultData> =
        httpClient.post("${baseUrl}reviews") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    suspend fun startAssignment(
        assignmentId: Long,
        request: StartAssignmentRequest = StartAssignmentRequest()
    ): WkResourceItem<AssignmentData> =
        httpClient.put("${baseUrl}assignments/$assignmentId/start") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

    /** Targeted single-assignment refetch — used to reconcile local state after a pending outbox
     *  mutation is terminally rejected and there's no authoritative response to patch in locally. */
    suspend fun getAssignment(assignmentId: Long): WkResourceItem<AssignmentData> =
        httpClient.get("${baseUrl}assignments/$assignmentId").body()
}
