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
    fun build_withPriority_admitsLowestTierItemsFirst() {
        val queue = QuizQueue<String>()
        // "A" and "B" are tier 0, "C" and "D" tier 1 — but they sit in the *reverse* order here, so
        // a queue that ignored the tier key would admit C first.
        queue.build(
            listOf("C", "D", "A", "B"),
            typesFor = { listOf(QuestionType.MEANING) },
            shuffle = false,
            cap = 2,
            priorityOf = { if (it == "A" || it == "B") 0 else 1 }
        )
        assertEquals(setOf("A", "B"), queue.toList().map { it.item }.toSet())
        assertEquals(setOf("C", "D"), queue.reserveList().map { it.item }.toSet())
    }

    @Test
    fun build_withPriority_ordersReserveByTier_soAdmitNextKeepsFeedingPriorityItems() {
        val queue = QuizQueue<String>()
        queue.build(
            listOf("C", "D", "A", "B"),
            typesFor = { listOf(QuestionType.MEANING) },
            shuffle = false,
            cap = 2,
            priorityOf = { if (it == "A" || it == "B") 0 else 1 }
        )
        // Nothing tier-0 is left behind, so the reserve is just the tier-1 items, in queue order.
        assertEquals(listOf("C", "D"), queue.reserveList().map { it.item })

        // With three tier-0 items and a cap of one, the two left behind must be admitted before the
        // tier-1 one — this is the property the whole setting rests on.
        val second = QuizQueue<String>()
        second.build(
            listOf("C", "A", "B"),
            typesFor = { listOf(QuestionType.MEANING) },
            shuffle = false,
            cap = 1,
            priorityOf = { if (it == "C") 1 else 0 }
        )
        assertEquals(listOf("A"), second.toList().map { it.item })
        assertEquals(listOf("B", "C"), second.reserveList().map { it.item })
        second.admitNext(cap = 2)
        assertTrue(second.toList().any { it.item == "B" })
    }

    @Test
    fun build_withPriorityAndShuffle_stillLeadsWithAPriorityItemAndKeepsReserveTierOrdered() {
        val queue = QuizQueue<String>()
        // Every tier-0 item must be admitted ahead of every tier-1 item, but *which* of the two
        // tier-0 items shows up first is left to the shuffle.
        repeat(20) {
            queue.build(
                listOf("C", "D", "A", "B"),
                typesFor = { listOf(QuestionType.MEANING) },
                shuffle = true,
                cap = 2,
                priorityOf = { if (it == "A" || it == "B") 0 else 1 }
            )
            assertEquals(setOf("A", "B"), queue.toList().map { it.item }.toSet())
            assertEquals(setOf("C", "D"), queue.reserveList().map { it.item }.toSet())
        }
    }

    @Test
    fun buildWithoutPriority_isUnchangedByTheNewParameter() {
        val queue = QuizQueue<String>()
        // No tier key: the first `cap` items of the (unshuffled) list are admitted, exactly as before.
        queue.build(
            listOf("A", "B", "C"),
            typesFor = { listOf(QuestionType.MEANING) },
            shuffle = false,
            cap = 2
        )
        assertEquals(listOf("A", "B"), queue.toList().map { it.item })
        assertEquals(listOf("C"), queue.reserveList().map { it.item })
    }

    @Test
    fun buildWithoutPriority_ignoresTiersEntirely() {
        val queue = QuizQueue<String>()
        // The same list and the same tier key as build_withPriority_admitsLowestTierItemsFirst, but
        // with priorityOf omitted — which is exactly how Review's DEFAULT mode calls build. A "B"
        // that would have led is admitted only in its own position, proving DEFAULT is the untouched
        // pre-setting path rather than a tier function that happens to rank everything equally.
        queue.build(
            listOf("C", "D", "A", "B"),
            typesFor = { listOf(QuestionType.MEANING) },
            shuffle = false,
            cap = 2
        )
        assertEquals(listOf("C", "D"), queue.toList().map { it.item })
        assertEquals(listOf("A", "B"), queue.reserveList().map { it.item })
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
