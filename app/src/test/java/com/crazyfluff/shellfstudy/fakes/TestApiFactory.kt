package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.network.WaniKaniApi
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** mockwebserver3 (OkHttp5)'s [MockResponse] is immutable and built via [MockResponse.Builder]. */
fun jsonResponse(body: String, code: Int = 200): MockResponse =
    MockResponse.Builder().code(code).body(body).build()

fun emptyResponse(code: Int): MockResponse =
    MockResponse.Builder().code(code).build()

/** Builds a real [WaniKaniApi] pointed at a local MockWebServer instance for tests. */
fun buildTestApi(baseUrl: String): WaniKaniApi {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }
    val retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
    return retrofit.create(WaniKaniApi::class.java)
}
