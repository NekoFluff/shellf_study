package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.OutboxRepository
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.fakes.FakeOutboxDao
import com.crazyfluff.shellfstudy.fakes.FakeOutboxSyncScheduler
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutboxRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun buildRepository(outboxDao: FakeOutboxDao, scheduler: FakeOutboxSyncScheduler): OutboxRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return OutboxRepository(outboxDao, scheduler, dataStore)
    }

    @Test
    fun `enqueueReviewSubmission inserts a row with grade-derived incorrect counts and requests a sync`() = runTest {
        val outboxDao = FakeOutboxDao()
        val scheduler = FakeOutboxSyncScheduler()
        val repository = buildRepository(outboxDao, scheduler)

        repository.enqueueReviewSubmission(assignmentId = 1, subjectId = 2, grade = ReviewGrade(meaningCorrect = true, readingCorrect = false))

        val rows = outboxDao.allReviewSubmissions()
        assertThat(rows).hasSize(1)
        assertThat(rows.first().assignmentId).isEqualTo(1)
        assertThat(rows.first().subjectId).isEqualTo(2)
        assertThat(rows.first().incorrectMeaningAnswers).isEqualTo(0)
        assertThat(rows.first().incorrectReadingAnswers).isEqualTo(1)
        assertThat(scheduler.requestCount).isEqualTo(1)
    }

    @Test
    fun `enqueueLessonStart inserts a row and requests a sync`() = runTest {
        val outboxDao = FakeOutboxDao()
        val scheduler = FakeOutboxSyncScheduler()
        val repository = buildRepository(outboxDao, scheduler)

        repository.enqueueLessonStart(assignmentId = 5, subjectId = 6)

        val rows = outboxDao.allLessonStarts()
        assertThat(rows).hasSize(1)
        assertThat(rows.first().assignmentId).isEqualTo(5)
        assertThat(scheduler.requestCount).isEqualTo(1)
    }

    @Test
    fun `observePendingCount sums pending reviews and lesson starts`() = runTest {
        val outboxDao = FakeOutboxDao()
        val repository = buildRepository(outboxDao, FakeOutboxSyncScheduler())

        repository.observePendingCount().test {
            assertThat(awaitItem()).isEqualTo(0)
            repository.enqueueReviewSubmission(1, 2, ReviewGrade(true, true))
            assertThat(awaitItem()).isEqualTo(1)
            repository.enqueueLessonStart(3, 4)
            assertThat(awaitItem()).isEqualTo(2)
        }
    }

    @Test
    fun `blockedOnAuth round-trips through DataStore`() = runTest {
        val repository = buildRepository(FakeOutboxDao(), FakeOutboxSyncScheduler())

        repository.blockedOnAuth.test {
            assertThat(awaitItem()).isFalse()
            repository.setBlockedOnAuth(true)
            assertThat(awaitItem()).isTrue()
            repository.setBlockedOnAuth(false)
            assertThat(awaitItem()).isFalse()
        }
    }
}
