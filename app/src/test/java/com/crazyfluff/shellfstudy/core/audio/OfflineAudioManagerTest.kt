package com.crazyfluff.shellfstudy.core.audio

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.crazyfluff.shellfstudy.fakes.FakeSubjectDao
import com.crazyfluff.shellfstudy.shared.data.audio.AudioDownloadOutcome
import com.crazyfluff.shellfstudy.shared.data.audio.AudioDownloadScheduler
import com.crazyfluff.shellfstudy.shared.data.audio.AudioLibrary
import com.crazyfluff.shellfstudy.shared.data.audio.LevelAudioStatus
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioManager
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioState
import com.crazyfluff.shellfstudy.shared.data.audio.OfflineAudioVoices
import com.crazyfluff.shellfstudy.shared.data.model.WaniKaniUser
import com.crazyfluff.shellfstudy.shared.database.SubjectEntity
import com.crazyfluff.shellfstudy.shared.network.MeaningData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioData
import com.crazyfluff.shellfstudy.shared.network.PronunciationAudioMetadataData
import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Path.Companion.toOkioPath
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OfflineAudioManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val subjectDao = FakeSubjectDao()
    private val scheduler = RecordingScheduler()
    private lateinit var manager: OfflineAudioManager

    private class RecordingScheduler : AudioDownloadScheduler {
        val scheduled = mutableListOf<Boolean>()
        var cancels = 0
        override fun schedule(wifiOnly: Boolean) {
            scheduled += wifiOnly
        }
        override fun cancel() {
            cancels++
        }
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse(body = "clip ${request.target}")
        }
        server.start()
        val library = AudioLibrary(root = tempFolder.newFolder("audio").toOkioPath(), httpClient = HttpClient())
        manager = OfflineAudioManager(
            dataStore = PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") }),
            library = library,
            subjectDao = subjectDao,
            scheduler = scheduler,
            prefetchImages = {}
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    private suspend fun seedVocabulary(id: Long, level: Int) {
        fun audio(name: String, gender: String, actor: Long) = PronunciationAudioData(
            url = server.url("/$id-$name.mp3").toString(),
            contentType = "audio/mpeg",
            metadata = PronunciationAudioMetadataData(gender = gender, voiceActorId = actor, pronunciation = "よみ")
        )
        subjectDao.upsertAll(
            listOf(
                SubjectEntity(
                    id = id, subjectType = "vocabulary", level = level, slug = "v$id", characters = "語",
                    meanings = listOf(MeaningData("Word", primary = true)), readings = emptyList(), documentUrl = null,
                    pronunciationAudios = listOf(audio("m", "male", 1), audio("f", "female", 2))
                )
            )
        )
    }

    private suspend fun state(): OfflineAudioState {
        manager.prepare()
        return manager.state.first { it.levels.isNotEmpty() }
    }

    private suspend fun statusOf(level: Int) = state().levels.single { it.level == level }.status

    @Test
    fun `reaching a level adds it to the library and starts downloading it`() = runTest {
        manager.onUserRefreshed(WaniKaniUser(id = "u", username = "u", level = 5))

        assertThat(manager.preferences.first().wantedLevels).containsExactly(5)
        assertThat(scheduler.scheduled).isNotEmpty()
    }

    @Test
    fun `with the current-level toggle off, reaching a level downloads nothing`() = runTest {
        manager.updateSettings(autoDownloadCurrentLevel = false)

        manager.onUserRefreshed(WaniKaniUser(id = "u", username = "u", level = 5))

        assertThat(manager.preferences.first().wantedLevels).isEmpty()
        assertThat(scheduler.scheduled).isEmpty()
    }

    @Test
    fun `levels beyond the subscription are locked and can't be downloaded`() = runTest {
        manager.onUserRefreshed(WaniKaniUser(id = "u", username = "u", level = 2, maxLevelGranted = 3))

        manager.download(listOf(4, 10))

        assertThat(manager.preferences.first().wantedLevels).containsExactly(2)
        assertThat(statusOf(4)).isEqualTo(LevelAudioStatus.Locked)
    }

    @Test
    fun `a run downloads every voice of each wanted level and nothing else`() = runTest {
        seedVocabulary(id = 1, level = 3)
        seedVocabulary(id = 2, level = 3)
        seedVocabulary(id = 3, level = 7)
        manager.download(listOf(3))

        assertThat(manager.runDownloads()).isEqualTo(AudioDownloadOutcome.COMPLETE)

        assertThat(server.requestCount).isEqualTo(4)
        assertThat(statusOf(3)).isInstanceOf(LevelAudioStatus.Downloaded::class.java)
        assertThat(statusOf(7)).isEqualTo(LevelAudioStatus.NotDownloaded(clips = 2))
    }

    @Test
    fun `choosing one voice downloads half as much`() = runTest {
        seedVocabulary(id = 1, level = 3)
        manager.updateSettings(voices = OfflineAudioVoices.FEMALE)
        manager.download(listOf(3))

        manager.runDownloads()

        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().target).contains("-f.mp3")
    }

    @Test
    fun `deleting a level frees it and stops wanting it`() = runTest {
        seedVocabulary(id = 1, level = 3)
        manager.download(listOf(3))
        manager.runDownloads()

        manager.deleteLevel(3)

        assertThat(manager.preferences.first().wantedLevels).isEmpty()
        assertThat(statusOf(3)).isEqualTo(LevelAudioStatus.NotDownloaded(clips = 2))
    }

    @Test
    fun `signing out removes every clip and every choice tied to the account`() = runTest {
        seedVocabulary(id = 1, level = 3)
        manager.onUserRefreshed(WaniKaniUser(id = "u", username = "u", level = 3))
        manager.runDownloads()

        manager.clearForLogout()

        assertThat(state().totalClips).isEqualTo(0)
        assertThat(manager.preferences.first().wantedLevels).isEmpty()
        assertThat(manager.preferences.first().currentLevel).isNull()
        assertThat(scheduler.cancels).isEqualTo(1)
    }
}
