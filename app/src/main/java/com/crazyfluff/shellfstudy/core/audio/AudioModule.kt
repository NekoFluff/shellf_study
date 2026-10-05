package com.crazyfluff.shellfstudy.core.audio

import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.crazyfluff.shellfstudy.shared.data.mutedBy
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.data.audio.AudioDownloadScheduler
import com.crazyfluff.shellfstudy.shared.data.audio.LibraryBackedAudioPlayer
import com.crazyfluff.shellfstudy.shared.di.APPLICATION_SCOPE
import com.crazyfluff.shellfstudy.shared.di.AUDIO_LIBRARY_ROOT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

val audioModule = module {
    // noBackupFilesDir: never cleared by the OS (unlike cacheDir) and left out of cloud backups —
    // the clips can always be downloaded again, so there's no point uploading them.
    single(AUDIO_LIBRARY_ROOT) {
        val context = androidContext()
        // Clips used to live in an ExoPlayer cache here, before the library replaced it.
        get<CoroutineScope>(APPLICATION_SCOPE).launch(Dispatchers.IO) {
            File(context.cacheDir, "pronunciation_audio").deleteRecursively()
        }
        File(context.noBackupFilesDir, "audio_library").toOkioPath()
    }
    single<AudioDownloadScheduler> { WorkManagerAudioDownloadScheduler(androidContext()) }
    single {
        val context = androidContext()
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                // ExoPlayer's automatic focus handling only supports USAGE_MEDIA/USAGE_GAME (it
                // silently never requests focus for any other usage, including this one) — see
                // RealPronunciationAudioPlayer, which requests focus itself instead.
                /* handleAudioFocus = */ false
            )
            .build()
    }
    single { androidContext().getSystemService(AudioManager::class.java) }
    single<PronunciationAudioPlayer> {
        val applicationScope = get<CoroutineScope>(APPLICATION_SCOPE)
        LibraryBackedAudioPlayer(
            library = get(),
            localPlayer = RealPronunciationAudioPlayer(get(), get()),
            scope = applicationScope + Dispatchers.Main
        ).mutedBy(get<SettingsRepository>(), applicationScope)
    }
}
