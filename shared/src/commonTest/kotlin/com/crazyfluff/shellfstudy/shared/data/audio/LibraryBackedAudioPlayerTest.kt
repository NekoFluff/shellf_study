package com.crazyfluff.shellfstudy.shared.data.audio

import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import okio.FileSystem

/** Records what it was asked to play: the path the library handed over, never a URL. */
private class RecordingLocalPlayer : PronunciationAudioPlayer {
    val played = mutableListOf<String>()
    var stops = 0
    override val state = MutableStateFlow(PlaybackState.IDLE)
    override fun play(audio: PronunciationAudio) {
        played += audio.url
    }
    override fun stop() {
        stops++
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryBackedAudioPlayerTest {

    private val root = systemTemporaryDirectory / "audio_player_test_${Random.nextLong().toULong()}"
    private var offline = false

    /** Held open by a test that needs a download still in flight; completed by default. */
    private var gate = CompletableDeferred(Unit)


    /** The player's scope: on the test's scheduler, so advanceUntilIdle runs it, but not the test's
     *  backgroundScope, whose work advanceUntilIdle deliberately doesn't wait for. */
    private val playerScopes = mutableListOf<CoroutineScope>()

    @AfterTest
    fun cleanUp() {
        playerScopes.forEach { it.cancel() }
        systemFileSystem.deleteRecursively(root)
    }

    private fun TestScope.player(local: RecordingLocalPlayer): Pair<LibraryBackedAudioPlayer, AudioLibrary> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        // On the test's own scheduler, so advanceUntilIdle waits for requests too.
        val engine = MockEngine(
            MockEngineConfig().apply {
                this.dispatcher = dispatcher
                addHandler { request ->
                    gate.await()
                    if (offline) throw IOException("offline")
                    respond(request.url.toString().encodeToByteArray())
                }
            }
        )
        val library = AudioLibrary(root, HttpClient(engine), ioDispatcher = dispatcher)
        val scope = CoroutineScope(dispatcher + SupervisorJob()).also { playerScopes += it }
        return LibraryBackedAudioPlayer(library, local, scope) to library
    }

    @Test
    fun `a clip not yet stored is downloaded then played from disk`() = runTest {
        val local = RecordingLocalPlayer()
        val (player, library) = player(local)

        player.play(clip("a"))
        advanceUntilIdle()

        assertEquals(listOf(library.localPath(clip("a"))), local.played)
        assertTrue(local.played.single().startsWith("/"))
        assertTrue(player.isAvailableOffline(clip("a")))
    }

    @Test
    fun `a stored clip plays at once with no network`() = runTest {
        val local = RecordingLocalPlayer()
        val (player, library) = player(local)
        library.download(listOf(clip("a")))
        offline = true

        player.play(clip("a"))

        assertEquals(1, local.played.size)
    }

    @Test
    fun `offline a clip that isn't stored reports an error instead of failing silently`() = runTest {
        offline = true
        val local = RecordingLocalPlayer()
        val (player, _) = player(local)

        player.play(clip("a"))
        advanceUntilIdle()

        assertEquals(PlaybackState.ERROR, player.state.value)
        assertTrue(local.played.isEmpty())
        assertFalse(player.isAvailableOffline(clip("a")))
    }

    @Test
    fun `stop during a download means the clip never starts`() = runTest {
        gate = CompletableDeferred()
        val local = RecordingLocalPlayer()
        val (player, _) = player(local)

        player.play(clip("a"))
        advanceUntilIdle()
        assertEquals(PlaybackState.BUFFERING, player.state.value)
        player.stop()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(local.played.isEmpty())
        assertEquals(PlaybackState.IDLE, player.state.value)
    }

    @Test
    fun `a newer play wins over a download still in flight`() = runTest {
        val local = RecordingLocalPlayer()
        val (player, library) = player(local)
        library.download(listOf(clip("b")))
        gate = CompletableDeferred()

        player.play(clip("a"))
        advanceUntilIdle()
        player.play(clip("b"))
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(library.localPath(clip("b"))), local.played)
    }
}

