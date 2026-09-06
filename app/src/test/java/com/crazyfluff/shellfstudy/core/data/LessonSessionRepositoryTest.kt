package com.crazyfluff.shellfstudy.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LessonSessionRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>

    private fun createRepository(): LessonSessionRepository {
        dataStore = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        return LessonSessionRepository(dataStore, Json)
    }

    private val sampleSession = PersistedLessonSession(
        quizQueue = listOf(PersistedQuestion(assignmentId = 1, questionType = "MEANING")),
        totalQuizCount = 2
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
        // Same literal key LessonSessionRepository.kt persists under — writing directly to the
        // DataStore is the only way to get malformed data into place for this fallback branch.
        val sessionKey = stringPreferencesKey("persisted_lesson_session")
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
    fun `load self-heals an empty-queue QUIZ snapshot, treating it as unresumable`() = runTest {
        // A save racing a completion-time clear, or a stale phase mismatch (see
        // PersistedLessonSession.phase's doc comment), can leave behind an empty-queue QUIZ snapshot
        // that was never meant to be resumed — load() must not hand this back.
        val repository = createRepository()
        repository.save(PersistedLessonSession(phase = PersistedLessonPhase.QUIZ, quizQueue = emptyList()))

        assertThat(repository.load()).isNull()
        // The corrupted record is also wiped, so it doesn't keep reappearing on every future load().
        repository.hasActiveSession.test { assertThat(awaitItem()).isFalse() }
    }

    @Test
    fun `load self-heals a STUDY snapshot with no study items, treating it as unresumable`() = runTest {
        val repository = createRepository()
        repository.save(PersistedLessonSession(phase = PersistedLessonPhase.STUDY, studyAssignmentIds = emptyList()))

        assertThat(repository.load()).isNull()
        repository.hasActiveSession.test { assertThat(awaitItem()).isFalse() }
    }

    @Test
    fun `load returns a STUDY snapshot that has study items`() = runTest {
        val repository = createRepository()
        val studySession = PersistedLessonSession(
            phase = PersistedLessonPhase.STUDY,
            studyAssignmentIds = listOf(1L, 2L),
            studyIndex = 1
        )
        repository.save(studySession)

        assertThat(repository.load()).isEqualTo(studySession)
    }
}
