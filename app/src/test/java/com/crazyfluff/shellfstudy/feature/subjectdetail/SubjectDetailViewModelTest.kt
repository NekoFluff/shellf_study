package com.crazyfluff.shellfstudy.feature.subjectdetail

import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailUiState
import com.crazyfluff.shellfstudy.shared.feature.subjectdetail.SubjectDetailViewModel
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.database.AssignmentEntity
import com.crazyfluff.shellfstudy.shared.database.ReviewStatisticEntity
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.shared.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioMetadataData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.fakes.FakeStrokeOrderRepository
import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubjectDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var viewModel: SubjectDetailViewModel
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var audioPlayer: FakePronunciationAudioPlayer
    private lateinit var strokeOrderRepository: FakeStrokeOrderRepository
    private lateinit var repositories: TestRepositories

    @Before
    fun setUp() = runTest(mainDispatcherRule.dispatcher) {
        server = MockWebServer()
        server.start()
        repositories = buildTestRepositories(server.url("/").toString(), defaultDispatcher = mainDispatcherRule.dispatcher)
        repositories.subjectDao.upsertAll(
            listOf(
                subjectEntity(id = 1, characters = "水", meaning = "Water", componentIds = listOf(2)),
                subjectEntity(id = 2, characters = "氵", meaning = "Water radical"),
                subjectEntity(
                    id = 3,
                    characters = "水",
                    meaning = "Water",
                    pronunciationAudios = listOf(
                        PronunciationAudioData(
                            url = "https://api.wanikani.com/audio/mizu.mp3",
                            contentType = "audio/mpeg",
                            metadata = PronunciationAudioMetadataData(pronunciation = "みず")
                        )
                    )
                ),
                subjectEntity(
                    id = 4,
                    characters = "水",
                    meaning = "Water",
                    pronunciationAudios = listOf(
                        PronunciationAudioData(
                            url = "https://api.wanikani.com/audio/mizu.ogg",
                            contentType = "audio/ogg",
                            metadata = PronunciationAudioMetadataData(pronunciation = "みず")
                        ),
                        PronunciationAudioData(
                            url = "https://api.wanikani.com/audio/mizu.mp3",
                            contentType = "audio/mpeg",
                            metadata = PronunciationAudioMetadataData(pronunciation = "みず")
                        )
                    )
                ),
                subjectEntity(
                    id = 5,
                    characters = "水",
                    meaning = "Water",
                    pronunciationAudios = listOf(
                        PronunciationAudioData(
                            url = "https://api.wanikani.com/audio/mizu.ogg",
                            contentType = "audio/ogg",
                            metadata = PronunciationAudioMetadataData(pronunciation = "みず")
                        )
                    )
                )
            )
        )
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(mainDispatcherRule.dispatcher + SupervisorJob()),
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
        audioPlayer = FakePronunciationAudioPlayer()
        strokeOrderRepository = FakeStrokeOrderRepository(
            mapOf('水' to listOf(StrokeOrderStroke(pathData = "M10,10L90,90", labelX = 5f, labelY = 5f)))
        )
        viewModel = SubjectDetailViewModel(
            repositories.subjectRepository, repositories.assignmentRepository, settingsRepository, audioPlayer, strokeOrderRepository,
            repositories.statsRepository
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /** Drains until the initial "nothing opened yet" emission clears. */
    private suspend fun ReceiveTurbine<SubjectDetailUiState>.awaitNotLoading(): SubjectDetailUiState {
        var state = awaitItem()
        while (state.isLoading) state = awaitItem()
        return state
    }

    /** Drains until [subjectId] is loaded AND its stroke-order lookup (a second, later-arriving
     *  emission — see [SubjectDetailViewModel.strokeOrderFlow]) has resolved, so no unconsumed
     *  event is left behind when the `test { }` block exits. */
    private suspend fun ReceiveTurbine<SubjectDetailUiState>.awaitSettled(subjectId: Long): SubjectDetailUiState {
        var state = awaitItem()
        while (state.detail?.subjectId != subjectId || state.strokeOrder is StrokeOrderUiState.Loading) {
            state = awaitItem()
        }
        return state
    }

    @Test
    fun `open loads the subject and resolves its component as a related tile`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            val loaded = awaitSettled(1)

            assertThat(loaded.detail?.meanings).containsExactly("Water")
            assertThat(loaded.relatedSubjects[2]?.meanings).containsExactly("Water radical")
            assertThat(loaded.backStack).isEmpty()
        }
    }

    @Test
    fun `navigateToRelated pushes current subject onto the back stack and loads the target`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            awaitSettled(1)

            viewModel.navigateToRelated(2)
            val drilled = awaitSettled(2)

            assertThat(drilled.backStack).containsExactly(1L)
        }
    }

    @Test
    fun `goBack pops the stack and returns false once empty`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            awaitSettled(1)

            viewModel.navigateToRelated(2)
            awaitSettled(2)

            assertThat(viewModel.goBack()).isTrue()
            val backAtRoot = awaitSettled(1)
            assertThat(backAtRoot.backStack).isEmpty()

            assertThat(viewModel.goBack()).isFalse()
        }
    }

    @Test
    fun `goBack restores the recorded scroll offset for the subject being returned to`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            awaitSettled(1)
            viewModel.recordScrollOffset(1, 240)

            viewModel.navigateToRelated(2)
            awaitSettled(2)

            assertThat(viewModel.goBack()).isTrue()
            val backAtRoot = awaitSettled(1)
            assertThat(backAtRoot.pendingScrollOffset).isEqualTo(240)
        }
    }

    @Test
    fun `goBack yields a pendingScrollOffset of 0 when nothing was recorded for the subject`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            awaitSettled(1)

            viewModel.navigateToRelated(2)
            awaitSettled(2)

            assertThat(viewModel.goBack()).isTrue()
            val backAtRoot = awaitSettled(1)
            assertThat(backAtRoot.pendingScrollOffset).isEqualTo(0)
        }
    }

    @Test
    fun `navigateToRelated always resets pendingScrollOffset to 0`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            awaitSettled(1)

            viewModel.navigateToRelated(2)
            awaitSettled(2)
            viewModel.recordScrollOffset(2, 500)

            assertThat(viewModel.goBack()).isTrue()
            awaitSettled(1)

            viewModel.navigateToRelated(2)
            val drilledAgain = awaitSettled(2)
            assertThat(drilledAgain.pendingScrollOffset).isEqualTo(0)
        }
    }

    @Test
    fun `open resets pendingScrollOffset to 0`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            awaitSettled(1)
            viewModel.recordScrollOffset(1, 300)

            viewModel.navigateToRelated(2)
            awaitSettled(2)

            // Land on a non-zero pendingScrollOffset via goBack so the subsequent open() reset is
            // an observable state change rather than a same-value no-op the StateFlow would drop.
            assertThat(viewModel.goBack()).isTrue()
            val backAtRoot = awaitSettled(1)
            assertThat(backAtRoot.pendingScrollOffset).isEqualTo(300)

            viewModel.open(1)
            val reopened = awaitSettled(1)
            assertThat(reopened.pendingScrollOffset).isEqualTo(0)
        }
    }

    @Test
    fun `uiState reflects showPitchAccent from settings and updates when it changes`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            val state = awaitNotLoading()
            assertThat(state.showPitchAccent).isTrue()

            settingsRepository.setShowPitchAccent(false)
            var updated = awaitItem()
            while (updated.showPitchAccent) updated = awaitItem()
            assertThat(updated.showPitchAccent).isFalse()
        }
    }

    @Test
    fun `uiState resolves stroke order for a kanji with bundled data`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            val loaded = awaitSettled(1)

            assertThat(loaded.strokeOrder).isEqualTo(
                StrokeOrderUiState.Available(listOf(StrokeOrderStroke(pathData = "M10,10L90,90", labelX = 5f, labelY = 5f)))
            )
        }
    }

    @Test
    fun `uiState has no stroke order for a radical with no matching character`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(2)
            val loaded = awaitSettled(2)

            assertThat(loaded.strokeOrder).isEqualTo(StrokeOrderUiState.Unavailable)
        }
    }

    private fun subjectEntity(
        id: Long,
        characters: String,
        meaning: String,
        componentIds: List<Long> = emptyList(),
        pronunciationAudios: List<PronunciationAudioData> = emptyList()
    ): SubjectEntity = SubjectEntity(
        id = id,
        subjectType = "kanji",
        level = 1,
        slug = characters,
        characters = characters,
        meanings = listOf(MeaningData(meaning = meaning, primary = true)),
        readings = listOf(ReadingData(reading = "みず", primary = true)),
        documentUrl = null,
        componentSubjectIds = componentIds,
        pronunciationAudios = pronunciationAudios,
        searchTarget = "$characters $meaning".lowercase()
    )

    @Test
    fun `playReading plays the audio matching the requested reading`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(3)
            awaitSettled(3)
        }

        viewModel.playReading("みず")

        assertThat(audioPlayer.playedAudios).hasSize(1)
        assertThat(audioPlayer.playedAudios.first().url).isEqualTo("https://api.wanikani.com/audio/mizu.mp3")
    }

    @Test
    fun `playReading is a no-op when the subject has no pronunciation audio`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            awaitSettled(1)
        }

        viewModel.playReading("みず")

        assertThat(audioPlayer.playedAudios).isEmpty()
    }

    @Test
    fun `playReading picks the mp3 clip when restrictAudioToMp3 is enabled`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setRestrictAudioToMp3(true)

        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(4)
            var state = awaitSettled(4)
            while (!state.restrictAudioToMp3) state = awaitItem()
        }

        viewModel.playReading("みず")

        assertThat(audioPlayer.playedAudios).hasSize(1)
        assertThat(audioPlayer.playedAudios.first().url).isEqualTo("https://api.wanikani.com/audio/mizu.mp3")
    }

    @Test
    fun `playReading is a no-op when restrictAudioToMp3 is enabled and only an ogg clip exists`() = runTest(mainDispatcherRule.dispatcher) {
        settingsRepository.setRestrictAudioToMp3(true)

        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(5)
            var state = awaitSettled(5)
            while (!state.restrictAudioToMp3) state = awaitItem()
        }

        viewModel.playReading("みず")

        assertThat(audioPlayer.playedAudios).isEmpty()
    }

    @Test
    fun `uiState has no assignmentStats or reviewStats when the subject has not been lessoned`() = runTest(mainDispatcherRule.dispatcher) {
        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            val loaded = awaitSettled(1)

            assertThat(loaded.assignmentStats).isNull()
            assertThat(loaded.reviewStats).isNull()
        }
    }

    @Test
    fun `uiState reflects assignmentStats once the subject has been lessoned`() = runTest(mainDispatcherRule.dispatcher) {
        repositories.assignmentDao.upsertAll(
            listOf(
                AssignmentEntity(
                    id = 900, subjectId = 1, subjectType = "kanji", srsStage = 5,
                    createdAt = "2020-01-01T00:00:00.000000Z",
                    unlockedAt = "2026-01-02T00:00:00.000000Z",
                    startedAt = "2026-01-03T00:00:00.000000Z",
                    passedAt = "2026-01-20T00:00:00.000000Z",
                    availableAt = "2026-01-26T03:00:00.000000Z",
                    hidden = false
                )
            )
        )

        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            val loaded = awaitSettled(1)

            assertThat(loaded.assignmentStats?.srsStage).isEqualTo(SrsStage.GURU_1)
            assertThat(loaded.assignmentStats?.passedAt).isNotNull()
            assertThat(loaded.reviewStats).isNull()
        }
    }

    @Test
    fun `uiState reflects reviewStats once the subject has review history`() = runTest(mainDispatcherRule.dispatcher) {
        repositories.reviewStatisticDao.upsertAll(
            listOf(
                ReviewStatisticEntity(
                    id = 900, subjectId = 1, subjectType = "kanji",
                    meaningCorrect = 9, meaningIncorrect = 1, meaningMaxStreak = 5, meaningCurrentStreak = 3,
                    readingCorrect = 8, readingIncorrect = 2, readingMaxStreak = 6, readingCurrentStreak = 1,
                    percentageCorrect = 85, hidden = false,
                    lastReviewedAt = "2026-01-25T12:00:00.000000Z"
                )
            )
        )

        viewModel.uiState.test {
            awaitNotLoading()

            viewModel.open(1)
            val loaded = awaitSettled(1)

            assertThat(loaded.reviewStats?.meaningAccuracyPercent).isEqualTo(90)
            assertThat(loaded.reviewStats?.hasBeenReviewed).isTrue()
        }
    }
}
