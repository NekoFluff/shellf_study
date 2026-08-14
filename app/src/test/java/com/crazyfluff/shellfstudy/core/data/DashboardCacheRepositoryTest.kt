package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.DashboardCacheRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DashboardCacheRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createRepository(): DashboardCacheRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return DashboardCacheRepository(dataStore)
    }

    @Test
    fun `cachedSummary emits null when nothing stored`() = runTest {
        val repository = createRepository()
        repository.cachedSummary.test {
            assertThat(awaitItem()).isNull()
        }
    }

    @Test
    fun `save persists username, level, counts, and sync time together`() = runTest {
        val repository = createRepository()

        repository.save(
            username = "durtle_fan",
            level = 12,
            lessonCount = 5,
            reviewCount = 23,
            syncedAtMillis = 1_700_000_000_000L
        )

        repository.cachedSummary.test {
            val summary = awaitItem()
            assertThat(summary).isNotNull()
            assertThat(summary!!.username).isEqualTo("durtle_fan")
            assertThat(summary.level).isEqualTo(12)
            assertThat(summary.lessonCount).isEqualTo(5)
            assertThat(summary.reviewCount).isEqualTo(23)
            assertThat(summary.lastSyncedAtMillis).isEqualTo(1_700_000_000_000L)
        }
    }

    @Test
    fun `a later save overwrites the previous cached values`() = runTest {
        val repository = createRepository()

        repository.save(username = "old_name", level = 1, lessonCount = 1, reviewCount = 1, syncedAtMillis = 1L)
        repository.save(username = "new_name", level = 2, lessonCount = 4, reviewCount = 9, syncedAtMillis = 2L)

        repository.cachedSummary.test {
            val summary = awaitItem()
            assertThat(summary!!.username).isEqualTo("new_name")
            assertThat(summary.level).isEqualTo(2)
            assertThat(summary.lessonCount).isEqualTo(4)
            assertThat(summary.reviewCount).isEqualTo(9)
            assertThat(summary.lastSyncedAtMillis).isEqualTo(2L)
        }
    }
}
