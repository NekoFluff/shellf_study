package com.crazyfluff.shellfstudy.core.network

import com.crazyfluff.shellfstudy.shared.data.TokenRepository
import com.crazyfluff.shellfstudy.shared.network.AuthTokenProvider
import com.crazyfluff.shellfstudy.shared.network.WaniKaniApi
import com.crazyfluff.shellfstudy.shared.network.createWaniKaniHttpClient
import com.crazyfluff.shellfstudy.shared.network.waniKaniJson
import kotlinx.coroutines.flow.firstOrNull
import org.koin.dsl.module

val networkModule = module {
    /** Also used outside networking — e.g. ReviewSessionRepository/LessonSessionRepository persist
     *  local quiz-session state as JSON via DataStore. */
    single { waniKaniJson() }

    single { AuthTokenProvider { get<TokenRepository>().tokenFlow.firstOrNull() } }

    single { createWaniKaniHttpClient(tokenProvider = get(), json = get()) }

    single { WaniKaniApi(get()) }
}
