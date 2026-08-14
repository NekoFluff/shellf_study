package com.crazyfluff.shellfstudy.core.network

import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.network.AuthTokenProvider
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.createWaniKaniHttpClient
import com.crazyfluff.shellfstudy.shared.network.waniKaniJson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** Also used outside networking — e.g. ReviewSessionRepository/LessonSessionRepository persist
     *  local quiz-session state as JSON via DataStore. */
    @Provides
    @Singleton
    fun provideJson(): Json = waniKaniJson()

    @Provides
    @Singleton
    fun provideAuthTokenProvider(tokenRepository: TokenRepository): AuthTokenProvider =
        AuthTokenProvider { tokenRepository.tokenFlow.firstOrNull() }

    @Provides
    @Singleton
    fun provideWaniKaniHttpClient(authTokenProvider: AuthTokenProvider, json: Json): HttpClient =
        createWaniKaniHttpClient(tokenProvider = authTokenProvider, json = json)

    @Provides
    @Singleton
    fun provideWaniKaniApi(httpClient: HttpClient): WaniKaniApi = WaniKaniApi(httpClient)
}
