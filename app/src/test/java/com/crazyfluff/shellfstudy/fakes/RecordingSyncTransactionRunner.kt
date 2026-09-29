package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.sync.SyncTransactionRunner

/**
 * A [SyncTransactionRunner] that runs the block directly and counts how many times it was asked to.
 *
 * The production runner exists to make Room broadcast invalidation once for a whole sync pass rather
 * than once per write, and the fakes in this module have no invalidation tracker to observe — so
 * there is nothing to assert about the broadcast itself here. What *is* worth pinning is the shape:
 * that the orchestrator groups a pass's writes into a single call rather than one per resource, and
 * that it does the grouping around the writes only, never around the fetches. Both are properties of
 * the orchestration, and both are the kind of thing a later refactor could quietly undo.
 */
class RecordingSyncTransactionRunner : SyncTransactionRunner {
    var transactionCount: Int = 0
        private set

    /** True while the block is executing, so a test can assert the writes really ran inside the group. */
    var isInsideTransaction: Boolean = false
        private set

    /**
     * Invoked immediately before the block runs. Lets a test snapshot external state at the moment the
     * transaction opens — used to prove every network fetch completed before the write connection was
     * taken, which is the property that keeps a paged fetch from holding the app's only writer.
     */
    var onEnter: (() -> Unit)? = null

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        transactionCount++
        isInsideTransaction = true
        onEnter?.invoke()
        return try {
            block()
        } finally {
            isInsideTransaction = false
        }
    }
}
