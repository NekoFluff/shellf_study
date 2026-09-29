package com.crazyfluff.shellfstudy.shared.data

import kotlin.concurrent.Volatile
import com.crazyfluff.shellfstudy.shared.data.model.ContextSentence
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.SrsStageCalculator
import com.crazyfluff.shellfstudy.shared.data.model.SubjectAssignmentStats
import com.crazyfluff.shellfstudy.shared.data.model.toPronunciationAudios
import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.SrsSystemDao
import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectDao
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.database.SyncStateDao
import com.crazyfluff.shellfstudy.shared.network.AssignmentData
import com.crazyfluff.shellfstudy.shared.network.ReviewResultData
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.WkResourceItem
import com.crazyfluff.shellfstudy.shared.network.collectAllPages
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal val ASSIGNMENTS_STALENESS = 1.hours

/**
 * How stale assignments may be before the dashboard's resume path refetches them.
 *
 * Short on purpose. Normal syncing uses the hour-long [ASSIGNMENTS_STALENESS], but the resume path has
 * to keep the forecast, item-spread and level-progress cards level with the banner counts, which come
 * from `/summary` and are therefore already fresh by the time those cards render.
 *
 * The gap this closes: the resume path used to *force* assignments unconditionally, which bypasses the
 * staleness gate entirely. An incremental fetch still returns whatever WaniKani has touched, and the
 * write re-inserts every one of those rows with `INSERT OR REPLACE` — on an account at a few thousand
 * assignments that is thousands of row rewrites maintaining five indexes each, every time the user
 * comes back to the dashboard. The gate exists precisely to avoid paying that when nothing has changed,
 * and forcing it defeated the gate.
 *
 * Half a minute rather than zero: a resume moments after a sync skips the rewrite, while a resume after
 * a real gap still refreshes. Session work does not depend on this — grades apply optimistically to the
 * local table immediately, so the user's own progress is already on screen.
 */
internal val ASSIGNMENTS_RESUME_STALENESS = 30.seconds

/** The one place a [ReviewGrade] turns into a resulting SRS stage. Both the optimistic local write
 *  ([AssignmentRepository.applyOptimisticReviewResult]) and the synchronous UI prediction
 *  ([AssignmentRepository.computeReviewRankChange]) go through this, so the chip, the cached stage,
 *  and the `incorrect_*_answers` posted to WaniKani can't disagree about the outcome.
 *
 *  Branches on [ReviewGrade.totalIncorrect] rather than [ReviewGrade.isFullyCorrect] deliberately:
 *  the count is what WaniKani's penalty is computed from, so keying the decision off the same value
 *  removes any way for the two to disagree. */
private fun ReviewGrade.nextStage(currentStage: Int, srsSystem: SrsSystemEntity): Int =
    if (totalIncorrect == 0) {
        SrsStageCalculator.nextStageOnCorrect(currentStage, srsSystem)
    } else {
        SrsStageCalculator.nextStageOnIncorrect(currentStage, totalIncorrect, srsSystem)
    }

/** Owns the full assignment mirror — SRS progress for every subject the user has encountered. */
class AssignmentRepository(
    private val api: WaniKaniApi,
    private val assignmentDao: AssignmentDao,
    private val subjectDao: SubjectDao,
    private val syncStateDao: SyncStateDao,
    private val srsSystemDao: SrsSystemDao,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun syncAssignments(force: Boolean = false): ApiResult<Unit> = safeApiCall {
        fetchAssignments(force)?.let { completeResourceSync(syncStateDao, SyncResources.ASSIGNMENTS, it) }
    }

    /**
     * Fetches assignments without writing them — the first half of [syncAssignments], exposed so
     * [com.crazyfluff.shellfstudy.shared.sync.SyncOrchestrator] can fetch every resource and then
     * write them all inside one transaction. Null when they are fresh enough to skip.
     */
    internal suspend fun fetchAssignments(
        force: Boolean = false,
        staleness: Duration = ASSIGNMENTS_STALENESS
    ): ResourceSync<List<AssignmentEntity>>? =
        fetchResourceSync(
            syncStateDao = syncStateDao,
            resource = SyncResources.ASSIGNMENTS,
            force = force,
            staleness = staleness,
            countRows = { it.size },
            fetch = { cursor ->
                collectAllPages(
                    firstPage = { api.getAssignments(updatedAfter = cursor) },
                    nextPage = { url -> api.getAssignmentsPage(url) }
                ).map { it.toEntity() }
            },
            write = { assignmentDao.upsertAll(it) }
        )

    suspend fun startAssignment(assignmentId: Long): ApiResult<Unit> = safeApiCall {
        val response = api.startAssignment(assignmentId)
        assignmentDao.upsertAll(listOf(response.toEntity()))
    }

    /**
     * Immediately patches the local assignment cache to reflect a review grade, before any network
     * call — the dashboard/review queue reflect progress instantly regardless of connectivity. Only
     * ever a prediction (see [SrsStageCalculator]); [reconcileAfterReviewResult] overwrites it with
     * the server-confirmed value once the real submission syncs. Returns null if the assignment
     * isn't cached, or its SRS system isn't cached anywhere (neither [srsSystemCache] nor the DB
     * fallback has it — e.g. SRS systems haven't synced since install) — the caller just won't see
     * a rank-change animation until the next successful sync catches this up. [srsSystemId] comes
     * from the caller's already-in-memory [ReviewItem]/[LessonItem], so this never needs to look it
     * up via [subjectDao] the way the older [srsSystemFor] still does for [reconcileAfterReviewResult].
     */
    suspend fun applyOptimisticReviewResult(assignmentId: Long, srsSystemId: Long, grade: ReviewGrade): RankChange? {
        val assignment = assignmentDao.getById(assignmentId) ?: return null
        val srsSystem = srsSystemById(srsSystemId) ?: return null
        val nextStage = grade.nextStage(assignment.srsStage, srsSystem)
        assignmentDao.upsertAll(listOf(assignment.withStageTransition(nextStage, srsSystem, Clock.System.now())))
        return RankChange(SrsStage.fromRaw(assignment.srsStage), SrsStage.fromRaw(nextStage))
    }

    /** Same idea as [applyOptimisticReviewResult] but for starting a lesson — every lesson item
     *  starts the same way (locked straight to the SRS system's starting stage), so no grade input
     *  is needed to know the target stage, only whether the assignment/SRS-system data is cached. */
    suspend fun applyOptimisticLessonStart(assignmentId: Long, srsSystemId: Long): RankChange? {
        val assignment = assignmentDao.getById(assignmentId) ?: return null
        val srsSystem = srsSystemById(srsSystemId) ?: return null
        assignmentDao.upsertAll(listOf(assignment.withStageTransition(srsSystem.startingStagePosition, srsSystem, Clock.System.now())))
        return RankChange(SrsStage.LOCKED, SrsStage.fromRaw(srsSystem.startingStagePosition))
    }

    // Small, rarely-changing reference data — WaniKani only has a couple of SRS systems — held in
    // memory so a review item's rank change can be predicted with zero DB access at all
    // ([computeReviewRankChange]), and so the deferred write
    // (applyOptimisticReviewResult/applyOptimisticLessonStart) doesn't need a DB round trip for the
    // SRS system either, only for the assignment row itself. Replaced wholesale on every warm, and
    // written from the loading path while graded answers read it, hence @Volatile.
    @Volatile
    private var srsSystemCache: Map<Long, SrsSystemEntity>? = null

    /** Reloads [srsSystemCache] — call before a grading session starts, and again once a sync may
     *  have brought SRS systems in, so every answer in that session can compute its rank change
     *  synchronously via [computeReviewRankChange]. A reload rather than a warm-once: the first
     *  session after install loads the queue before SRS systems have ever synced, and a cache
     *  filled then would stay empty for the life of the process. */
    suspend fun warmSrsSystemCache() {
        srsSystemCache = srsSystemDao.observeAll().first().associateBy { it.id }
    }

    /** Cache-first lookup, falling back to a direct DB read if the cache hasn't been warmed (or
     *  was warmed before this system existed — e.g. right after a first-ever sync). */
    private suspend fun srsSystemById(srsSystemId: Long): SrsSystemEntity? =
        srsSystemCache?.get(srsSystemId) ?: srsSystemDao.getById(srsSystemId)

    /**
     * Pure, synchronous prediction of a review grade's rank change — no DB access, safe to call
     * directly from a ViewModel's UI-update path. Needs [warmSrsSystemCache] to have already run;
     * returns null on a cache miss, same as [applyOptimisticReviewResult] does when the SRS system
     * isn't cached yet — the caller just won't see a rank-change animation for that answer. The
     * actual DB write is a separate, deferred concern — still [applyOptimisticReviewResult].
     */
    fun computeReviewRankChange(item: ReviewItem, grade: ReviewGrade): RankChange? {
        val srsSystem = srsSystemCache?.get(item.srsSystemId) ?: return null
        val nextStage = grade.nextStage(item.srsStage, srsSystem)
        return RankChange(SrsStage.fromRaw(item.srsStage), SrsStage.fromRaw(nextStage))
    }

    /**
     * Pure, synchronous prediction of a lesson item's starting rank change — same idea as
     * [computeReviewRankChange], but every lesson item starts the same way (locked straight to the
     * SRS system's starting stage) so there's no grade input to branch on, unlike a review's
     * up/down result. Needs [warmSrsSystemCache] to have already run; returns null on a cache miss.
     */
    fun computeLessonStartRankChange(srsSystemId: Long): RankChange? {
        val srsSystem = srsSystemCache?.get(srsSystemId) ?: return null
        return RankChange(SrsStage.LOCKED, SrsStage.fromRaw(srsSystem.startingStagePosition))
    }

    /** Reconciles the assignment with the WK-confirmed result once a queued review submission
     *  actually syncs — the server's value always wins over the local prediction. */
    suspend fun reconcileAfterReviewResult(result: ReviewResultData) {
        val assignment = assignmentDao.getById(result.assignmentId) ?: return
        val srsSystem = srsSystemFor(assignment.subjectId) ?: return
        assignmentDao.upsertAll(listOf(assignment.withStageTransition(result.endingSrsStage, srsSystem, Clock.System.now())))
    }

    /** Targeted single-assignment refetch — used when a pending outbox mutation is terminally
     *  rejected (e.g. HTTP 422, already recorded elsewhere) and there's no authoritative response
     *  to reconcile with locally, so the only way back to server truth is to just re-fetch it. */
    suspend fun refetchAssignment(assignmentId: Long): ApiResult<Unit> = safeApiCall {
        val response = api.getAssignment(assignmentId)
        assignmentDao.upsertAll(listOf(response.toEntity()))
    }

    private suspend fun srsSystemFor(subjectId: Long): SrsSystemEntity? =
        subjectDao.getById(subjectId)?.srsSystemId?.let { srsSystemById(it) }

    /** Local, immediately-reactive count of reviews due — see [observeReviewQueue] for the full
     *  items. Used by the dashboard to reconcile against the WaniKani `/summary` count, which can
     *  briefly lag behind a review just graded on this device (its outbox submission hasn't
     *  reached the server yet).
     *
     *  Re-subscribes on [hourlyRolloverTicks] because the DAO query takes `now` as a parameter:
     *  without it the predicate keeps the timestamp from first collection, so a dashboard left open
     *  across an hour boundary keeps counting reviews that came due since — which is exactly when the
     *  local count is the one being trusted (offline, or with submissions still in the outbox). */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReviewDueCount(): Flow<Int> = hourlyRolloverTicks()
        .flatMapLatest { now -> assignmentDao.observeDueForReviewCount(now.toString()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReviewQueue(): Flow<List<ReviewItem>> =
        assignmentDao.observeDueForReview(Clock.System.now().toString()).flatMapLatest { assignments ->
            if (assignments.isEmpty()) {
                flowOf(emptyList())
            } else {
                // Chunked: this is the unbounded one — every due assignment's subject id, which on an
                // account with a long backlog is more bound variables than old SQLite accepts.
                chunkedIds(assignments.map { it.subjectId }) { subjectDao.observeByIds(it) }.map { subjects ->
                    buildReviewItems(assignments, subjects)
                }
            }
        }.flowOn(defaultDispatcher)

    /** Resolves specific assignments to [ReviewItem]s by id, regardless of due status — for
     *  restoring a persisted session's progress, which may reference an item that has since
     *  graduated out of [observeReviewQueue]'s due filter. */
    suspend fun getReviewItems(assignmentIds: Collection<Long>): List<ReviewItem> {
        if (assignmentIds.isEmpty()) return emptyList()
        val assignments = chunkedIdsOnce(assignmentIds.toList()) { assignmentDao.getByIds(it) }
        if (assignments.isEmpty()) return emptyList()
        val subjects = chunkedIds(assignments.map { it.subjectId }) { subjectDao.observeByIds(it) }.first()
        return buildReviewItems(assignments, subjects)
    }

    private fun buildReviewItems(assignments: List<AssignmentEntity>, subjects: List<SubjectEntity>): List<ReviewItem> {
        val subjectsById = subjects.associateBy { it.id }
        return assignments.mapNotNull { assignment ->
            val subject = subjectsById[assignment.subjectId] ?: return@mapNotNull null
            ReviewItem(
                assignmentId = assignment.id,
                subjectId = subject.id,
                subjectType = SubjectType.fromWkString(subject.subjectType),
                characters = subject.characters,
                characterImageUrl = subject.characterImageUrl,
                level = subject.level,
                srsStage = assignment.srsStage,
                meanings = subject.acceptedMeanings(),
                readings = subject.acceptedGradableReadings(),
                auxiliaryMeanings = subject.whitelistAuxiliaryMeanings(),
                pronunciationAudios = subject.toPronunciationAudios(),
                srsSystemId = subject.srsSystemId
            )
        }
    }

    /** Local, immediately-reactive count of lessons due — see [observeReviewDueCount]'s doc for
     *  why the dashboard reconciles against this instead of trusting `/summary` alone. */
    fun observeLessonDueCount(): Flow<Int> = assignmentDao.observeDueForLessonCount()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLessonQueue(): Flow<List<LessonItem>> =
        assignmentDao.observeDueForLesson().flatMapLatest { assignments ->
            if (assignments.isEmpty()) {
                flowOf(emptyList())
            } else {
                chunkedIds(assignments.map { it.subjectId }) { subjectDao.observeByIds(it) }.map { subjects ->
                    buildLessonItems(assignments, subjects)
                }
            }
        }.flowOn(defaultDispatcher)

    /** Resolves specific assignments to [LessonItem]s by id, regardless of due status — for
     *  restoring a persisted session's progress, which may reference an item that has since
     *  been started out of [observeLessonQueue]'s due filter. */
    suspend fun getLessonItems(assignmentIds: Collection<Long>): List<LessonItem> {
        if (assignmentIds.isEmpty()) return emptyList()
        val assignments = chunkedIdsOnce(assignmentIds.toList()) { assignmentDao.getByIds(it) }
        if (assignments.isEmpty()) return emptyList()
        val subjects = chunkedIds(assignments.map { it.subjectId }) { subjectDao.observeByIds(it) }.first()
        return buildLessonItems(assignments, subjects)
    }

    private fun buildLessonItems(assignments: List<AssignmentEntity>, subjects: List<SubjectEntity>): List<LessonItem> {
        val subjectsById = subjects.associateBy { it.id }
        return assignments.mapNotNull { assignment ->
            val subject = subjectsById[assignment.subjectId] ?: return@mapNotNull null
            LessonItem(
                assignmentId = assignment.id,
                subjectId = subject.id,
                subjectType = SubjectType.fromWkString(subject.subjectType),
                characters = subject.characters,
                characterImageUrl = subject.characterImageUrl,
                level = subject.level,
                lessonPosition = subject.lessonPosition,
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
                pronunciationAudios = subject.toPronunciationAudios(),
                contextSentences = subject.contextSentences.map { ContextSentence(japanese = it.ja, english = it.en) },
                componentSubjectIds = subject.componentSubjectIds,
                amalgamationSubjectIds = subject.amalgamationSubjectIds,
                visuallySimilarSubjectIds = subject.visuallySimilarSubjectIds,
                srsSystemId = subject.srsSystemId
            )
        }
    }

    /** The subject's current SRS stage plus its lifecycle dates, or null if it hasn't been
     *  lessoned yet (no assignment exists) — the subject detail view's stat chip and milestone
     *  list source. */
    fun observeAssignmentStats(subjectId: Long): Flow<SubjectAssignmentStats?> =
        assignmentDao.observeBySubjectId(subjectId).map { assignment ->
            assignment?.let {
                SubjectAssignmentStats(
                    srsStage = SrsStage.fromRaw(it.srsStage),
                    nextReviewAt = it.availableAt?.let(Instant::parse),
                    unlockedAt = it.unlockedAt?.let(Instant::parse),
                    startedAt = it.startedAt?.let(Instant::parse),
                    passedAt = it.passedAt?.let(Instant::parse),
                    burnedAt = it.burnedAt?.let(Instant::parse)
                )
            }
        }
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

/** Derives availableAt/passedAt/burnedAt from a target SRS stage — shared by every "patch this
 *  assignment to stage X" call site (optimistic review grade, optimistic lesson start, and
 *  post-sync reconciliation), so they can't drift out of sync with each other. */
private fun AssignmentEntity.withStageTransition(newStage: Int, srsSystem: SrsSystemEntity, now: Instant): AssignmentEntity {
    val nowIso = now.toString()
    val availableAt = SrsStageCalculator.availableAtFor(newStage, srsSystem, now)?.toString()
    return copy(
        srsStage = newStage,
        startedAt = startedAt ?: nowIso,
        availableAt = availableAt,
        passedAt = passedAt ?: if (newStage >= srsSystem.passingStagePosition) nowIso else null,
        burnedAt = burnedAt ?: if (newStage >= srsSystem.burningStagePosition) nowIso else null
    )
}

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
