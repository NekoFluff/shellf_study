package com.crazyfluff.shellfstudy.shared.sync

import androidx.room.RoomDatabase
import androidx.room.useWriterConnection

/**
 * Runs a group of database writes as one unit, so Room broadcasts invalidation once for the group.
 *
 * ## What this buys
 *
 * Room refreshes its invalidation tracker — and so re-runs every observable query in the app — once
 * per *outermost* write operation. `DBUtil.internalPerform` skips `invalidationTracker.refreshAsync()`
 * while the transactor is already inside a transaction, and `RoomDatabase.useWriterConnection`
 * refreshes once when the connection is released. A sync pass that writes eight times separately
 * therefore wakes every observable flow in the app eight times, and the dashboard alone holds a dozen,
 * several of which are full scans of tables holding thousands of rows.
 *
 * ## Why it is an interface
 *
 * It is the one part of a sync pass that genuinely needs a live database. Everything else the
 * orchestrator does is `ResourceSync` values and repository calls, which the JVM tests drive with
 * in-memory fakes. Keeping the transaction behind this seam lets those tests exercise the real
 * orchestration — ordering, staleness gating, which resources get written — without standing up a
 * Room instance, and lets a test assert that the group really did run as one unit.
 */
interface SyncTransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}

/**
 * The production [SyncTransactionRunner], backed by Room's writer connection.
 *
 * Note that the whole sync pass deliberately does *not* run inside this: the fetches are network-bound
 * and a Room database has exactly one write connection, so holding it across a paged fetch would block
 * every other writer in the app and can throw once the connection is held past its timeout. The
 * repositories fetch first and hand the orchestrator only the rows to write, so the connection is held
 * for the writes alone.
 */
class RoomSyncTransactionRunner(private val database: RoomDatabase) : SyncTransactionRunner {
    override suspend fun <T> runInTransaction(block: suspend () -> T): T =
        database.useWriterConnection { block() }
}
