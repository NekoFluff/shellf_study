package com.crazyfluff.shellfstudy.core.audio

import android.os.Looper
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.model.PronunciationAudio
import com.crazyfluff.shellfstudy.shared.data.mutedBy
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * ExoPlayer throws "Player is accessed on the wrong thread" for any call off the main thread, and
 * muting calls `stop()` from a settings collector. Wired from the background application scope, that
 * collector once ran on a Default-dispatcher worker and crashed the app the moment audio was muted.
 */
@RunWith(AndroidJUnit4::class)
class MutedPlayerThreadingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // The same dispatcher APPLICATION_SCOPE uses in production.
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @After
    fun tearDown() = applicationScope.cancel()

    /** Stands in for ExoPlayer's thread check: records which thread each `stop()` arrived on. */
    private class MainThreadOnlyPlayer : PronunciationAudioPlayer {
        override val state = MutableStateFlow(PlaybackState.IDLE)
        val stopThreadsWereMain = mutableListOf<Boolean>()

        override fun play(audio: PronunciationAudio) = Unit

        override fun stop() {
            stopThreadsWereMain += Looper.myLooper() == Looper.getMainLooper()
        }
    }

    @Test
    fun `muting stops the delegate on the main thread`() {
        val settingsRepository = SettingsRepository(
            PreferenceDataStoreFactory.create(produceFile = { tempFolder.newFile("test.preferences_pb") })
        )
        val delegate = MainThreadOnlyPlayer()
        delegate.mutedBy(settingsRepository, applicationScope)

        runBlocking { settingsRepository.setAudioMuted(true) }
        val deadline = System.currentTimeMillis() + 5_000
        while (delegate.stopThreadsWereMain.isEmpty() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }

        assertThat(delegate.stopThreadsWereMain).containsExactly(true)
    }
}
