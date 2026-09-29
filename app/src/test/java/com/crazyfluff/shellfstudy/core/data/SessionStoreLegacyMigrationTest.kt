package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.fakes.FakeSessionDao
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Covers the one-off move of an in-progress session out of the shared preferences DataStore and into
 * its own Room database — see [ReviewSessionRepository] and
 * [com.crazyfluff.shellfstudy.shared.data.RoomSessionStore].
 *
 * This path is worth its own tests because every failure mode in it is silent. A migration that
 * copies nothing makes an in-progress session vanish; one that leaves the legacy key behind pins
 * `hasActiveSession` to true forever, which shows up not as a crash but as a "Resume" card that opens
 * a session which then loads as null; and one that clears the legacy key before the Room write lands
 * loses the session outright. None of those would fail any existing test, because the existing tests
 * all start from an empty DataStore.
 */
class SessionStoreLegacyMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val legacyKey = stringPreferencesKey("persisted_review_session")

    private fun newDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { tempFolder.newFile("legacy-${System.nanoTime()}.preferences_pb") }
    )

    private val sampleSession = PersistedReviewSession(
        queue = listOf(PersistedQuestion(assignmentId = 7, questionType = "MEANING")),
        progress = emptyList(),
        totalQuestions = 1
    )

    @Test
    fun `a session left in DataStore is still loadable after the move to Room`() = runTest {
        val sessionDao = FakeSessionDao()
        val dataStore = newDataStore()
        val json = Json

        // What a previous version left behind: the serialized payload under the old preferences key.
        dataStore.edit { it[legacyKey] = json.encodeToString(PersistedReviewSession.serializer(), sampleSession) }

        val repository = ReviewSessionRepository(sessionDao, dataStore, json)

        val loaded = repository.load()
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.queue.map { it.assignmentId }).containsExactly(7L)
    }

    @Test
    fun `migration clears the legacy key so hasActiveSession cannot stay stuck true`() = runTest {
        val sessionDao = FakeSessionDao()
        val dataStore = newDataStore()
        val json = Json
        dataStore.edit { it[legacyKey] = json.encodeToString(PersistedReviewSession.serializer(), sampleSession) }

        val repository = ReviewSessionRepository(sessionDao, dataStore, json)

        repository.load() // performs the migration

        assertThat(dataStore.data.first()[legacyKey]).isNull()
    }

    @Test
    fun `after migration, clearing the session reports no active session`() = runTest {
        val sessionDao = FakeSessionDao()
        val dataStore = newDataStore()
        val json = Json
        dataStore.edit { it[legacyKey] = json.encodeToString(PersistedReviewSession.serializer(), sampleSession) }

        val repository = ReviewSessionRepository(sessionDao, dataStore, json)
        repository.load()

        repository.clear()

        // The legacy key must not resurrect the session, and the Room tombstone must read as absent.
        repository.hasActiveSession.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(repository.load()).isNull()
    }

    @Test
    fun `a session saved after the move never touches DataStore`() = runTest {
        val sessionDao = FakeSessionDao()
        val dataStore = newDataStore()
        val json = Json
        val repository = ReviewSessionRepository(sessionDao, dataStore, json)

        repository.save(sampleSession)

        assertThat(dataStore.data.first().asMap()).isEmpty()
        assertThat(repository.load()).isNotNull()
    }

    @Test
    fun `migration is idempotent and does not overwrite a newer Room session`() = runTest {
        val sessionDao = FakeSessionDao()
        val dataStore = newDataStore()
        val json = Json
        dataStore.edit { it[legacyKey] = json.encodeToString(PersistedReviewSession.serializer(), sampleSession) }

        val repository = ReviewSessionRepository(sessionDao, dataStore, json)
        repository.load() // migrates, clearing the key

        val newer = sampleSession.copy(queue = listOf(PersistedQuestion(assignmentId = 99, questionType = "READING")))
        repository.save(newer)

        // A second load runs the migration check again; with the legacy key gone it must be a no-op.
        val loaded = repository.load()
        assertThat(loaded!!.queue.map { it.assignmentId }).containsExactly(99L)
    }
}
