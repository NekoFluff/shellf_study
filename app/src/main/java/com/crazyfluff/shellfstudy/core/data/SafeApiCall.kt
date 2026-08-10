package com.crazyfluff.shellfstudy.core.data

import retrofit2.HttpException
import java.io.IOException

suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: IOException) {
        ApiResult.Error("Network error — check your connection.", e)
    } catch (e: HttpException) {
        val message = if (e.code() == 401) "Invalid API token." else "WaniKani API error (${e.code()})."
        ApiResult.Error(message, e)
    }
