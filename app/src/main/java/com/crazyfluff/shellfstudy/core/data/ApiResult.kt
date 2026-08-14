package com.crazyfluff.shellfstudy.core.data

import io.ktor.client.plugins.ResponseException

sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Error(val message: String, val throwable: Throwable? = null) : ApiResult<Nothing>
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Error -> this
}

/** True only for a confirmed 401 — distinguishes an actually-invalid token from a network/server hiccup. */
val ApiResult.Error.isAuthError: Boolean
    get() = (throwable as? ResponseException)?.response?.status?.value == 401

/** A definitive 4xx rejection (e.g. 422 — already recorded elsewhere) that will never succeed on
 *  retry, as opposed to a transient network/5xx failure that's worth retrying. */
val ApiResult.Error.isTerminalRejection: Boolean
    get() = (throwable as? ResponseException)?.response?.status?.value
        ?.let { it in 400..499 && it != 401 } ?: false
