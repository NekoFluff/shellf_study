package com.crazyfluff.shellfstudy.core.data

import io.ktor.client.plugins.ResponseException
import java.io.IOException

suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: IOException) {
        ApiResult.Error("Network error — check your connection.", e)
    } catch (e: ResponseException) {
        val code = e.response.status.value
        val message = if (code == 401) "Invalid API token." else "WaniKani API error ($code)."
        ApiResult.Error(message, e)
    }
