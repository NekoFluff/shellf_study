package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.core.data.model.ContextSentence
import com.crazyfluff.shellfstudy.core.data.model.ItemSpread
import com.crazyfluff.shellfstudy.core.data.model.LessonItem
import com.crazyfluff.shellfstudy.core.data.model.LevelItem
import com.crazyfluff.shellfstudy.core.data.model.LevelProgress
import com.crazyfluff.shellfstudy.core.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.core.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.core.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.core.data.model.ReviewItem
import com.crazyfluff.shellfstudy.core.data.model.SrsStage
import com.crazyfluff.shellfstudy.core.data.model.SubjectTypeProgress
import com.crazyfluff.shellfstudy.core.data.model.toPronunciationAudios
import com.crazyfluff.shellfstudy.core.database.AssignmentDao
import com.crazyfluff.shellfstudy.core.database.AssignmentEntity
import com.crazyfluff.shellfstudy.core.database.SubjectDao
import com.crazyfluff.shellfstudy.core.database.SubjectEntity
import com.crazyfluff.shellfstudy.core.database.SyncStateDao
import com.crazyfluff.shellfstudy.core.network.AssignmentData
import com.crazyfluff.shellfstudy.core.network.SubjectType
import com.crazyfluff.shellfstudy.core.network.WaniKaniApi
import com.crazyfluff.shellfstudy.core.network.WkResourceItem
import com.crazyfluff.shellfstudy.core.network.collectAllPages
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

private const val RESOURCE_ASSIGNMENTS = "assignments"
private val ASSIGNMENTS_STALENESS = Duration.ofHours(1)

private val APPRENTICE_STAGES = listOf(SrsStage.APPRENTICE_1, SrsStage.APPRENTICE_2, SrsStage.APPRENTICE_3, SrsStage.APPRENTICE_4)
private val GURU_STAGES = listOf(SrsStage.GURU_1, SrsStage.GURU_2)

/** Guru or higher is what counts toward leveling up. */
private val GURU_SRS_STAGE = SrsStage.GURU_1.raw

/** Owns the full assignment mirror — SRS progress for every subject the user has encountered. */
@Singleton
class AssignmentRepository @Inject constructor(
    private val api: WaniKaniApi,
    private val assignmentDao: AssignmentDao,
    private val subjectDao: SubjectDao,
    private val syncStateDao: SyncStateDao,
    private val subjectRepository: SubjectRepository
) {
    suspend fun syncAssignments(force: Boolean = false): ApiResult<Unit> {
        if (!shouldSync(syncStateDao, RESOURCE_ASSIGNMENTS, force, ASSIGNMENTS_STALENESS)) return ApiResult.Success(Unit)
        return safeApiCall {
            val cursor = syncCursor(syncStateDao, RESOURCE_ASSIGNMENTS)
            val startedAt = Instant.now().toString()
            val items = collectAllPages(
                firstPage = { api.getAssignments(updatedAfter = cursor) },
                nextPage = { url -> api.getAssignmentsPage(url) }
            )
            assignmentDao.upsertAll(items.map { it.toEntity() })
            recordSyncSuccess(syncStateDao, RESOURCE_ASSIGNMENTS, cursor = startedAt)
        }
    }

    /**
     * Ensures assignments and subjects are up to date before starting a review/lesson session —
     * both staleness-gated, not forced, since the dashboard already syncs this same data on load
     * and on every resume. Subjects are needed alongside assignments: without subject content
     * already cached, [observeReviewQueue] and [observeLessonQueue] would join to nothing and
     * silently show an empty queue.
     */
    suspend fun refreshReviewQueue(): ApiResult<Unit> = refreshQueue()

    suspend fun refreshLessonQueue(): ApiResult<Unit> = refreshQueue()

    private suspend fun refreshQueue(): ApiResult<Unit> {
        val subjectsResult = subjectRepository.syncSubjects()
        if (subjectsResult is ApiResult.Error) return subjectsResult
        return syncAssignments(force = false)
    }

    suspend fun startAssignment(assignmentId: Long): ApiResult<Unit> = safeApiCall {
        val response = api.startAssignment(assignmentId)
        assignmentDao.upsertAll(listOf(response.toEntity()))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReviewQueue(): Flow<List<ReviewItem>> =
        assignmentDao.observeDueForReview(Instant.now().toString()).flatMapLatest { assignments ->
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
                            meanings = subject.acceptedMeanings(),
                            readings = subject.acceptedGradableReadings(),
                            auxiliaryMeanings = subject.whitelistAuxiliaryMeanings(),
                            pronunciationAudios = subject.toPronunciationAudios()
                        )
                    }
                }
            }
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
                            meanings = subject.acceptedMeanings(),
                            readings = subject.acceptedGradableReadings(),
                            meaningMnemonic = subject.meaningMnemonic,
                            readingMnemonic = subject.readingMnemonic,
                            auxiliaryMeanings = subject.whitelistAuxiliaryMeanings(),
                            meaningHint = subject.meaningHint,
                            readingHint = subject.readingHint,
                            onyomiReadings = subject.readings.filter { it.type == "onyomi" }.map { it.reading },
                            kunyomiReadings = subject.readings.filter { it.type == "kunyomi" }.map { it.reading },
                            nanoriReadings = subject.readings.filter { it.type == "nanori" }.map { it.reading },
                            partsOfSpeech = subject.partsOfSpeech,
                            contextSentences = subject.contextSentences.map { ContextSentence(japanese = it.ja, english = it.en) },
                            componentSubjectIds = subject.componentSubjectIds,
                            amalgamationSubjectIds = subject.amalgamationSubjectIds,
                            visuallySimilarSubjectIds = subject.visuallySimilarSubjectIds
                        )
                    }
                }
            }
        }

    fun observeReviewForecast(hours: Int = 24): Flow<ReviewForecast> {
        val now = Instant.now()
        val nowIso = now.toString()
        return combine(
            assignmentDao.observeDueForReview(nowIso),
            assignmentDao.observeUpcoming(nowIso)
        ) { availableNow, upcoming ->
            val buckets = (1..hours).map { hourOffset ->
                val bucketStart = now.plus(Duration.ofHours((hourOffset - 1).toLong()))
                val bucketEnd = now.plus(Duration.ofHours(hourOffset.toLong()))
                val count = upcoming.count { assignment ->
                    val availableAt = assignment.availableAt?.let(Instant::parse) ?: return@count false
                    !availableAt.isBefore(bucketStart) && availableAt.isBefore(bucketEnd)
                }
                ReviewForecastBucket(hoursFromNow = hourOffset, availableAt = bucketStart, newlyAvailableCount = count)
            }
            ReviewForecast(reviewsAvailableNow = availableNow.size, buckets = buckets)
        }
    }

    fun observeSrsItemSpread(): Flow<ItemSpread> =
        combine(assignmentDao.observeSrsStageCounts(), subjectDao.observeTotalCount()) { stageCounts, totalSubjects ->
            val byStage = stageCounts.associate { SrsStage.fromRaw(it.srsStage) to it.count }
            val apprentice = APPRENTICE_STAGES.sumOf { byStage[it] ?: 0 }
            val guru = GURU_STAGES.sumOf { byStage[it] ?: 0 }
            val master = byStage[SrsStage.MASTER] ?: 0
            val enlightened = byStage[SrsStage.ENLIGHTENED] ?: 0
            val burned = byStage[SrsStage.BURNED] ?: 0
            val started = apprentice + guru + master + enlightened + burned
            ItemSpread(
                lockedCount = (totalSubjects - started).coerceAtLeast(0),
                apprenticeCount = apprentice,
                guruCount = guru,
                masterCount = master,
                enlightenedCount = enlightened,
                burnedCount = burned
            )
        }

    fun observeLevelProgress(level: Int): Flow<LevelProgress> =
        assignmentDao.observeLevelProgressItemRows(level).map { rows ->
            val bySubjectType = rows.groupBy { SubjectType.fromWkString(it.subjectType) }
            val breakdown = listOf(SubjectType.RADICAL, SubjectType.KANJI, SubjectType.VOCABULARY).map { type ->
                val items = bySubjectType[type].orEmpty().map { row ->
                    LevelItem(
                        subjectId = row.subjectId,
                        subjectType = type,
                        display = row.characters ?: row.slug,
                        passed = row.passedAt != null,
                        characterImageUrl = row.characterImageUrl
                    )
                }
                SubjectTypeProgress(subjectType = type, items = items)
            }
            LevelProgress(level = level, breakdown = breakdown)
        }

    fun observeItemsSeenCount(): Flow<Int> = assignmentDao.observeItemsSeenCount()

    /** Count of assignments started since local midnight — used for the "lessons done today" indicator. */
    fun observeLessonsCompletedToday(): Flow<Int> =
        assignmentDao.observeStartedTodayCount(startOfTodayIso())

    /**
     * How many of the current level's kanji are at Guru or higher, out of the total — WaniKani
     * requires 90% of a level's kanji at Guru+ before the user can level up.
     */
    fun observeLevelUpProgress(level: Int): Flow<LevelUpProgress> =
        assignmentDao.observeKanjiLevelUpRows(level).map { rows ->
            LevelUpProgress(
                kanjiGuruedOrHigher = rows.count { it.srsStage >= GURU_SRS_STAGE },
                kanjiTotal = rows.size
            )
        }

    private fun startOfTodayIso(): String =
        LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
}

/** Meanings WaniKani actually accepts as a correct answer — excludes any explicitly flagged
 *  `accepted_answer: false` (shown for reference, e.g. deprecated alternates, but not gradable). */
private fun SubjectEntity.acceptedMeanings(): List<String> =
    meanings.filter { it.acceptedMeaning }.map { it.meaning }

/** Readings gradable against the single "what is the reading?" quiz question — excludes any
 *  explicitly flagged not-accepted, and kanji nanori (name readings), which WaniKani shows for
 *  reference but never tests. */
private fun SubjectEntity.acceptedGradableReadings(): List<String> =
    readings.filter { it.acceptedReading && it.type != "nanori" }.map { it.reading }

/** WaniKani's own official alternate meanings (e.g. "1" alongside "one") that should be accepted
 *  just like a primary meaning — excludes blacklist entries, which are deliberately wrong-looking
 *  decoys never meant to be treated as correct. */
private fun SubjectEntity.whitelistAuxiliaryMeanings(): List<String> =
    auxiliaryMeanings.filter { it.type == "whitelist" }.map { it.meaning }

private fun WkResourceItem<AssignmentData>.toEntity(): AssignmentEntity = AssignmentEntity(
    id = id,
    subjectId = data.subjectId,
    subjectType = data.subjectType,
    srsStage = data.srsStage,
    createdAt = data.createdAt,
    unlockedAt = data.unlockedAt,
    startedAt = data.startedAt,
    passedAt = data.passedAt,
    burnedAt = data.burnedAt,
    availableAt = data.availableAt,
    resurrectedAt = data.resurrectedAt,
    hidden = data.hidden
)
