package com.crazyfluff.shellfstudy.core.network

import com.crazyfluff.shellfstudy.core.data.TokenRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

private const val WANIKANI_REVISION = "20170710"

/**
 * Attaches the stored WaniKani API token as a Bearer credential, plus the required
 * Wanikani-Revision header, to every request. Runs on OkHttp's own dispatcher thread,
 * so blocking on the DataStore read here is acceptable.
 */
class AuthInterceptor @Inject constructor(
    private val tokenRepository: TokenRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenRepository.tokenFlow.firstOrNull() }
        val request = chain.request().newBuilder()
            .addHeader("Wanikani-Revision", WANIKANI_REVISION)
            .apply {
                if (!token.isNullOrBlank()) {
                    addHeader("Authorization", "Bearer $token")
                }
            }
            .build()
        return chain.proceed(request)
    }
}
