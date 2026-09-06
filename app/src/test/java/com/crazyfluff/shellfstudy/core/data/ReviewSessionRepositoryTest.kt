package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ReviewSessionRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>

    private fun createRepository(): ReviewSessionRepository {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return ReviewSessionRepository(dataStore, Json)
    }

    private val sampleSession = PersistedReviewSession(
        queue = listOf(PersistedQuestion(assignmentId = 1, questionType = "MEANING")),
        progress = listOf(
            PersistedItemProgress(
                assignmentId = 1,
                meaningDone = false,
                readingDone = false,
                hadIncorrectMeaning = false,
                hadIncorrectReading = false
            )
        ),
        totalQuestions = 2
    )

    @Test
    fun `load returns null when nothing has been saved`() = runTest {
        val repository = createRepository()

        assertThat(repository.load()).isNull()
    }

    @Test
    fun `save then load round-trips the session`() = runTest {
        val repository = createRepository()

        repository.save(sampleSession)

        assertThat(repository.load()).isEqualTo(sampleSession)
    }

    @Test
    fun `clear removes the saved session`() = runTest {
        val repository = createRepository()
        repository.save(sampleSession)

        repository.clear()

        assertThat(repository.load()).isNull()
    }

    @Test
    fun `load returns null when the persisted value is corrupted JSON`() = runTest {
        val repository = createRepository()
        // Same literal key ReviewSessionRepository.kt persists under — writing directly to the
        // DataStore is the only way to get malformed data into place for this fallback branch.
        val sessionKey = stringPreferencesKey("persisted_review_session")
        dataStore.edit { prefs -> prefs[sessionKey] = "not valid json" }

        assertThat(repository.load()).isNull()
    }

    @Test
    fun `hasActiveSession is false when nothing is saved`() = runTest {
        val repository = createRepository()

        repository.hasActiveSession.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `hasActiveSession is true once a session is saved`() = runTest {
        val repository = createRepository()
        repository.save(sampleSession)

        repository.hasActiveSession.test {
            assertThat(awaitItem()).isTrue()
        }
    }

    @Test
    fun `hasActiveSession is false again after clear`() = runTest {
        val repository = createRepository()
        repository.save(sampleSession)
        repository.clear()

        repository.hasActiveSession.test {
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `load self-heals an empty-queue snapshot with no pending submission, treating it as unresumable`() = runTest {
        // A save racing a completion-time clear (see QuizSessionController) can leave behind an
        // empty-queue snapshot that was never meant to be resumed — load() must not hand this back.
        val repository = createRepository()
        repository.save(PersistedReviewSession(queue = emptyList(), progress = emptyList(), totalQuestions = 1))

        assertThat(repository.load()).isNull()
        // The corrupted record is also wiped, so it doesn't keep reappearing on every future load().
        repository.hasActiveSession.test { assertThat(awaitItem()).isFalse() }
    }

    @Test
    fun `load still returns an empty-queue snapshot that carries a pending submission`() = runTest {
        // An empty queue with a pending submission is a legitimate, resumable state — it means the
        // session reached its last question but the user hadn't tapped Continue yet, so the pending
        // WaniKani submission still needs to be committed on resume. See
        // PersistedReviewSession.pendingSubmissionAssignmentId's doc comment.
        val repository = createRepository()
        val pendingSession = PersistedReviewSession(
            queue = emptyList(),
            progress = emptyList(),
            totalQuestions = 1,
            pendingSubmissionAssignmentId = 42L
        )
        repository.save(pendingSession)

        assertThat(repository.load()).isEqualTo(pendingSession)
    }
}
