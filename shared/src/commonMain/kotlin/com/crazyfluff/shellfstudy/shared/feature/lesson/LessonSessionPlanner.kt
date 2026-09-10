package com.crazyfluff.shellfstudy.shared.feature.lesson

import com.crazyfluff.shellfstudy.shared.data.DEFAULT_LESSON_BATCH_SIZE

/**
 * Pure session-shape decisions for a lesson session — how a committed selection is sliced into the
 * study→quiz batches a session actually cycles through, and how many items a fresh selection should
 * default to. Kept free of LessonItem/Room/ViewModel concerns so the arithmetic is directly testable
 * (see LessonSessionPlannerTest) and shared by both the live session and its resume path.
 *
 * A session's plan is *frozen* at "Start session": the learner's ordered selection is the plan, and
 * batches are that same order re-sliced. Nothing is re-planned mid-session, so the progress a learner
 * sees ("batch 2 of 4") is a promise the session can always keep — but it also means the only thing
 * that has to be persisted for a resume is the ordered assignment ids plus the batch size.
 */
object LessonSessionPlanner {

    /** Slices a session's frozen, ordered assignment ids into batches of [batchSize]. An empty
     *  selection (or a batch size past the end) yields a single short batch, never an empty one in
     *  the middle of a plan — a plan either has batches or is empty, which is what lets "batch index
     *  == batches.size" unambiguously mean "every batch is done" (see the CHECKPOINT phase). */
    fun batches(assignmentIds: List<Long>, batchSize: Int): List<List<Long>> =
        if (assignmentIds.isEmpty()) emptyList() else assignmentIds.chunked(normalizeBatchSize(batchSize))

    /**
     * How many of [availableCount] lessons a fresh selection should pre-select.
     *
     * Deliberately the *smaller* of the configured batch size and what's left of today's goal: one
     * batch is the unit a session is committed to, and starting a session that would blow past a goal
     * the learner already hit is exactly the oversized-session problem batching exists to prevent.
     * Once the goal is met ([remainingDailyGoal] <= 0) the goal stops constraining the default — the
     * learner has already decided to do more today, so a plain batch is the honest default — and
     * [availableCount] wins whenever there isn't that much left to study.
     */
    fun defaultSelectionSize(availableCount: Int, batchSize: Int, remainingDailyGoal: Int): Int {
        if (availableCount <= 0) return 0
        val target = if (remainingDailyGoal >= 1) minOf(normalizeBatchSize(batchSize), remainingDailyGoal) else normalizeBatchSize(batchSize)
        return target.coerceIn(1, availableCount)
    }

    /** Same rule as [DEFAULT_LESSON_BATCH_SIZE]'s documentation: a batch size of 0 or less is never
     *  meaningful (it would produce no batches at all), so it's clamped rather than validated —
     *  persisted settings or a persisted session plan from an older build, or a hand-edited
     *  preference, must not be able to build a session with nothing in it. */
    fun normalizeBatchSize(batchSize: Int): Int = batchSize.coerceAtLeast(1)
}
