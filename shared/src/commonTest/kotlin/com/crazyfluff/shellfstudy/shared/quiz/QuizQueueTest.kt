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
    fun moveMatchingToBack_movesFirstMatchToBack() {
        val queue = QuizQueue<String>()
        // After build(shuffle=false): A-MEANING, A-READING, B-MEANING, B-READING
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING, QuestionType.READING) }, shuffle = false)
        queue.removeCurrent() // consume A-MEANING → queue: A-READING, B-MEANING, B-READING
        // A's only remaining entry (A-READING) moves to the back instead of staying at the front.
        queue.moveMatchingToBack { it.item == "A" }
        assertEquals(PendingQuestion("B", QuestionType.MEANING), queue.current)
        assertEquals(
            listOf(
                PendingQuestion("B", QuestionType.MEANING),
                PendingQuestion("B", QuestionType.READING),
                PendingQuestion("A", QuestionType.READING)
            ),
            queue.toList()
        )
    }

    @Test
    fun moveMatchingToBack_noOp_whenPredicateMatchesNothing() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        val before = queue.toList()
        queue.moveMatchingToBack { it.item == "Z" }
        assertEquals(before, queue.toList())
    }

    @Test
    fun build_withCap_admitsOnlyCapItems_holdingRestInReserve() {
        val queue = QuizQueue<String>()
        // 3 items x 2 types = 6 entries; cap = 2 items admits only A and B's entries up front.
        queue.build(listOf("A", "B", "C"), typesFor = { listOf(QuestionType.MEANING, QuestionType.READING) }, shuffle = false, cap = 2)
        assertEquals(2, queue.inFlightItemCount)
        assertEquals(4, queue.toList().size)
        assertEquals(2, queue.reserveList().size)
        assertTrue(queue.reserveList().all { it.item == "C" })
        // size/isEmpty still reflect the whole queue, cap or not.
        assertEquals(6, queue.size)
    }

    @Test
    fun build_withCapAtOrAboveItemCount_admitsEverything_reserveEmpty() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false, cap = 5)
        assertEquals(2, queue.inFlightItemCount)
        assertTrue(queue.reserveList().isEmpty())
    }

    @Test
    fun admitNext_belowCap_doesNothing_untilCapIsReached() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A", "B", "C"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false, cap = 1)
        // Already at cap (1 admitted, cap 1) — no-op regardless of reserve contents.
        queue.admitNext(cap = 1)
        assertEquals(1, queue.inFlightItemCount)
        assertEquals(2, queue.reserveList().size)
    }

    @Test
    fun admitNext_underCap_pullsInOneMoreItemsQuestions() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A", "B"), typesFor = { listOf(QuestionType.MEANING, QuestionType.READING) }, shuffle = false, cap = 1)
        assertEquals(1, queue.inFlightItemCount)
        assertEquals(1, queue.reserveList().size / 2)
        // Room for one more (cap 2, currently 1) — pulls in B's entries (both types) from reserve.
        queue.admitNext(cap = 2)
        assertEquals(2, queue.inFlightItemCount)
        assertTrue(queue.reserveList().isEmpty())
        assertEquals(4, queue.toList().size)
    }

    @Test
    fun admitNext_doesNothing_whenReserveIsEmpty() {
        val queue = QuizQueue<String>()
        queue.build(listOf("A"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false)
        val before = queue.toList()
        queue.admitNext(cap = 10)
        assertEquals(before, queue.toList())
    }

    @Test
    fun restore_roundTripsBothInFlightAndReserve() {
        val queue = QuizQueue<String>()
        val inFlight = listOf(PendingQuestion("A", QuestionType.MEANING))
        val reserve = listOf(PendingQuestion("B", QuestionType.MEANING), PendingQuestion("B", QuestionType.READING))
        queue.restore(inFlight, reserve)
        assertEquals(inFlight, queue.toList())
        assertEquals(reserve, queue.reserveList())
        assertEquals(1, queue.inFlightItemCount)
        assertEquals(3, queue.size)
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
    fun retainCurrentAndMatching_alsoDropsReserveEntirely() {
        val queue = QuizQueue<String>()
        // A admitted; B, C held back in reserve.
        queue.build(listOf("A", "B", "C"), typesFor = { listOf(QuestionType.MEANING) }, shuffle = false, cap = 1)
        assertEquals(2, queue.reserveList().size)
        // wrapUp-style call: keep everything (nothing to drop from inFlight) — reserve still goes.
        queue.retainCurrentAndMatching { true }
        assertTrue(queue.reserveList().isEmpty())
        assertEquals(1, queue.size)
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
