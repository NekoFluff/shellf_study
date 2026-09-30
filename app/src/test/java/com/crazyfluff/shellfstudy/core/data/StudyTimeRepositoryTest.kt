package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.FakeStudyTimeDao
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.fakes.FakeReviewStatisticDao
import com.crazyfluff.shellfstudy.fakes.FakeAssignmentDao
import com.crazyfluff.shellfstudy.fakes.buildTestStudyTimeRepository
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyKind
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeRepository
import com.crazyfluff.shellfstudy.shared.data.studytime.StudyTimeWindow
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.time.Clock
import kotlin.time.Instant

class StudyTimeRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val dao = FakeStudyTimeDao()
    private lateinit var dataStore: DataStore<Preferences>

    /** Noon UTC, so "today" is the same date in any zone the test machine runs in within ±11h. */
    private val now = Instant.parse("2026-09-24T12:00:00Z")
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }

    private fun TestScope.createRepository(): StudyTimeRepository {
        dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempFolder.newFile("study_time.preferences_pb") }
        )
        val dispatcher = StandardTestDispatcher(testScheduler)
        return buildTestStudyTimeRepository(
            dao, dataStore, backgroundScope, dispatcher,
            clock = fixedClock,
            minSegmentMs = StudyTimeRepository.MIN_SEGMENT_MS
        )
    }

    private val startMs = now.toEpochMilliseconds() - 60 * MINUTE

    /** The insert follows a DataStore read (the cached level) on DataStore's own thread, so it isn't
     *  drained by the test scheduler — wait for the row itself. */
    private suspend fun awaitStored() = dao.observeAll().first { it.isNotEmpty() }

    @Test
    fun `record stores the stretch stamped with the cached WaniKani level`() = runTest {
        val repository = createRepository()
        DashboardCacheRepository(dataStore)
            .save(username = "koichi", level = 12, lessonCount = 0, reviewCount = 0, syncedAtMillis = 0L)

        repository.record(StudyKind.REVIEW, startMs, startMs + 5 * MINUTE, itemsCompleted = 20)
        val stored = awaitStored().single()
        assertThat(stored.kind).isEqualTo("REVIEW")
        assertThat(stored.durationMs).isEqualTo(5 * MINUTE)
        assertThat(stored.level).isEqualTo(12)
        assertThat(stored.itemsAnswered).isEqualTo(20)
    }

    @Test
    fun `record leaves the level empty before the dashboard has cached one`() = runTest {
        val repository = createRepository()

        repository.record(StudyKind.LESSON, startMs, startMs + MINUTE, itemsCompleted = 0)
        assertThat(awaitStored().single().level).isNull()
    }

    @Test
    fun `record drops stretches too short to be study and negative ones from a clock change`() = runTest {
        val repository = createRepository()

        repository.record(StudyKind.REVIEW, startMs, startMs + 500, itemsCompleted = 0)
        repository.record(StudyKind.REVIEW, startMs, startMs - MINUTE, itemsCompleted = 0)
        // A valid stretch recorded after them: once it lands, the dropped ones would have too.
        repository.record(StudyKind.LESSON, startMs, startMs + MINUTE, itemsCompleted = 0)

        assertThat(awaitStored().map { it.kind }).containsExactly("LESSON")
    }

    @Test
    fun `record caps an implausibly long stretch`() = runTest {
        val repository = createRepository()

        repository.record(StudyKind.REVIEW, startMs, startMs + 30 * 60 * MINUTE, itemsCompleted = 1)
        assertThat(awaitStored().single().durationMs).isEqualTo(StudyTimeRepository.MAX_SEGMENT_MS)
    }

    @Test
    fun `overview reports today's split against the goal from settings`() = runTest {
        val repository = createRepository()
        SettingsRepository(dataStore).setDailyStudyMinutesGoal(20)
        repository.record(StudyKind.REVIEW, startMs, startMs + 6 * MINUTE, itemsCompleted = 36)
        repository.record(StudyKind.LESSON, startMs + 10 * MINUTE, startMs + 14 * MINUTE, itemsCompleted = 2)
        testScheduler.advanceUntilIdle()

        repository.observeOverview().test {
            var overview = awaitItem()
            while (overview.goalMs != 20 * MINUTE || overview.today.totalMs != 10 * MINUTE) overview = awaitItem()
            assertThat(overview.today.reviewMs).isEqualTo(6 * MINUTE)
            assertThat(overview.today.lessonMs).isEqualTo(4 * MINUTE)
            assertThat(overview.goalFraction).isEqualTo(0.5f)
            assertThat(overview.pace.reviewMsPerItem).isEqualTo(10_000L)
            assertThat(overview.pace.lessonMsPerItem).isEqualTo(2 * MINUTE)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `report follows the selected window`() = runTest {
        val repository = createRepository()

        repository.observeReport(flowOf(StudyTimeWindow.MONTH)).test {
            val report = awaitItem()
            assertThat(report.window).isEqualTo(StudyTimeWindow.MONTH)
            assertThat(report.buckets).hasSize(30)
            assertThat(report.overview.hasAnyData).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `report prices the whole WaniKani history, radicals and kana-only words at half`() = runTest {
        dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempFolder.newFile("lifetime.preferences_pb") }
        )
        val reviewStatisticDao = FakeReviewStatisticDao().apply {
            upsertAll(
                listOf(
                    statistic(id = 1, type = "kanji", meaningCorrect = 30),
                    statistic(id = 2, type = "vocabulary", meaningCorrect = 20),
                    statistic(id = 3, type = "radical", meaningCorrect = 10),
                    statistic(id = 4, type = "kana_vocabulary", meaningCorrect = 6)
                )
            )
        }
        val assignmentDao = FakeAssignmentDao().apply {
            upsertAll(
                (1L..4L).map {
                    AssignmentEntity(id = it, subjectId = it, subjectType = "kanji", srsStage = 5,
                        createdAt = "2026-01-01T00:00:00Z", startedAt = "2026-01-02T00:00:00Z", hidden = false)
                }
            )
        }
        val repository = buildTestStudyTimeRepository(
            dao, dataStore, backgroundScope, StandardTestDispatcher(testScheduler),
            reviewStatisticDao = reviewStatisticDao,
            assignmentDao = assignmentDao,
            clock = fixedClock
        )

        repository.observeReport(flowOf(StudyTimeWindow.WEEK)).test {
            val lifetime = awaitItem().lifetime!!
            assertThat(lifetime.twoQuestionReviews).isEqualTo(50)
            assertThat(lifetime.oneQuestionReviews).isEqualTo(16)
            assertThat(lifetime.lessons).isEqualTo(4)
            // No recorded pace yet, so the defaults: 20s per two-question review, half that for one.
            assertThat(lifetime.reviewMs).isEqualTo(50 * 20_000L + 16 * 10_000L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun statistic(id: Long, type: String, meaningCorrect: Int) = ReviewStatisticEntity(
        id = id, subjectId = id, subjectType = type,
        meaningCorrect = meaningCorrect, meaningIncorrect = 3, meaningMaxStreak = 1, meaningCurrentStreak = 1,
        readingCorrect = meaningCorrect, readingIncorrect = 2, readingMaxStreak = 1, readingCurrentStreak = 1,
        percentageCorrect = 90, hidden = false
    )

    private companion object {
        const val MINUTE = 60_000L
    }
}
