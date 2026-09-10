package com.crazyfluff.shellfstudy.shared.feature.lesson

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LessonSessionPlannerTest {

    private fun ids(range: IntRange) = range.map { it.toLong() }

    @Test
    fun slicesAPlanIntoBatchesOfTheConfiguredBatchSize() {
        val batches = LessonSessionPlanner.batches(ids(1..12), batchSize = 5)

        assertEquals(listOf(ids(1..5), ids(6..10), ids(11..12)), batches)
    }

    @Test
    fun aSelectionThatFitsInOneBatchStaysOneBatch() {
        assertEquals(listOf(ids(1..3)), LessonSessionPlanner.batches(ids(1..3), batchSize = 5))
    }

    @Test
    fun anExactMultipleProducesNoTrailingEmptyBatch() {
        // The invariant "batchIndex == batchCount means every batch is done" depends on there never
        // being an empty batch in the middle of a plan.
        val batches = LessonSessionPlanner.batches(ids(1..10), batchSize = 5)

        assertEquals(2, batches.size)
        assertTrue(batches.none { it.isEmpty() })
    }

    @Test
    fun anEmptySelectionProducesNoBatchesAtAll() {
        assertEquals(emptyList(), LessonSessionPlanner.batches(emptyList(), batchSize = 5))
    }

    @Test
    fun aNonPositiveBatchSizeIsClampedToOneRatherThanProducingNoBatches() {
        // A batch size that survived from an older build (or a hand-edited preference) must not be
        // able to build a session with nothing in it.
        assertEquals(listOf(listOf(1L), listOf(2L)), LessonSessionPlanner.batches(ids(1..2), batchSize = 0))
        assertEquals(1, LessonSessionPlanner.normalizeBatchSize(-4))
    }

    @Test
    fun defaultSelectionIsOneBatchWhenTheDailyGoalHasRoomForIt() {
        assertEquals(5, LessonSessionPlanner.defaultSelectionSize(availableCount = 20, batchSize = 5, remainingDailyGoal = 15))
    }

    @Test
    fun defaultSelectionShrinksToWhatIsLeftOfTheDailyGoal() {
        // Picking a full batch would blow past a goal the learner is two items from finishing.
        assertEquals(2, LessonSessionPlanner.defaultSelectionSize(availableCount = 20, batchSize = 5, remainingDailyGoal = 2))
    }

    @Test
    fun defaultSelectionIsAPlainBatchOnceTheDailyGoalIsMet() {
        // The goal stops constraining the default once it's been reached — the learner has already
        // decided to do more today, so a full batch is the honest default rather than zero items.
        assertEquals(5, LessonSessionPlanner.defaultSelectionSize(availableCount = 20, batchSize = 5, remainingDailyGoal = 0))
        assertEquals(5, LessonSessionPlanner.defaultSelectionSize(availableCount = 20, batchSize = 5, remainingDailyGoal = -3))
    }

    @Test
    fun defaultSelectionNeverExceedsWhatIsAvailable() {
        assertEquals(3, LessonSessionPlanner.defaultSelectionSize(availableCount = 3, batchSize = 5, remainingDailyGoal = 15))
        assertEquals(0, LessonSessionPlanner.defaultSelectionSize(availableCount = 0, batchSize = 5, remainingDailyGoal = 15))
    }

    @Test
    fun defaultSelectionIsNeverZeroWhenLessonsAreAvailable() {
        // A met goal means "no constraint", not "select nothing" — the default falls back to a batch,
        // capped by what's actually there.
        assertEquals(4, LessonSessionPlanner.defaultSelectionSize(availableCount = 4, batchSize = 5, remainingDailyGoal = 0))
    }
}
