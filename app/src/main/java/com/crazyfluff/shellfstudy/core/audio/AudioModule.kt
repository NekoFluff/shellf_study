package com.crazyfluff.shellfstudy.core.audio

import android.media.AudioManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.crazyfluff.shellfstudy.shared.data.AUDIO_CACHE_MAX_BYTES
import com.crazyfluff.shellfstudy.shared.data.mutedBy
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import com.crazyfluff.shellfstudy.shared.data.SettingsRepository
import com.crazyfluff.shellfstudy.shared.di.APPLICATION_SCOPE
import kotlinx.coroutines.CoroutineScope
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.io.File

@UnstableApi
val audioModule = module {
    single {
        SimpleCache(
            File(androidContext().cacheDir, "pronunciation_audio"),
            LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES)
        )
    }
    single<CacheDataSource.Factory> {
        CacheDataSource.Factory()
            .setCache(get<SimpleCache>())
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())
    }
    single {
        val context = androidContext()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(get<CacheDataSource.Factory>()))
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
        RealPronunciationAudioPlayer(get(), get())
            .mutedBy(get<SettingsRepository>(), get<CoroutineScope>(APPLICATION_SCOPE))
    }
}
