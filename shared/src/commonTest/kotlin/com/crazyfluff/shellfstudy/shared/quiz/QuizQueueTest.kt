package com.crazyfluff.shellfstudy.shared.quiz

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuizQueueTest {

    @Test
    fun build_createsOneEntryPerItemPerType() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING, QuestionType.READING) }, shuffle = false)
        assertEquals(4, queue.size)
    }

    @Test
    fun build_shuffleFalse_preservesInsertionOrder() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A"), typesFor = { listOf(QuestionType.MEANING, QuestionType.READING) }, shuffle = false)
        assertEquals(PendingQuestion("A", QuestionType.MEANING), queue.current)
        queue.removeCurrent()
        assertEquals(PendingQuestion("A", QuestionType.READING), queue.current)
    }

    @Test
    fun requeue_appendsToBack_soNextQuestionDiffers() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        val first = queue.removeCurrent()!!
        queue.requeue(first)
        // B should now be current, not the requeued A
        assertEquals(PendingQuestion("B", QuestionType.MEANING), queue.current)
        queue.removeCurrent()
        assertEquals(first, queue.current)
    }

    @Test
    fun moveMatchingToFront_movesLastMatchToFront() {
        val queue = QuizQueue<String>()
        // After build(shuffle=false): A-MEANING, A-READING, B-MEANING, B-READING
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING, QuestionType.READING) }, shuffle = false)
        queue.removeCurrent() // consume A-MEANING → queue: A-READING, B-MEANING, B-READING
        // Two entries match item=="B": B-MEANING (index 1) and B-READING (index 2).
        // indexOfLast finds B-READING — that one moves to front.
        queue.moveMatchingToFront { it.item == "B" }
        assertEquals(PendingQuestion("B", QuestionType.READING), queue.current)
    }

    @Test
    fun moveMatchingToFront_noOp_whenPredicateMatchesNothing() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        val before = queue.toList()
        queue.moveMatchingToFront { it.item == "Z" }
        assertEquals(before, queue.toList())
    }

    @Test
    fun capInFlight_belowCap_leavesFrontUnchanged() {
        val queue = QuizQueue<String>()
        // After build(shuffle=false): A-MEANING, B-MEANING
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        // "B" is started, "A" is not — but inFlightCount (1) is below cap (2), so the not-yet-started
        // front is left alone.
        queue.capInFlight(isStarted = { it == "B" }, inFlightCount = 1, cap = 2)
        assertEquals(PendingQuestion("A", QuestionType.MEANING), queue.current)
    }

    @Test
    fun capInFlight_atCap_swapsInTheFirstAlreadyStartedEntry() {
        val queue = QuizQueue<String>()
        // After build(shuffle=false): A-MEANING, B-MEANING, C-MEANING
        queue.build(listOf("A", "B", "C"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        // Front (A) isn't started; B and C are. At cap, the not-yet-started front must be swapped out
        // for the first already-started entry (B) instead of introducing A.
        queue.capInFlight(isStarted = { it == "B" || it == "C" }, inFlightCount = 2, cap = 2)
        assertEquals(PendingQuestion("B", QuestionType.MEANING), queue.current)
    }

    @Test
    fun capInFlight_atCap_leavesFrontUnchanged_whenFrontIsAlreadyStarted() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        val before = queue.toList()
        queue.capInFlight(isStarted = { it == "A" }, inFlightCount = 5, cap = 5)
        assertEquals(before, queue.toList())
    }

    @Test
    fun capInFlight_atCap_leavesFrontUnchanged_whenNothingElseIsStarted() {
        val queue = QuizQueue<String>()
        // Nothing has been started yet (e.g. the very first question of a session) — there's no
        // already-started entry to swap in, so the not-yet-started front must still be offered.
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        val before = queue.toList()
        queue.capInFlight(isStarted = { false }, inFlightCount = 10, cap = 10)
        assertEquals(before, queue.toList())
    }

    @Test
    fun retainCurrentAndMatching_keepsCurrentAndMatchingRest_dropsNonMatching() {
        val queue = QuizQueue<String>()
        // After build(shuffle=false): A-MEANING, B-MEANING, C-MEANING
        queue.build(listOf("A", "B", "C"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        // Current is A; keep entries where item != "C" in the rest → C is dropped
        queue.retainCurrentAndMatching { it.item != "C" }
        assertEquals(2, queue.size)
        assertEquals(PendingQuestion("A", QuestionType.MEANING), queue.current)
    }

    @Test
    fun retainCurrentAndMatching_onEmptyQueue_isStillSafe() {
        val queue = QuizQueue<String>()
        queue.retainCurrentAndMatching { true }
        assertTrue(queue.isEmpty)
        assertNull(queue.current)
    }

    @Test
    fun noneMatches_returnsTrueWhenNoEntryMatchesPredicate() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        assertTrue(queue.noneMatches { it.item == "Z" })
    }

    @Test
    fun noneMatches_returnsFalseWhenAnEntryMatchesPredicate() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        assertFalse(queue.noneMatches { it.item == "A" })
    }

    @Test
    fun pushFront_reinsertsRemovedQuestionAsCurrent() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        val removed = queue.removeCurrent()!!
        // B is now current; pushing the removed A back should make it current again, ahead of B.
        queue.pushFront(removed)
        assertEquals(removed, queue.current)
        assertEquals(2, queue.size)
    }

    @Test
    fun restoreAndToList_roundTripsEntriesInOrder() {
        val queue = QuizQueue<String>()
        val entries = listOf(PendingQuestion("A", QuestionType.MEANING), PendingQuestion("B", QuestionType.READING))
        queue.restore(entries)
        assertEquals(entries, queue.toList())
        assertEquals(entries.first(), queue.current)
    }
}
