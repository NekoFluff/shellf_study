package com.crazyfluff.shellfstudy.core.network.weblio

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.time.Duration
import javax.inject.Qualifier
import javax.inject.Singleton

private const val WEBLIO_BASE_URL = "https://www.weblio.jp/"
private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
private val READ_TIMEOUT: Duration = Duration.ofMinutes(1)

/** Disambiguates weblio's [OkHttpClient]/[Retrofit] bindings from the WaniKani ones in [com.crazyfluff.shellfstudy.core.network.NetworkModule]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WeblioClient

@Module
@InstallIn(SingletonComponent::class)
object WeblioNetworkModule {

    @Provides
    @Singleton
    @WeblioClient
    fun provideWeblioOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT)
        .readTimeout(READ_TIMEOUT)
        .build()

    @Provides
    @Singleton
    @WeblioClient
    fun provideWeblioRetrofit(@WeblioClient okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(WEBLIO_BASE_URL)
        .client(okHttpClient)
        .build()

    @Provides
    @Singleton
    fun provideWeblioApi(@WeblioClient retrofit: Retrofit): WeblioApi = retrofit.create(WeblioApi::class.java)
}
