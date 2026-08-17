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
    fun restoreAndToList_roundTripsEntriesInOrder() {
        val queue = QuizQueue<String>()
        val entries = listOf(PendingQuestion("A", QuestionType.MEANING), PendingQuestion("B", QuestionType.READING))
        queue.restore(entries)
        assertEquals(entries, queue.toList())
        assertEquals(entries.first(), queue.current)
    }
}
