package com.crazyfluff.shellfstudy.shared.data.audio

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import okio.Path.Companion.toPath

internal fun clip(name: String, level: Int = 1, gender: String? = null) = PronunciationAudio(
    url = "https://cdn.example/$name.mp3",
    contentType = "audio/mpeg",
    pronunciation = null,
    gender = gender,
    voiceActorId = null,
    voiceActorName = null,
    voiceDescription = null,
    level = level
)

class AudioLibraryTest {

    private val fileSystem = systemFileSystem
    private val root = systemTemporaryDirectory / "audio_library_test_${Random.nextLong().toULong()}"

    /** Which URLs the server was asked for, in order. */
    private val requested = mutableListOf<String>()
    private var offline = false
    private val refused = mutableSetOf<String>()

    private val httpClient = HttpClient(
        MockEngine { request ->
            val url = request.url.toString()
            requested += url
            when {
                offline -> throw IOException("offline")
                url in refused -> respondError(HttpStatusCode.NotFound, "<html>not found</html>")
                else -> respond(url.encodeToByteArray())
            }
        }
    )

    private fun library() = AudioLibrary(root = root, httpClient = httpClient, fileSystem = fileSystem)

    @AfterTest
    fun cleanUp() {
        fileSystem.deleteRecursively(root)
    }

    @Test
    fun `a downloaded clip is stored under its level and survives a fresh library`() = runTest {
        val audio = clip("a", level = 3)
        assertEquals(AudioDownloadOutcome.COMPLETE, library().download(listOf(audio)))

        // A new instance reads what's on disk — the directory is the whole index.
        val reopened = library().apply { load() }
        val path = assertNotNull(reopened.localPath(audio))
        assertTrue(path.contains("/3/"))
        assertEquals(1, reopened.levels.value[3]?.clips)
    }

    @Test
    fun `clips already stored are not fetched again`() = runTest {
        val library = library()
        library.download(listOf(clip("a"), clip("b")))
        requested.clear()

        library.download(listOf(clip("a"), clip("b"), clip("c")))

        assertEquals(listOf("https://cdn.example/c.mp3"), requested)
    }

    @Test
    fun `a refused clip is never written so an error page can't be stored as audio`() = runTest {
        refused += "https://cdn.example/missing.mp3"
        val library = library()

        assertEquals(AudioDownloadOutcome.COMPLETE, library.download(listOf(clip("missing"))))

        assertNull(library.localPath(clip("missing")))
        assertNull(library.ensure(clip("missing")))
    }

    @Test
    fun `offline nothing is written or thrown and the next run picks up where it stopped`() = runTest {
        val library = library()
        offline = true

        assertEquals(AudioDownloadOutcome.NETWORK_FAILURE, library.download(listOf(clip("a"), clip("b"))))
        assertNull(library.ensure(clip("a")))
        assertTrue(library.levels.value.isEmpty())

        offline = false
        assertEquals(AudioDownloadOutcome.COMPLETE, library.download(listOf(clip("a"), clip("b"))))
        assertEquals(2, library.levels.value[1]?.clips)
    }

    @Test
    fun `ensure downloads a clip on first play and serves it from disk afterwards`() = runTest {
        val library = library()

        val first = assertNotNull(library.ensure(clip("a")))
        offline = true
        val second = library.ensure(clip("a"))

        assertEquals(first, second)
        assertEquals("https://cdn.example/a.mp3", fileSystem.read(first.toPath()) { readUtf8() })
    }

    @Test
    fun `deleting a level removes its files and leaves other levels alone`() = runTest {
        val library = library()
        library.download(listOf(clip("a", level = 1), clip("b", level = 2)))

        library.deleteLevel(1)

        assertNull(library.localPath(clip("a", level = 1)))
        assertNotNull(library.localPath(clip("b", level = 2)))
        assertEquals(setOf(2), library.levels.value.keys)
        assertEquals(setOf(2), library().apply { load() }.levels.value.keys)
    }

    @Test
    fun `a download the process didn't finish is discarded on load`() = runTest {
        fileSystem.createDirectories(root / "1")
        fileSystem.write(root / "1" / "abc.mp3.tmp") { writeUtf8("half a clip") }

        val library = library().apply { load() }

        assertTrue(library.levels.value.isEmpty())
        assertTrue(fileSystem.list(root / "1").isEmpty())
    }
}
