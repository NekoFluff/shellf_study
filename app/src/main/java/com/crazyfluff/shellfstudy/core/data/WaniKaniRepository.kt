package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.core.data.model.DashboardSummary
import com.crazyfluff.shellfstudy.core.data.model.LessonItem
import com.crazyfluff.shellfstudy.core.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.core.data.model.ReviewItem
import com.crazyfluff.shellfstudy.core.data.model.SubjectSummary
import com.crazyfluff.shellfstudy.core.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.core.database.AssignmentDao
import com.crazyfluff.shellfstudy.core.database.AssignmentEntity
import com.crazyfluff.shellfstudy.core.database.SubjectDao
import com.crazyfluff.shellfstudy.core.database.SubjectEntity
import com.crazyfluff.shellfstudy.core.network.ReviewSubmissionBody
import com.crazyfluff.shellfstudy.core.network.ReviewSubmissionRequest
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.network.WaniKaniApi
import com.crazyfluff.shellfstudy.core.util.stripWkMarkup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaniKaniRepository @Inject constructor(
    private val api: WaniKaniApi,
    private val subjectDao: SubjectDao,
    private val assignmentDao: AssignmentDao
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

    /** Fetches the assignments due for review right now (and their subject content) and caches them. */
    suspend fun refreshReviewQueue(): ApiResult<Unit> = safeApiCall {
        val assignmentItems = api.getAssignments(immediatelyAvailableForReview = true).data

        if (assignmentItems.isEmpty()) {
            assignmentDao.replaceDueForReview(emptyList())
            return@safeApiCall
        }

        val subjectItems = api.getSubjects(assignmentItems.map { it.data.subjectId }).data
        subjectDao.upsertAll(
            subjectItems.map { item ->
                SubjectEntity(
                    id = item.id,
                    subjectType = item.objectType,
                    level = item.data.level,
                    slug = item.data.slug,
                    characters = item.data.characters,
                    meanings = item.data.meanings,
                    readings = item.data.readings,
                    documentUrl = item.data.documentUrl,
                    meaningMnemonic = item.data.meaningMnemonic?.let(::stripWkMarkup),
                    readingMnemonic = item.data.readingMnemonic?.let(::stripWkMarkup)
                )
            }
        )

        assignmentDao.replaceDueForReview(
            assignmentItems.map { item ->
                AssignmentEntity(
                    id = item.id,
                    subjectId = item.data.subjectId,
                    subjectType = item.data.subjectType,
                    srsStage = item.data.srsStage,
                    availableAt = item.data.availableAt,
                    passedAt = item.data.passedAt,
                    burnedAt = item.data.burnedAt,
                    hidden = item.data.hidden,
                    dueForReview = true
                )
            }
        )
    }

    /** Fetches the assignments available to learn right now (and their subject content) and caches them. */
    suspend fun refreshLessonQueue(): ApiResult<Unit> = safeApiCall {
        val assignmentItems = api.getAssignments(immediatelyAvailableForLessons = true).data

        if (assignmentItems.isEmpty()) {
            assignmentDao.replaceDueForLesson(emptyList())
            return@safeApiCall
        }

        val subjectItems = api.getSubjects(assignmentItems.map { it.data.subjectId }).data
        subjectDao.upsertAll(
            subjectItems.map { item ->
                SubjectEntity(
                    id = item.id,
                    subjectType = item.objectType,
                    level = item.data.level,
                    slug = item.data.slug,
                    characters = item.data.characters,
                    meanings = item.data.meanings,
                    readings = item.data.readings,
                    documentUrl = item.data.documentUrl,
                    meaningMnemonic = item.data.meaningMnemonic?.let(::stripWkMarkup),
                    readingMnemonic = item.data.readingMnemonic?.let(::stripWkMarkup)
                )
            }
        )

        assignmentDao.replaceDueForLesson(
            assignmentItems.map { item ->
                AssignmentEntity(
                    id = item.id,
                    subjectId = item.data.subjectId,
                    subjectType = item.data.subjectType,
                    srsStage = item.data.srsStage,
                    availableAt = item.data.availableAt,
                    passedAt = item.data.passedAt,
                    burnedAt = item.data.burnedAt,
                    hidden = item.data.hidden,
                    dueForReview = false,
                    dueForLesson = true
                )
            }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLessonQueue(): Flow<List<LessonItem>> =
        assignmentDao.observeDueForLesson().flatMapLatest { assignments ->
            if (assignments.isEmpty()) {
                flowOf(emptyList())
            } else {
                subjectDao.observeByIds(assignments.map { it.subjectId }).map { subjects ->
                    val subjectsById = subjects.associateBy { it.id }
                    assignments.mapNotNull { assignment ->
                        val subject = subjectsById[assignment.subjectId] ?: return@mapNotNull null
                        LessonItem(
                            assignmentId = assignment.id,
                            subjectId = subject.id,
                            subjectType = SubjectType.fromWkString(subject.subjectType),
                            characters = subject.characters,
                            level = subject.level,
                            meanings = subject.meanings.map { it.meaning },
                            readings = subject.readings.map { it.reading },
                            meaningMnemonic = subject.meaningMnemonic,
                            readingMnemonic = subject.readingMnemonic
                        )
                    }
                }
            }
        }

    /** Marks a lesson's assignment as started once the user has quizzed through it successfully. */
    suspend fun startAssignment(assignmentId: Long): ApiResult<Unit> = safeApiCall {
        api.startAssignment(assignmentId)
        Unit
    }

    /** Count of assignments started since local midnight — used for the "lessons done today" indicator. */
    suspend fun fetchLessonsCompletedToday(): ApiResult<Int> = safeApiCall {
        val startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        api.getAssignments(startedAfter = startOfDay).totalCount
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReviewQueue(): Flow<List<ReviewItem>> =
        assignmentDao.observeDueForReview().flatMapLatest { assignments ->
            if (assignments.isEmpty()) {
                flowOf(emptyList())
            } else {
                subjectDao.observeByIds(assignments.map { it.subjectId }).map { subjects ->
                    val subjectsById = subjects.associateBy { it.id }
                    assignments.mapNotNull { assignment ->
                        val subject = subjectsById[assignment.subjectId] ?: return@mapNotNull null
                        ReviewItem(
                            assignmentId = assignment.id,
                            subjectId = subject.id,
                            subjectType = SubjectType.fromWkString(subject.subjectType),
                            characters = subject.characters,
                            level = subject.level,
                            srsStage = assignment.srsStage,
                            meanings = subject.meanings.map { it.meaning },
                            readings = subject.readings.map { it.reading }
                        )
                    }
                }
            }
        }

    /**
     * Subjects available to search — currently limited to whatever has been cached locally by
     * past review sessions, not the user's full WaniKani subject library. A dedicated bulk-sync
     * (fetching all unlocked subjects, not just ones due for review) would be needed to search
     * the complete set; that's a larger follow-up, not part of this feature.
     */
    fun observeCachedSubjects(): Flow<List<SubjectSummary>> =
        subjectDao.observeAll().map { entities ->
            entities.map { entity ->
                SubjectSummary(
                    subjectId = entity.id,
                    subjectType = SubjectType.fromWkString(entity.subjectType),
                    characters = entity.characters,
                    level = entity.level,
                    meanings = entity.meanings.map { it.meaning },
                    readings = entity.readings.map { it.reading }
                )
            }
        }

    suspend fun submitReview(assignmentId: Long, grade: ReviewGrade): ApiResult<Unit> = safeApiCall {
        api.submitReview(
            ReviewSubmissionRequest(
                ReviewSubmissionBody(
                    assignmentId = assignmentId,
                    incorrectMeaningAnswers = if (grade.meaningCorrect) 0 else 1,
                    incorrectReadingAnswers = if (grade.readingCorrect) 0 else 1
                )
            )
        )
        Unit
    }

    private suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): ApiResult<T> =
        try {
            ApiResult.Success(block())
        } catch (e: IOException) {
            ApiResult.Error("Network error — check your connection.", e)
        } catch (e: HttpException) {
            val message = if (e.code() == 401) "Invalid API token." else "WaniKani API error (${e.code()})."
            ApiResult.Error(message, e)
        }
}
