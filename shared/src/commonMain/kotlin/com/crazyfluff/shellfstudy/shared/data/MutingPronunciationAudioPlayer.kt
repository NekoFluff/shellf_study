package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.plus

/**
 * Wraps the platform player so [AppSettings.audioMuted] silences every clip from one place: autoplay
 * after grading, the reading rows' play buttons, the reading hint and the subject-detail sheet all
 * reach audio through the injected [PronunciationAudioPlayer], so none of them has to check the
 * setting itself. Muting also stops a clip that is already playing.
 *
 * [scope] must run on the thread the delegate expects to be called on — the main thread for both
 * platform players (ExoPlayer throws "Player is accessed on the wrong thread" otherwise) — because
 * muting calls [PronunciationAudioPlayer.stop] from it.
 *
 * Until [muted]'s first emission the player assumes unmuted — a DataStore read lands long before
 * anything could be graded or tapped.
 */
class MutingPronunciationAudioPlayer(
    private val delegate: PronunciationAudioPlayer,
    muted: Flow<Boolean>,
    scope: CoroutineScope
) : PronunciationAudioPlayer {
    private val isMuted: StateFlow<Boolean> = muted.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, false)

    init {
        isMuted.onEach { if (it) delegate.stop() }.launchIn(scope)
    }

    override val state: StateFlow<PlaybackState> get() = delegate.state

    override fun play(audio: PronunciationAudio) {
        if (!isMuted.value) delegate.play(audio)
    }

    override fun stop() = delegate.stop()
}

/** How both platforms' DI wraps their real player: muted per [settingsRepository], with muting's
 *  `stop()` dispatched on the main thread, which both platform players require. */
fun PronunciationAudioPlayer.mutedBy(
    settingsRepository: SettingsRepository,
    applicationScope: CoroutineScope
): PronunciationAudioPlayer = MutingPronunciationAudioPlayer(
    delegate = this,
    muted = settingsRepository.settings.map { it.audioMuted },
    scope = applicationScope + Dispatchers.Main
)
