package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.ContextSentence
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpread
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpreadBucket
import com.crazyfluff.shellfstudy.shared.data.model.foldKana
import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelProgress
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastWindow
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.SrsStageCalculator
import com.crazyfluff.shellfstudy.shared.data.model.SubjectAssignmentStats
import com.crazyfluff.shellfstudy.shared.data.model.SubjectTypeProgress
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
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

/** Guru or higher is what counts toward leveling up. */
private val GURU_SRS_STAGE = SrsStage.GURU_1.raw

/** Which item-spread bucket a stage's count rolls up into — mirrors the dashboard theme's own SRS-stage bucketing. */
private fun bucketFor(stage: SrsStage): ItemSpreadBucket = when (stage) {
    SrsStage.LOCKED -> ItemSpreadBucket.LOCKED
    SrsStage.APPRENTICE_1, SrsStage.APPRENTICE_2, SrsStage.APPRENTICE_3, SrsStage.APPRENTICE_4 -> ItemSpreadBucket.APPRENTICE
    SrsStage.GURU_1, SrsStage.GURU_2 -> ItemSpreadBucket.GURU
    SrsStage.MASTER -> ItemSpreadBucket.MASTER
    SrsStage.ENLIGHTENED -> ItemSpreadBucket.ENLIGHTENED
    SrsStage.BURNED -> ItemSpreadBucket.BURNED
}

/** Which item-spread bucket [currentSrsStage] advances INTO on a correct review — a simple "+1
 *  stage, capped at Burned" rather than [SrsStageCalculator.nextStageOnCorrect], since that needs
 *  each assignment's [SrsSystemEntity] (a per-subject join the forecast doesn't otherwise load) and
 *  every SRS system currently ships burning at stage 9, matching this approximation exactly. */
private fun nextStageBucketFor(currentSrsStage: Int): ItemSpreadBucket {
    val nextStage = SrsStage.fromRaw((currentSrsStage + 1).coerceAtMost(SrsStage.BURNED.raw))
    return bucketFor(nextStage)
}

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

private fun Instant.truncatedToHour(): Instant = Instant.fromEpochSeconds((epochSeconds / 3600) * 3600)

/**
 * The instant of local midnight starting [date], as a string comparable against the `startedAt`
 * values WaniKani returns.
 *
 * The date is resolved *in [zone]*, not appended to a literal `T00:00:00Z`. Those differ by the
 * zone's offset, and the difference is not academic: stamping the date onto a UTC midnight makes the
 * cutoff hours too early for every zone behind UTC, so "lessons completed today" counts everything
 * since late yesterday afternoon. On a device in America/Phoenix (UTC-7) this turned a true count of
 * 0 into 13, because 13 assignments were started between 17:00 and midnight the previous local day.
 *
 * Rendered by hand rather than via `Instant.toString()`: the comparison in
 * [AssignmentDao.observeStartedSinceCount] is lexicographic, and every `startedAt` WaniKani writes
 * carries an explicit zero fraction (`2023-04-04T07:56:05.000000Z`). `Instant.toString()` drops a
 * zero fraction entirely and emits a variable number of fractional digits otherwise, so the digits
 * would stop deciding the comparison and `'.' < 'Z'` would instead.
 */
internal fun localMidnightIso(date: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val midnight = date.atTime(0, 0).toInstant(zone).toString()
    // `toString()` yields e.g. "2026-09-28T07:00:00Z" for a whole-second instant; pad it to the
    // canonical six-digit fraction, and pass anything already carrying a fraction through unchanged.
    val withoutZulu = midnight.removeSuffix("Z")
    return if ('.' in withoutZulu) "$withoutZulu" + "Z" else "$withoutZulu" + ".000000Z"
}

/** Owns the full assignment mirror — SRS progress for every subject the user has encountered. */
class AssignmentRepository(
    private val api: WaniKaniApi,
    private val assignmentDao: AssignmentDao,
    private val subjectDao: SubjectDao,
    private val syncStateDao: SyncStateDao,
    private val subjectRepository: SubjectRepository,
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

    // Small, rarely-changing reference data — WaniKani only has a couple of SRS systems — cached
    // in memory once so a review item's rank change can be predicted with zero DB access at all
    // ([computeReviewRankChange]), and so the deferred write
    // (applyOptimisticReviewResult/applyOptimisticLessonStart) doesn't need a DB round trip for the
    // SRS system either, only for the assignment row itself. Safe to treat as immutable for a
    // session: SRS systems don't change after the initial sync.
    private var srsSystemCache: Map<Long, SrsSystemEntity>? = null

    /** Warms [srsSystemCache] if it isn't already — call once before a grading session starts
     *  (e.g. when the review/lesson queue loads), so every review answer in that session can
     *  compute its rank change synchronously via [computeReviewRankChange]. */
    suspend fun warmSrsSystemCache() {
        if (srsSystemCache == null) {
            srsSystemCache = srsSystemDao.observeAll().first().associateBy { it.id }
        }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeReviewForecast(window: ReviewForecastWindow = ReviewForecastWindow.DAY): Flow<ReviewForecast> {
        // Re-subscribe to the DAO at every hour boundary — see hourlyRolloverTicks for why the
        // baked-in `availableAt <= :nowIso` predicate needs it. Without that, reviews that become
        // available when the clock hour rolls over stay stuck in "upcoming" instead of moving to
        // "available now", even though the review count (fetched from the API) updates correctly.
        // WaniKani assignments only ever become available on the hour, so buckets are aligned to
        // clock-hour boundaries (not rolling 1h windows from `now`) — otherwise a bucket labeled
        // e.g. "3 PM" would actually span 2:47-3:47, and the label would read an hour behind the
        // reviews it describes.
        return hourlyRolloverTicks().flatMapLatest { now ->
            val nowIso = now.toString()
            val currentHourStart = now.truncatedToHour()
            combine(
                assignmentDao.observeAvailableNow(nowIso),
                assignmentDao.observeUpcoming(nowIso)
            ) { availableNow, upcoming ->
                // Grouped straight into window.bucketCount buckets (not one bucket per hour then
                // merged down later) — a 4-month window would otherwise mean building 2880 mostly-
                // empty hourly buckets just to immediately discard all but 12 of them.
                val byBucketIndex = upcoming.groupBy { assignment ->
                    val availableAt = assignment.availableAt?.let(Instant::parse) ?: return@groupBy null
                    val hoursFromNow = (availableAt - currentHourStart).inWholeHours.toInt()
                    ((hoursFromNow - 1) / window.bucketHours) + 1
                }
                val buckets = (1..window.bucketCount).map { bucketIndex ->
                    val bucketStart = currentHourStart + (bucketIndex * window.bucketHours).hours
                    val inBucket = byBucketIndex[bucketIndex].orEmpty()
                    ReviewForecastBucket(
                        hoursFromNow = bucketIndex * window.bucketHours,
                        availableAt = bucketStart,
                        newlyAvailableCount = inBucket.size,
                        countsByType = inBucket.groupingBy { SubjectType.fromWkString(it.subjectType) }.eachCount(),
                        countsByNextStage = inBucket.groupingBy { nextStageBucketFor(it.srsStage) }.eachCount()
                    )
                }
                ReviewForecast(
                    reviewsAvailableNow = availableNow.size,
                    buckets = buckets,
                    availableNowCountsByType = availableNow.groupingBy { SubjectType.fromWkString(it.subjectType) }.eachCount(),
                    availableNowCountsByNextStage = availableNow.groupingBy { nextStageBucketFor(it.srsStage) }.eachCount()
                )
            }
        }.flowOn(defaultDispatcher)
        // Room's InvalidationTracker re-fires observeDueForReview/observeUpcoming on ANY write to
        // the assignments table, anywhere in the app — not just ones this forecast cares about.
        // DashboardViewModel collects this in viewModelScope, which stays alive (and this keeps
        // recomputing) even while Dashboard isn't the visible screen, e.g. mid-review-session where
        // Review is pushed on top of it. Without flowOn, the O(hours * upcoming-count) bucketing
        // above ran synchronously on Dispatchers.Main.immediate, competing with whatever screen was
        // actually in the foreground for the same frame — this is what caused the review-submit
        // stutter (dropped frame(s) right after Submit), not anything in the Review screen itself.
    }

    // flowOn(Dispatchers.Default) on these (and observeReviewForecast above) is defense in depth,
    // not the primary fix — DashboardViewModel now only collects any of these while Dashboard is
    // actually visible (see its stateIn(WhileSubscribed(...)) usage), so Room's InvalidationTracker
    // no longer wakes them up on every unrelated write while some other screen is in the
    // foreground. This just guarantees that even while genuinely subscribed, the grouping/mapping
    // work below never runs on Dispatchers.Main.immediate.
    fun observeSrsItemSpread(): Flow<ItemSpread> =
        combine(assignmentDao.observeSrsStageAndTypeCounts(), subjectDao.observeTotalCountsByType()) { stageTypeCounts, totalsByType ->
            val countsByBucket = mutableMapOf<ItemSpreadBucket, MutableMap<SubjectType, Int>>()
            stageTypeCounts.forEach { row ->
                val bucket = bucketFor(SrsStage.fromRaw(row.srsStage))
                val type = SubjectType.fromWkString(row.subjectType).foldKana()
                val byType = countsByBucket.getOrPut(bucket) { mutableMapOf() }
                byType[type] = (byType[type] ?: 0) + row.count
            }

            val startedByType = mutableMapOf<SubjectType, Int>()
            countsByBucket.values.forEach { byType ->
                byType.forEach { (type, count) -> startedByType[type] = (startedByType[type] ?: 0) + count }
            }
            val totalByType = totalsByType
                .groupBy { SubjectType.fromWkString(it.subjectType).foldKana() }
                .mapValues { (_, rows) -> rows.sumOf { it.count } }
            countsByBucket[ItemSpreadBucket.LOCKED] = totalByType
                .mapValuesTo(mutableMapOf()) { (type, total) -> (total - (startedByType[type] ?: 0)).coerceAtLeast(0) }

            fun bucketTotal(bucket: ItemSpreadBucket) = countsByBucket[bucket]?.values?.sum() ?: 0

            ItemSpread(
                lockedCount = bucketTotal(ItemSpreadBucket.LOCKED),
                apprenticeCount = bucketTotal(ItemSpreadBucket.APPRENTICE),
                guruCount = bucketTotal(ItemSpreadBucket.GURU),
                masterCount = bucketTotal(ItemSpreadBucket.MASTER),
                enlightenedCount = bucketTotal(ItemSpreadBucket.ENLIGHTENED),
                burnedCount = bucketTotal(ItemSpreadBucket.BURNED),
                countsByType = countsByBucket.mapValues { it.value.toMap() }
            )
        }.flowOn(defaultDispatcher)

    fun observeLevelProgress(level: Int): Flow<LevelProgress> =
        assignmentDao.observeLevelProgressItemRows(level).map { rows ->
            val bySubjectType = rows.groupBy { SubjectType.fromWkString(it.subjectType) }
            val breakdown = listOf(SubjectType.RADICAL, SubjectType.KANJI, SubjectType.VOCABULARY).map { type ->
                val items = bySubjectType[type].orEmpty().map { row ->
                    LevelItem(
                        subjectId = row.subjectId,
                        subjectType = type,
                        characters = row.characters,
                        display = row.characters ?: row.slug,
                        passed = row.passedAt != null,
                        srsStage = SrsStage.fromRaw(row.srsStage),
                        characterImageUrl = row.characterImageUrl
                    )
                }
                SubjectTypeProgress(subjectType = type, items = items)
            }
            LevelProgress(level = level, breakdown = breakdown)
        }.flowOn(defaultDispatcher)

    fun observeItemsSeenCount(): Flow<Int> = assignmentDao.observeItemsSeenCount()

    /**
     * Count of assignments started on the current local calendar date — used for the "lessons done
     * today" indicator.
     *
     * The counting is done in SQL ([AssignmentDao.observeStartedSinceCount]) from a local-midnight
     * cutoff, rather than by loading every started timestamp and parsing each one in Kotlin. The
     * ticker is still what makes the value roll over at local midnight: the DAO flow only re-emits on
     * a write, so without it the count would stay frozen at yesterday's until some unrelated write
     * happened to land in the assignments table.
     *
     * [flatMapLatest] rather than `combine` because the cutoff is a query *parameter*: a day change
     * has to re-subscribe with the new cutoff, not just recompute from a stale result.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLessonsCompletedToday(): Flow<Int> = dailyRolloverTicks()
        .flatMapLatest { today ->
            assignmentDao.observeStartedSinceCount(localMidnightIso(today))
        }
        .flowOn(defaultDispatcher)

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
        }.flowOn(defaultDispatcher)
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
