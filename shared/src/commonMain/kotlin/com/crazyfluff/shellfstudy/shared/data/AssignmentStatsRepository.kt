package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.ItemSpread
import com.crazyfluff.shellfstudy.shared.data.model.ItemSpreadBucket
import com.crazyfluff.shellfstudy.shared.data.model.foldKana
import com.crazyfluff.shellfstudy.shared.data.model.LevelItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelProgress
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpPath
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecast
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastBucket
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastWindow
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.SrsStageCalculator
import com.crazyfluff.shellfstudy.shared.data.model.SubjectTypeProgress
import com.crazyfluff.shellfstudy.shared.database.AssignmentDao
import com.crazyfluff.shellfstudy.shared.database.SrsSystemDao
import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectDao
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

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

/**
 * Read models over the assignment mirror for the dashboard's cards and the lesson picker — review
 * forecast, item spread, level progress and today's lesson count. Read-only: syncing and grading
 * writes live in [AssignmentRepository].
 */
class AssignmentStatsRepository(
    private val assignmentDao: AssignmentDao,
    private val subjectDao: SubjectDao,
    private val srsSystemDao: SrsSystemDao,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
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

    /**
     * The fastest possible level-up for [level] — see [LevelUpPathCalculator].
     *
     * Recomputed on every hour boundary as well as on writes: an overdue review is assumed to happen
     * "now", so the whole path slides later each hour it's left undone even though nothing in the
     * database changes.
     */
    fun observeLevelUpPath(level: Int): Flow<LevelUpPath> =
        combine(
            hourlyRolloverTicks(),
            assignmentDao.observeLevelUpPathRows(level),
            srsSystemDao.observeAll()
        ) { now, rows, systems ->
            LevelUpPathCalculator.calculate(rows, systems.associateBy { it.id }, now)
        }.flowOn(defaultDispatcher)
}
