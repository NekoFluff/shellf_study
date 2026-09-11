package com.crazyfluff.shellfstudy.shared.data.audio

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.crazyfluff.shellfstudy.shared.database.iosCachesDirectoryPath
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import okio.FileSystem
import okio.Path.Companion.toPath

private const val AUDIO_CACHE_DIRECTORY_NAME = "pronunciation_audio"
internal const val AUDIO_CACHE_MAX_BYTES = 50L * 1024 * 1024

private val CONTENT_TYPE_EXTENSIONS = mapOf(
    "audio/mpeg" to "mp3",
    "audio/ogg" to "ogg",
    "audio/webm" to "webm"
)

/** On-disk cache for pronunciation clips, mirroring Android's ExoPlayer `SimpleCache` (same
 *  directory name, same 50MB LRU cap) so replaying a clip on iOS doesn't re-download it either.
 *  Lives under the OS-purgeable caches directory — these files are always re-fetchable from the
 *  WaniKani CDN, so losing them under storage pressure is harmless.
 *
 *  Eviction is by download time rather than a true "last played" LRU — this cache never rewrites
 *  a file's timestamp on a cache hit, only on the (re)download that created it — which keeps the
 *  policy simple while still favoring recently-fetched clips over stale ones. */
class IosAudioFileCache(
    private val httpClient: HttpClient = HttpClient(),
    private val fileSystem: FileSystem = FileSystem.SYSTEM
) {

    private val directoryPath by lazy {
        "${iosCachesDirectoryPath()}/$AUDIO_CACHE_DIRECTORY_NAME".toPath().also { fileSystem.createDirectories(it) }
    }

    /** Fast, synchronous existence check — the path a play call takes on every clip before
     *  deciding whether it needs the network at all. */
    fun cachedFile(audio: PronunciationAudio): String? {
        val path = filePath(audio)
        return path.toString().takeIf { fileSystem.exists(path) }
    }

    /** Downloads [audio] into the cache if it isn't already there. Safe to call repeatedly for the
     *  same clip — an existing file short-circuits before any network request. */
    suspend fun download(audio: PronunciationAudio) {
        val path = filePath(audio)
        if (fileSystem.exists(path)) return
        val bytes: ByteArray = httpClient.get(audio.url).body()
        fileSystem.write(path) { write(bytes) }
        evictIfNeeded()
    }

    private fun filePath(audio: PronunciationAudio) =
        directoryPath / "${audio.url.hashCode()}.${CONTENT_TYPE_EXTENSIONS[audio.contentType] ?: "audio"}"

    private fun evictIfNeeded() {
        val entries = fileSystem.list(directoryPath)
            .map { it to fileSystem.metadata(it) }
            .filter { (_, metadata) -> metadata.isRegularFile }
            .sortedBy { (_, metadata) -> metadata.lastModifiedAtMillis ?: 0L }

        var totalBytes = entries.sumOf { (_, metadata) -> metadata.size ?: 0L }
        for ((path, metadata) in entries) {
            if (totalBytes <= AUDIO_CACHE_MAX_BYTES) break
            fileSystem.delete(path)
            totalBytes -= metadata.size ?: 0L
        }
    }
}
