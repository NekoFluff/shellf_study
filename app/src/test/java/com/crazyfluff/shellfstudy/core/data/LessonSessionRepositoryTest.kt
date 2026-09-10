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
        sessionAssignmentIds = listOf(1L, 2L),
        batchSize = 5,
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
    fun `load self-heals a QUIZ snapshot with an empty queue, treating it as unresumable`() = runTest {
        // A quiz snapshot with nothing left to answer was never meant to be resumed — load() must not
        // hand this back, even though its plan is intact.
        val repository = createRepository()
        repository.save(sampleSession.copy(quizQueue = emptyList()))

        assertThat(repository.load()).isNull()
        // The corrupted record is also wiped, so it doesn't keep reappearing on every future load().
        repository.hasActiveSession.test { assertThat(awaitItem()).isFalse() }
    }

    @Test
    fun `load returns null for a snapshot with no plan at all, treating it as unresumable`() = runTest {
        val repository = createRepository()
        repository.save(PersistedLessonSession(phase = PersistedLessonPhase.STUDY, studyAssignmentIds = emptyList()))

        assertThat(repository.load()).isNull()
        repository.hasActiveSession.test { assertThat(awaitItem()).isFalse() }
    }

    @Test
    fun `load returns a STUDY snapshot that has a plan`() = runTest {
        val repository = createRepository()
        val studySession = PersistedLessonSession(
            phase = PersistedLessonPhase.STUDY,
            sessionAssignmentIds = listOf(1L, 2L, 3L),
            batchSize = 2,
            batchIndex = 1,
            studyIndex = 1
        )
        repository.save(studySession)

        assertThat(repository.load()).isEqualTo(studySession)
    }

    @Test
    fun `load returns a parked CHECKPOINT snapshot even though its queue is empty`() = runTest {
        // The normal shape of "Finish for now" at a batch checkpoint — a resumable session with no
        // question pending, which the empty-queue rule above must not mistake for corruption.
        val repository = createRepository()
        val checkpoint = PersistedLessonSession(
            phase = PersistedLessonPhase.CHECKPOINT,
            sessionAssignmentIds = listOf(1L, 2L, 3L),
            batchSize = 2,
            batchIndex = 1
        )
        repository.save(checkpoint)

        assertThat(repository.load()).isEqualTo(checkpoint)
    }

    @Test
    fun `load migrates a pre-plan STUDY snapshot into a single-batch plan`() = runTest {
        // Written by a build from before session plans existed: the selected batch was the whole
        // session, so it becomes a one-batch plan rather than the session being dropped on upgrade.
        val repository = createRepository()
        repository.save(
            PersistedLessonSession(
                phase = PersistedLessonPhase.STUDY,
                studyAssignmentIds = listOf(7L, 8L),
                studyIndex = 1
            )
        )

        val loaded = repository.load()
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.sessionAssignmentIds).containsExactly(7L, 8L).inOrder()
        assertThat(loaded.batchSize).isEqualTo(2)
        assertThat(loaded.batchIndex).isEqualTo(0)
        assertThat(loaded.studyIndex).isEqualTo(1)
    }

    @Test
    fun `load migrates a pre-plan QUIZ snapshot into a plan built from its queue and progress`() = runTest {
        val repository = createRepository()
        repository.save(
            PersistedLessonSession(
                phase = PersistedLessonPhase.QUIZ,
                quizQueue = listOf(
                    PersistedQuestion(assignmentId = 4, questionType = "MEANING"),
                    PersistedQuestion(assignmentId = 5, questionType = "READING")
                ),
                progress = listOf(
                    com.crazyfluff.shellfstudy.shared.data.PersistedItemProgress(
                        assignmentId = 4, meaningDone = true, readingDone = false,
                        hadIncorrectMeaning = false, hadIncorrectReading = false
                    )
                ),
                totalQuizCount = 3
            )
        )

        val loaded = repository.load()
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.sessionAssignmentIds).containsExactly(4L, 5L).inOrder()
        assertThat(loaded.batchSize).isEqualTo(2)
    }
}
