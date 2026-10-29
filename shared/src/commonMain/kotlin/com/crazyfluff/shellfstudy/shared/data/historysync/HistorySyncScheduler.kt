package com.crazyfluff.shellfstudy.shared.data.historysync

/**
 * Requests a history sync pass. On Android this enqueues a WorkManager job constrained on
 * connectivity; on iOS it launches the pass in the app-level coroutine scope. Safe to call whether or
 * not sync is turned on: a pass with sync off returns at once.
 */
fun interface HistorySyncScheduler {
    /** Runs a pass after a short quiet period, so triggers that arrive together make one pass. */
    fun requestSync()

    /** Skips the quiet period — for when the learner has just asked for fresh data (pull-to-refresh). */
    fun requestImmediateSync() = requestSync()

    companion object {
        /** For callers built without a scheduler — tests, mostly. */
        val None = HistorySyncScheduler {}
    }
}
