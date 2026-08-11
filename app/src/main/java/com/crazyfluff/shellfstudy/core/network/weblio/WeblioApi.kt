package com.crazyfluff.shellfstudy.core.network.weblio

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query

/** A separate small client for weblio.jp — a different host from the WaniKani API, no auth needed. */
interface WeblioApi {
    @GET("content")
    suspend fun getEntry(@Query("query") query: String): ResponseBody
}
