package com.crazyfluff.shellfstudy.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.crazyfluff.shellfstudy.shared.data.LessonSessionRepository
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonPhase
import com.crazyfluff.shellfstudy.shared.data.PersistedLessonSession
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.PersistedReviewSession
import com.crazyfluff.shellfstudy.shared.data.ReviewSessionRepository
import com.crazyfluff.shellfstudy.shared.di.coroutineScopeModule
import com.crazyfluff.shellfstudy.shared.di.repositoryModule
import com.crazyfluff.shellfstudy.shared.network.waniKaniJson
import com.crazyfluff.shellfstudy.shared.session.LessonSessionController
import com.crazyfluff.shellfstudy.shared.session.ReviewSessionController
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

/**
 * Starts the real [coroutineScopeModule] + [repositoryModule] and resolves the two session
 * controllers the way the app does, to pin down a DI collision that no static/ViewModel-level test
 * could catch: Koin indexes definitions by the *erased* class only, so two `single {
 * QuizSessionController<T>(...) }` registrations share one key and the later silently overrides the
 * earlier — the lesson singleton then resolves to the review store, and the lesson screen's first
 * persist crashes with a ClassCastException (lesson payload hitting ReviewSessionRepository.save).
 * Each feature is therefore wired as its own concrete controller subclass; this test proves the two
 * singletons are distinct beans and that each one's writes land in its own feature's repository.
 */
class SessionControllerDiTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var koin: Koin

    @Before
    fun setUp() {
        koin = startKoin {
            modules(
                // Stand-ins for the platform-provided beans the repositories need; everything else
                // under test comes from the real shared modules.
                module {
                    single { waniKaniJson() }
                    single<DataStore<Preferences>> {
                        PreferenceDataStoreFactory.create(
                            produceFile = { tempFolder.newFile("koin-test.preferences_pb") }
                        )
                    }
                },
                coroutineScopeModule,
                repositoryModule,
            )
        }.koin
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `lesson and review controllers are distinct beans, each persisting only to its own feature's repository`() = runTest {
        val lessonController = koin.get<LessonSessionController>()
        val reviewController = koin.get<ReviewSessionController>()
        val lessonRepository = koin.get<LessonSessionRepository>()
        val reviewRepository = koin.get<ReviewSessionRepository>()

        // Distinct singletons — not the same bean under two generic spellings.
        assertThat(lessonController).isNotSameInstanceAs(reviewController)

        // A lesson persist lands in the lesson repository and nowhere else.
        lessonController.begin()
        lessonController.persist(
            PersistedLessonSession(phase = PersistedLessonPhase.STUDY, studyAssignmentIds = listOf(1L))
        )
        assertThat(lessonRepository.load()).isNotNull()
        assertThat(reviewRepository.load()).isNull()

        // A review persist lands in the review repository, leaving the lesson record untouched.
        reviewController.begin()
        reviewController.persist(
            PersistedReviewSession(
                queue = listOf(PersistedQuestion(1L, "MEANING")),
                progress = emptyList(),
                totalQuestions = 1
            )
        )
        assertThat(reviewRepository.load()).isNotNull()
        assertThat(lessonRepository.load()).isNotNull()
    }
}
