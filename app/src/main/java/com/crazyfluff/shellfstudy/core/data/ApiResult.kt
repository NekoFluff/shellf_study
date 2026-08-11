package com.crazyfluff.shellfstudy.core.data

import retrofit2.HttpException

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
    get() = (throwable as? HttpException)?.code() == 401
