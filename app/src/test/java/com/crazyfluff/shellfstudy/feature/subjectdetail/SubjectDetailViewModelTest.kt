package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.data.model.StrokeOrderStroke
import com.crazyfluff.shellfstudy.core.designsystem.strokeorder.StrokeOrderUiState
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioMetadataData
import com.crazyfluff.shellfstudy.shared.network.ReadingData
import com.crazyfluff.shellfstudy.fakes.FakePronunciationAudioPlayer
import com.crazyfluff.shellfstudy.fakes.FakeStrokeOrderRepository
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

    @Before
    fun setUp() = runTest(mainDispatcherRule.dispatcher) {
        server = MockWebServer()
        server.start()
        val repositories = buildTestRepositories(server.url("/").toString())
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
            repositories.subjectRepository, repositories.assignmentRepository, settingsRepository, audioPlayer, strokeOrderRepository
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
}
