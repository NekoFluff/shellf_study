package com.crazyfluff.shellfstudy.shared.data.audio

import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Plays every clip from the [library]: a stored clip plays straight from disk, and one that isn't
 * stored yet is downloaded into the library first, then played from there. So a clip heard once online
 * is available offline from then on, and the platform player only ever opens local files.
 *
 * [localPlayer] is the platform's player, handed a copy of the clip whose `url` is the stored file's
 * absolute path. [scope] must run on the main thread, which both platform players require.
 *
 * A newer [play] or a [stop] cancels a download still in flight, so a slow fetch can never start
 * playing over whatever the user asked for since.
 */
class LibraryBackedAudioPlayer(
    private val library: AudioLibrary,
    private val localPlayer: PronunciationAudioPlayer,
    private val scope: CoroutineScope
) : PronunciationAudioPlayer {

    /** What this class is doing before the platform player is involved — null once it has handed over. */
    private val preparing = MutableStateFlow<PlaybackState?>(null)
    private var pending: Job? = null

    override val state: StateFlow<PlaybackState> =
        combine(preparing, localPlayer.state) { own, platform -> own ?: platform }
            .stateIn(scope, SharingStarted.Eagerly, PlaybackState.IDLE)

    init {
        // Warm the index, so the first tap finds stored clips without waiting on a directory listing.
        scope.launch { library.load() }
    }

    override fun play(audio: PronunciationAudio) {
        pending?.cancel()
        library.localPath(audio)?.let { path ->
            preparing.value = null
            localPlayer.play(audio.copy(url = path))
            return
        }
        localPlayer.stop()
        preparing.value = PlaybackState.BUFFERING
        pending = scope.launch {
            val path = library.ensure(audio)
            if (path == null) {
                preparing.value = PlaybackState.ERROR
            } else {
                preparing.value = null
                localPlayer.play(audio.copy(url = path))
            }
        }
    }

    override fun stop() {
        pending?.cancel()
        pending = null
        preparing.value = null
        localPlayer.stop()
    }

    override fun isAvailableOffline(audio: PronunciationAudio): Boolean = library.localPath(audio) != null
}
