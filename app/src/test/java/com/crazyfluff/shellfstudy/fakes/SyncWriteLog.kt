package com.crazyfluff.shellfstudy.fakes

/**
 * Records the database writes a sync pass performs, so tests can assert on wakeup *volume* rather
 * than only on the values that end up stored.
 *
 * This exists because "how many times did we write" is invisible to every other test here. The fakes
 * are in-memory maps, so writing the same rows twice costs nothing and changes no observable value —
 * a pass that writes eight times and one that writes once produce identical end state. In production
 * those are very different: Room broadcasts invalidation once per outermost write operation, and
 * every observable query in the app re-runs on each broadcast.
 *
 * ## What the numbers mean
 *
 * [writes] is the proxy metric. In production the real cost is Room's invalidation broadcasts, which
 * fire once per outermost write operation — so the count that matters is "how many write operations
 * did this pass issue", and a write of an *empty* list still counts, because `DBUtil.internalPerform`
 * refreshes the invalidation tracker after the block regardless of whether it stored anything
 * (`upsertAll(emptyList())` therefore still wakes every observer).
 *
 * [emptyWrites] is what the no-op guard is meant to remove. A resource whose incremental fetch
 * returned nothing has no row to store and no business waking anyone.
 */
class SyncWriteLog {
    private val entries = mutableListOf<Entry>()

    data class Entry(val resource: String, val rowCount: Int) {
        val isEmpty: Boolean get() = rowCount == 0
    }

    /** Records one bulk write of [rowCount] rows against [resource]. */
    fun record(resource: String, rowCount: Int) {
        entries += Entry(resource, rowCount)
    }

    val writes: List<Entry> get() = entries.toList()

    /** Total write operations issued — the invalidation-broadcast proxy. */
    val writeCount: Int get() = entries.size

    /** Write operations that stored nothing but still invalidate. */
    val emptyWrites: List<Entry> get() = entries.filter { it.isEmpty }

    /** Write operations that actually stored rows. */
    val nonEmptyWrites: List<Entry> get() = entries.filterNot { it.isEmpty }

    fun rowCountFor(resource: String): Int = entries.filter { it.resource == resource }.sumOf { it.rowCount }

    fun reset() = entries.clear()

    override fun toString(): String =
        "writes=$writeCount (empty=${emptyWrites.size}) " +
            entries.joinToString(prefix = "[", postfix = "]") { "${it.resource}:${it.rowCount}" }
}
