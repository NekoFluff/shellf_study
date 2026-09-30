package com.crazyfluff.shellfstudy.shared.data.studytime

import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.dailyRolloverTicks
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeDao
import com.crazyfluff.shellfstudy.shared.database.studytime.StudyTimeSegmentEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Records active lesson/review time and serves the aggregated views of it. The only store of this
 * data: WaniKani's API doesn't know how long anyone studied, so history begins when the app starts
 * recording.
 */
class StudyTimeRepository(
    private val studyTimeDao: StudyTimeDao,
    private val settingsRepository: SettingsRepository,
    private val dashboardCacheRepository: DashboardCacheRepository,
    private val applicationScope: CoroutineScope,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.System,
    /** Stretches shorter than this are dropped as noise — a tap through a screen, not study.
     *  ViewModel tests lower it, since their sessions last milliseconds of real time. */
    private val minSegmentMs: Long = MIN_SEGMENT_MS,
    /** Re-emits at local midnight so "today" rolls over without a write — see dailyRolloverTicks. */
    private val dayTicks: () -> Flow<Any> = ::dailyRolloverTicks
) {
    /**
     * Stores one stretch of session time. Fire-and-forget on [applicationScope], because the caller is
     * often a ViewModel's `onCleared`, where its own scope is already cancelled.
     *
     * The session clock is wall-clock time, so a device clock change mid-session could produce a
     * negative or absurd stretch: anything under [minSegmentMs] is dropped and anything longer than
     * [MAX_SEGMENT_MS] is capped.
     */
    fun record(kind: StudyKind, startMs: Long, endMs: Long, itemsCompleted: Int) {
        val durationMs = (endMs - startMs).coerceAtMost(MAX_SEGMENT_MS)
        if (durationMs < minSegmentMs || durationMs < 0L) return
        applicationScope.launch {
            studyTimeDao.insert(
                StudyTimeSegmentEntity(
                    kind = kind.name,
                    startedAtMs = startMs,
                    durationMs = durationMs,
                    level = dashboardCacheRepository.cachedSummary.first()?.level,
                    itemsAnswered = itemsCompleted.coerceAtLeast(0)
                )
            )
        }
    }

    fun observeOverview(): Flow<StudyTimeOverview> =
        combine(segments(), goalMs(), dayTicks()) { segments, goalMs, _ ->
            val zone = TimeZone.currentSystemDefault()
            StudyTimeAggregator.overview(segments, todayIn(zone), zone, goalMs)
        }.flowOn(defaultDispatcher)

    fun observeReport(window: Flow<StudyTimeWindow>): Flow<StudyTimeReport> =
        combine(
            segments(),
            goalMs(),
            dashboardCacheRepository.cachedSummary.map { it?.level }.distinctUntilChanged(),
            window,
            dayTicks()
        ) { segments, goalMs, level, selectedWindow, _ ->
            val zone = TimeZone.currentSystemDefault()
            StudyTimeAggregator.report(segments, todayIn(zone), zone, selectedWindow, goalMs, level)
        }.flowOn(defaultDispatcher)

    private fun segments(): Flow<List<StudySegment>> =
        studyTimeDao.observeAll().map { rows -> rows.mapNotNull { it.toSegment() } }

    private fun goalMs(): Flow<Long> =
        settingsRepository.settings.map { it.dailyStudyMinutesGoal * MS_PER_MINUTE }.distinctUntilChanged()

    private fun todayIn(zone: TimeZone) = clock.todayIn(zone)

    companion object {
        const val MIN_SEGMENT_MS = 1_000L
        const val MAX_SEGMENT_MS = 6 * 60 * 60 * 1_000L
        private const val MS_PER_MINUTE = 60_000L
    }
}

/** Rows with a kind this build doesn't know are skipped rather than failing the whole read. */
private fun StudyTimeSegmentEntity.toSegment(): StudySegment? {
    val kind = StudyKind.entries.firstOrNull { it.name == kind } ?: return null
    return StudySegment(kind, startedAtMs, durationMs, level, itemsAnswered)
}
