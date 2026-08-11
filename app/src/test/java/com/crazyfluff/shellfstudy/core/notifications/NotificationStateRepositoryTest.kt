package com.crazyfluff.shellfstudy.core.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NotificationStateRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): NotificationStateRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return NotificationStateRepository(dataStore)
    }

    @Test
    fun `state emits defaults when nothing stored`() = runTest {
        val repository = createRepository()
        repository.state.test {
            val state = awaitItem()
            assertThat(state).isEqualTo(NotificationState())
        }
    }

    @Test
    fun `updateReviewWatermark and updateLessonWatermark persist independently`() = runTest {
        val repository = createRepository()

        repository.updateReviewWatermark(12)
        repository.updateLessonWatermark(3)

        repository.state.test {
            val state = awaitItem()
            assertThat(state.lastNotifiedReviewCount).isEqualTo(12)
            assertThat(state.lastNotifiedLessonCount).isEqualTo(3)
        }
    }

    @Test
    fun `recordBacklogNotified persists a round-trippable instant`() = runTest {
        val repository = createRepository()
        val now = Instant.parse("2026-08-10T20:00:00Z")

        repository.recordBacklogNotified(now)

        repository.state.test {
            assertThat(awaitItem().lastBacklogNotifiedAt).isEqualTo(now)
        }
    }

    @Test
    fun `updateMilestoneWatermark clears the level when null and marks initialized`() = runTest {
        val repository = createRepository()

        repository.updateMilestoneWatermark(level = 10, burnedCount = 25)
        repository.state.test {
            val state = awaitItem()
            assertThat(state.lastNotifiedLevel).isEqualTo(10)
            assertThat(state.lastNotifiedBurnedCount).isEqualTo(25)
            assertThat(state.milestonesInitialized).isTrue()
        }

        repository.updateMilestoneWatermark(level = null, burnedCount = 25)
        repository.state.test {
            assertThat(awaitItem().lastNotifiedLevel).isNull()
        }
    }

    @Test
    fun `recordStreakReminderSent persists a round-trippable date`() = runTest {
        val repository = createRepository()
        val date = LocalDate.of(2026, 8, 10)

        repository.recordStreakReminderSent(date)

        repository.state.test {
            assertThat(awaitItem().lastStreakReminderSentDate).isEqualTo(date)
        }
    }

    @Test
    fun `clear resets all fields back to defaults`() = runTest {
        val repository = createRepository()
        repository.updateReviewWatermark(12)
        repository.updateLessonWatermark(3)
        repository.recordBacklogNotified(Instant.now())
        repository.updateMilestoneWatermark(level = 10, burnedCount = 25)
        repository.recordStreakReminderSent(LocalDate.now())

        repository.clear()

        repository.state.test {
            assertThat(awaitItem()).isEqualTo(NotificationState())
        }
    }
}
