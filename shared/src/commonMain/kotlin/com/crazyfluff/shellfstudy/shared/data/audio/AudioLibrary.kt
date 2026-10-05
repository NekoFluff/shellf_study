package com.crazyfluff.shellfstudy.shared.data.audio

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encodeUtf8
import okio.FileSystem
import okio.Path

/** What the library holds for one level. */
data class LevelStorage(val clips: Int, val bytes: Long)

/** How a batch of downloads ended. */
enum class AudioDownloadOutcome {
    /** Every clip is in the library (or WaniKani refused one, which retrying won't change). */
    COMPLETE,

    /** The network went away part-way. What finished is kept; running again picks up the rest. */
    NETWORK_FAILURE
}

private sealed interface ClipResult {
    data class Stored(val path: Path) : ClipResult
    data object Rejected : ClipResult
    data object NetworkFailure : ClipResult
}

private class StoredClip(val level: Int, val path: Path, val bytes: Long)

private sealed interface Response {
    class Body(val bytes: ByteArray) : Response
    data object Refused : Response
    data object Unreachable : Response
}

private const val PARALLEL_DOWNLOADS = 4
private const val TEMP_SUFFIX = ".tmp"

private val CONTENT_TYPE_EXTENSIONS = mapOf(
    "audio/mpeg" to "mp3",
    "audio/ogg" to "ogg",
    "audio/webm" to "webm"
)

/**
 * Every pronunciation clip kept on this device: the only place audio is played from.
 *
 * Clips live under [root] in one directory per level (`3/<sha1 of url>.mp3`), in storage the OS never
 * clears on its own and nothing here ever evicts. A clip arrives either from a level download chosen in
 * Settings → Offline audio, or the first time it is played online ([ensure]); either way it stays until
 * the user deletes its level. The directory layout *is* the index — there is no database to fall out
 * of step with the files — so per-level sizes and deletes are a directory listing and a recursive
 * delete.
 *
 * Every write lands in a temporary file that is moved into place only once complete, and a response
 * that isn't a success is never written, so a clip is either whole or absent. Nothing here throws
 * except cancellation: a clip that can't be fetched is reported as such, never as a crash.
 */
class AudioLibrary(
    private val root: Path,
    private val httpClient: HttpClient,
    private val fileSystem: FileSystem = systemFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    /** File name → clip, for every clip on disk. Null until [load] has listed the directory. */
    private val index = MutableStateFlow<Map<String, StoredClip>?>(null)
    private val loadLock = Mutex()

    private val _levels = MutableStateFlow<Map<Int, LevelStorage>>(emptyMap())

    /** Clip count and size per level, kept current as clips are added and deleted. */
    val levels: StateFlow<Map<Int, LevelStorage>> = _levels.asStateFlow()

    /** Lists what's on disk. Idempotent and cheap after the first call; every other entry point calls
     *  it, so callers only need it to warm the index early (see [localPath]). */
    suspend fun load() {
        if (index.value != null) return
        loadLock.withLock {
            if (index.value != null) return
            val clips = withContext(ioDispatcher) { fileSystem.readLibrary(root) }
            index.value = clips
            publishLevels(clips)
        }
    }

    /** Where [audio] is stored, or null if it isn't. Synchronous, so a play button can ask on every
     *  tap; until [load] has run it reports nothing as stored. */
    fun localPath(audio: PronunciationAudio): String? = index.value?.get(fileName(audio))?.path?.toString()

    /** [audio]'s stored path, downloading it into the library first if it isn't there yet. Null when
     *  it can't be had right now (offline, or refused by the server). */
    suspend fun ensure(audio: PronunciationAudio): String? {
        load()
        localPath(audio)?.let { return it }
        return (fetch(audio) as? ClipResult.Stored)?.path?.toString()
    }

    /**
     * Downloads every clip in [clips] that isn't already stored, [PARALLEL_DOWNLOADS] at a time,
     * reporting `(done, total)` after each. Stops starting new downloads once one fails for lack of a
     * network — the rest would fail the same way.
     */
    suspend fun download(
        clips: List<PronunciationAudio>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): AudioDownloadOutcome {
        load()
        val missing = clips.distinctBy(::fileName).filter { localPath(it) == null }
        val total = missing.size
        onProgress(0, total)
        if (missing.isEmpty()) return AudioDownloadOutcome.COMPLETE

        var done = 0
        var networkFailed = false
        val progressLock = Mutex()
        val permits = Semaphore(PARALLEL_DOWNLOADS)
        coroutineScope {
            missing.map { clip ->
                async {
                    permits.withPermit {
                        if (networkFailed) return@withPermit
                        val result = fetch(clip)
                        progressLock.withLock {
                            if (result is ClipResult.NetworkFailure) networkFailed = true else done++
                            onProgress(done, total)
                        }
                    }
                }
            }.awaitAll()
        }
        return if (networkFailed) AudioDownloadOutcome.NETWORK_FAILURE else AudioDownloadOutcome.COMPLETE
    }

    suspend fun deleteLevel(level: Int) {
        load()
        withContext(ioDispatcher) { runCatching { fileSystem.deleteRecursively(root / level.toString()) } }
        updateIndex { clips -> clips.filterValues { it.level != level } }
    }

    suspend fun deleteAll() {
        load()
        withContext(ioDispatcher) { runCatching { fileSystem.deleteRecursively(root) } }
        updateIndex { emptyMap() }
    }

    private suspend fun fetch(audio: PronunciationAudio): ClipResult =
        when (val response = httpClient.requestClip(audio.url)) {
            Response.Unreachable -> ClipResult.NetworkFailure
            Response.Refused -> ClipResult.Rejected
            is Response.Body -> store(audio, response.bytes)
        }

    private suspend fun store(audio: PronunciationAudio, bytes: ByteArray): ClipResult {
        val name = fileName(audio)
        val directory = root / audio.level.toString()
        val target = directory / name
        val written = withContext(ioDispatcher) {
            runCatching {
                fileSystem.createDirectories(directory)
                val temp = directory / (name + TEMP_SUFFIX)
                fileSystem.write(temp) { write(bytes) }
                fileSystem.atomicMove(temp, target)
            }.isSuccess
        }
        // A failed write is the device's problem (storage full), not the clip's: retrying later may
        // well work, so it's reported like a network failure rather than a refusal.
        if (!written) return ClipResult.NetworkFailure
        updateIndex { it + (name to StoredClip(audio.level, target, bytes.size.toLong())) }
        return ClipResult.Stored(target)
    }

    private fun updateIndex(change: (Map<String, StoredClip>) -> Map<String, StoredClip>) {
        index.update { current -> change(current.orEmpty()) }
        publishLevels(index.value.orEmpty())
    }

    private fun publishLevels(clips: Map<String, StoredClip>) {
        _levels.value = clips.values.groupBy { it.level }
            .mapValues { (_, stored) -> LevelStorage(clips = stored.size, bytes = stored.sumOf { it.bytes }) }
    }
}

/** Every complete clip under [root], by file name; removes downloads that never finished. */
private fun FileSystem.readLibrary(root: Path): Map<String, StoredClip> {
    if (!exists(root)) return emptyMap()
    val clips = mutableMapOf<String, StoredClip>()
    val levelDirectories = listOrNull(root).orEmpty()
        .mapNotNull { directory -> directory.name.toIntOrNull()?.let { level -> level to directory } }
    for ((level, directory) in levelDirectories) {
        val (unfinished, complete) = listOrNull(directory).orEmpty()
            .partition { it.name.endsWith(TEMP_SUFFIX) }
        // A temporary file is a download the process didn't live to finish.
        unfinished.forEach { runCatching { delete(it) } }
        for (file in complete) {
            metadataOrNull(file)?.size?.let { size -> clips[file.name] = StoredClip(level, file, size) }
        }
    }
    return clips
}

private suspend fun HttpClient.requestClip(url: String): Response = try {
    val response = get(url)
    if (response.status.isSuccess()) Response.Body(response.bodyAsBytes()) else Response.Refused
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    Response.Unreachable
}

private fun fileName(audio: PronunciationAudio): String =
    "${audio.url.encodeUtf8().sha1().hex()}.${CONTENT_TYPE_EXTENSIONS[audio.contentType] ?: "audio"}"
