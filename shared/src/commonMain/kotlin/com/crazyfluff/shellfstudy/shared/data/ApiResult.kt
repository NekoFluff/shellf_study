package com.crazyfluff.shellfstudy.shared.data

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

/**
 * A definitive 4xx rejection (e.g. 422 — already recorded elsewhere) that will never succeed on
 * retry, as opposed to a transient network/5xx failure that's worth retrying.
 *
 * Three 4xx codes are not terminal, and treating them as if they were loses data:
 *
 * - 401 has its own handling — see [isAuthError], which blocks the drain and asks for a new token.
 * - 429 is the API's rate limit (60 requests a minute), which a long session's drain can hit with no
 *   fault of its own. A new attempt later is exactly the documented remedy.
 * - 408 is a request timeout, which is transient by definition.
 *
 * The outbox drain only ever reads `PENDING` rows, so marking one `FAILED_TERMINAL` retires it for
 * good: the graded review was never submitted, and only the local optimistic stage remained.
 */
val ApiResult.Error.isTerminalRejection: Boolean
    get() = (throwable as? ResponseException)?.response?.status?.value
        ?.let { it in 400..499 && it !in NON_TERMINAL_CLIENT_ERRORS } ?: false

/** Client errors the drain must not retire a row over: handled separately, or transient. */
private val NON_TERMINAL_CLIENT_ERRORS = setOf(401, 408, 429)
