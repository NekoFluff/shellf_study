package com.crazyfluff.shellfstudy.shared.data

import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException

suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        val code = e.response.status.value
        val message = if (code == 401) "Invalid API token." else "WaniKani API error ($code)."
        ApiResult.Error(message, e)
    } catch (e: Exception) {
        // Anything else here is a connectivity failure (DNS, connection refused, timeout, offline,
        // etc.) — the exact exception type differs per platform engine (OkHttp on Android, Darwin
        // on iOS), so this catches broadly rather than enumerating each one.
        ApiResult.Error("Network error — check your connection.", e)
    }
