package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.network.PaginationException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

suspend inline fun <T> safeApiCall(crossinline block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: ResponseException) {
        val code = e.response.status.value
        val message = if (code == 401) "Invalid API token." else "WaniKani API error ($code)."
        ApiResult.Error(message, e, status = code)
    } catch (e: SerializationException) {
        // The response arrived and did not match the DTOs: a field renamed or retyped upstream, not a
        // connectivity problem. Reporting it as one — which the broad catch below used to do — sends
        // the reader looking at their network for a bug in this app's models.
        ApiResult.Error("Unexpected response from WaniKani.", e)
    } catch (e: PaginationException) {
        ApiResult.Error(e.message ?: "Unexpected response from WaniKani.", e)
    } catch (e: Exception) {
        // Anything else here is a connectivity failure (DNS, connection refused, timeout, offline,
        // etc.) — the exact exception type differs per platform engine (OkHttp on Android, Darwin
        // on iOS), so this catches broadly rather than enumerating each one.
        ApiResult.Error("Network error — check your connection.", e)
    }
