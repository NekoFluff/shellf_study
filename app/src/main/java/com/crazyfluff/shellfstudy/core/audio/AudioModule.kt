package com.crazyfluff.shellfstudy.core.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.crazyfluff.shellfstudy.shared.data.PronunciationAudioPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

private const val AUDIO_CACHE_MAX_BYTES = 50L * 1024 * 1024

@Module
@InstallIn(SingletonComponent::class)
@UnstableApi
object AudioModule {

    @Provides
    @Singleton
    fun provideAudioCache(@ApplicationContext context: Context): SimpleCache =
        SimpleCache(
            File(context.cacheDir, "pronunciation_audio"),
            LeastRecentlyUsedCacheEvictor(AUDIO_CACHE_MAX_BYTES)
        )

    @Provides
    @Singleton
    fun provideAudioCacheDataSourceFactory(cache: SimpleCache): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory())

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        cacheDataSourceFactory: CacheDataSource.Factory
    ): ExoPlayer =
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .build()

    @Provides
    @Singleton
    fun providePronunciationAudioPlayer(impl: RealPronunciationAudioPlayer): PronunciationAudioPlayer = impl
}
