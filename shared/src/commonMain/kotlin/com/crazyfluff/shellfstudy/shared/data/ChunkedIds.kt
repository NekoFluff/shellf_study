package com.crazyfluff.shellfstudy.shared.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * SQLite's limit on bound variables in a single statement, with headroom.
 *
 * `SQLITE_MAX_VARIABLE_NUMBER` defaults to 999 before SQLite 3.32 — which is what Android 28 through
 * 32 ship — so a query like `WHERE id IN (:ids)` with a thousand ids fails outright with "too many SQL
 * variables". Android 33+ raised the default to 32766, so this only bites on older devices and only
 * for an account with a large backlog, which is exactly the combination that is hard to notice.
 */
private const val SQLITE_VARIABLE_LIMIT = 900

/**
 * Runs [query] over [ids] in chunks that fit SQLite's bound-variable limit, concatenating the results.
 *
 * The busiest `IN (:ids)` call site had no bound at all: the review queue passes the subject id of
 * every due assignment, and a long-abandoned account can have thousands of those. A single chunk — the
 * common case — goes straight to [query] rather than through the combine.
 *
 * `inline` with a reified row type only because `Iterable<Flow<T>>.combine` requires one; the chunking
 * itself is ordinary.
 */
internal inline fun <ID, reified ROW> chunkedIds(
    ids: List<ID>,
    query: (List<ID>) -> Flow<List<ROW>>
): Flow<List<ROW>> {
    if (ids.isEmpty()) return flowOf(emptyList())
    val chunks = ids.chunked(SQLITE_VARIABLE_LIMIT)
    if (chunks.size == 1) return query(chunks.single())
    val chunkFlows: List<Flow<List<ROW>>> = chunks.map { chunk -> query(chunk) }
    return combine(chunkFlows) { results -> results.flatMap { it } }
}

/** [chunkedIds] for a one-shot suspend query — the same limit, no flow to combine. */
internal suspend fun <ID, ROW> chunkedIdsOnce(
    ids: List<ID>,
    query: suspend (List<ID>) -> List<ROW>
): List<ROW> {
    if (ids.isEmpty()) return emptyList()
    return ids.chunked(SQLITE_VARIABLE_LIMIT).flatMap { query(it) }
}
