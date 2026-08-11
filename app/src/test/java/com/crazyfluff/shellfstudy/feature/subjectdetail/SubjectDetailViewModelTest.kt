package com.crazyfluff.shellfstudy.feature.subjectdetail

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.crazyfluff.shellfstudy.MainDispatcherRule
import com.crazyfluff.shellfstudy.core.data.SettingsRepository
import com.crazyfluff.shellfstudy.core.database.SubjectEntity
import com.crazyfluff.shellfstudy.core.network.MeaningData
import com.crazyfluff.shellfstudy.core.network.ReadingData
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.google.common.truth.Truth.assertThat
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

    @Before
    fun setUp() = runTest {
        server = MockWebServer()
        server.start()
        val repositories = buildTestRepositories(server.url("/").toString())
        repositories.subjectDao.upsertAll(
            listOf(
                subjectEntity(id = 1, characters = "水", meaning = "Water", componentIds = listOf(2)),
                subjectEntity(id = 2, characters = "氵", meaning = "Water radical")
            )
        )
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        settingsRepository = SettingsRepository(dataStore)
        viewModel = SubjectDetailViewModel(repositories.subjectRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `open loads the subject and resolves its component as a related tile`() = runTest {
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.open(1)
            var loaded = awaitItem()
            while (loaded.detail?.subjectId != 1L) loaded = awaitItem()

            assertThat(loaded.detail?.meanings).containsExactly("Water")
            assertThat(loaded.relatedSubjects[2]?.meanings).containsExactly("Water radical")
            assertThat(loaded.backStack).isEmpty()
        }
    }

    @Test
    fun `navigateToRelated pushes current subject onto the back stack and loads the target`() = runTest {
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.open(1)
            var loaded = awaitItem()
            while (loaded.detail?.subjectId != 1L) loaded = awaitItem()

            viewModel.navigateToRelated(2)
            var drilled = awaitItem()
            while (drilled.detail?.subjectId != 2L) drilled = awaitItem()

            assertThat(drilled.backStack).containsExactly(1L)
        }
    }

    @Test
    fun `goBack pops the stack and returns false once empty`() = runTest {
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.open(1)
            var loaded = awaitItem()
            while (loaded.detail?.subjectId != 1L) loaded = awaitItem()

            viewModel.navigateToRelated(2)
            var drilled = awaitItem()
            while (drilled.detail?.subjectId != 2L) drilled = awaitItem()

            assertThat(viewModel.goBack()).isTrue()
            var backAtRoot = awaitItem()
            while (backAtRoot.detail?.subjectId != 1L) backAtRoot = awaitItem()
            assertThat(backAtRoot.backStack).isEmpty()

            assertThat(viewModel.goBack()).isFalse()
        }
    }

    @Test
    fun `uiState reflects showPitchAccent from settings and updates when it changes`() = runTest {
        viewModel.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertThat(state.showPitchAccent).isTrue()

            settingsRepository.setShowPitchAccent(false)
            var updated = awaitItem()
            while (updated.showPitchAccent) updated = awaitItem()
            assertThat(updated.showPitchAccent).isFalse()
        }
    }

    private fun subjectEntity(
        id: Long,
        characters: String,
        meaning: String,
        componentIds: List<Long> = emptyList()
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
        searchTarget = "$characters $meaning".lowercase()
    )
}
