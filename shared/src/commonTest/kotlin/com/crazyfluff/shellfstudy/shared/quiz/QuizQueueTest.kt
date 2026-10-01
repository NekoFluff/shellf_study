package com.crazyfluff.shellfstudy.shared.quiz

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QuizQueueTest {

    private fun isP(item: String) = item.startsWith("P")
    private fun priority(count: Int) = (0 until count).map { "P$it" }
    private fun ordinary(count: Int) = (0 until count).map { "O$it" }

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
    fun build_withPriority_admitsPriorityItemsFirst() {
        val queue = QuizQueue<String>()
        // "A" and "B" are priority items, but sit *after* "C" and "D" here, so a queue that ignored
        // isPriority would admit C first.
        queue.build(
            listOf("C", "D", "A", "B"),
            typesFor = { listOf(QuestionType.MEANING) },
            shuffle = false,
            cap = 2,
            isPriority = { it == "A" || it == "B" }
        )
        assertEquals(setOf("A", "B"), queue.toList().map { it.item }.toSet())
        assertEquals(listOf("C", "D"), queue.reserveList().map { it.item })
    }

    @Test
    fun build_withPriority_keepsPriorityItemsAheadInReserve_soAdmitNextKeepsFeedingThem() {
        val queue = QuizQueue<String>()
        // Three priority items and a cap of one: the two left behind must be admitted before the
        // ordinary one — this is the property the whole setting rests on.
        queue.build(
            listOf("C", "A", "B"),
            typesFor = { listOf(QuestionType.MEANING) },
            shuffle = false,
            cap = 1,
            isPriority = { it != "C" }
        )
        assertEquals(listOf("A"), queue.toList().map { it.item })
        assertEquals(listOf("B", "C"), queue.reserveList().map { it.item })
        assertEquals(listOf("B"), queue.admitNext(cap = 2, isPriority = { it != "C" }))
    }

    @Test
    fun build_withPriorityAndShuffle_stillSelectsThePriorityItems() {
        val queue = QuizQueue<String>()
        // Every priority item must be admitted ahead of every ordinary one, but *which* of the two
        // shows up first is left to the shuffle.
        repeat(20) {
            queue.build(
                listOf("C", "D", "A", "B"),
                typesFor = { listOf(QuestionType.MEANING) },
                shuffle = true,
                cap = 2,
                isPriority = { it == "A" || it == "B" }
            )
            assertEquals(setOf("A", "B"), queue.toList().map { it.item }.toSet())
            assertEquals(listOf("C", "D"), queue.reserveList().map { it.item })
        }
    }

    @Test
    fun build_withFewPriorityItems_admitsOnlyThem() {
        val queue = QuizQueue<String>()
        queue.build(
            ordinary(10) + listOf("P0", "P1"),
            typesFor = { listOf(QuestionType.MEANING) },
            shuffle = false,
            cap = 10,
            isPriority = ::isP
        )
        // Just the two priority items — not the eight ordinary ones a plain cap of ten would have
        // let in beside them.
        assertEquals(listOf("P0", "P1"), queue.toList().map { it.item })
        assertEquals(ordinary(10), queue.reserveList().map { it.item })
    }

    @Test
    fun build_withManyPriorityItems_fillsTheCapWithThem_andAdmitsTheNextPriorityItemFirst() {
        val queue = QuizQueue<String>()
        queue.build(
            ordinary(3) + priority(12),
            typesFor = { listOf(QuestionType.MEANING) },
            shuffle = false,
            cap = 10,
            isPriority = ::isP
        )
        assertEquals(priority(10), queue.toList().map { it.item })

        queue.removeCurrent() // P0 finished
        assertEquals(listOf("P10"), queue.admitNext(cap = 10, isPriority = ::isP))
    }

    @Test
    fun admitNext_holdsOrdinaryItemsBack_whileAnyPriorityItemIsInFlight() {
        val queue = QuizQueue<String>()
        // Two priority items in flight, nothing priority left in reserve.
        queue.restore(
            inFlight = priority(2).map { PendingQuestion(it, QuestionType.MEANING) },
            reserve = ordinary(3).map { PendingQuestion(it, QuestionType.MEANING) },
            shuffleOnAdmit = false
        )
        // One finishes, freeing a slot — but P1 is still in flight, so nothing comes in.
        queue.removeCurrent()
        assertEquals(emptyList(), queue.admitNext(cap = 10, isPriority = ::isP))
        assertEquals(listOf("P1"), queue.toList().map { it.item })
    }

    @Test
    fun admitNext_refillsToTheCapInOneCall_onceTheLastPriorityItemFinishes() {
        val queue = QuizQueue<String>()
        queue.restore(
            inFlight = listOf(PendingQuestion("P0", QuestionType.MEANING)),
            reserve = ordinary(20).map { PendingQuestion(it, QuestionType.MEANING) },
            shuffleOnAdmit = false
        )
        queue.removeCurrent() // P0, the last priority item, finishes
        assertEquals(ordinary(10), queue.admitNext(cap = 10, isPriority = ::isP))
        assertEquals(10, queue.inFlightItemCount)
    }

    @Test
    fun aSimulatedSessionAsksEveryPriorityItemBeforeAnyOrdinaryOne_andAlwaysDrains() {
        // Seeded so a failure reproduces. Every step answers the current question right or wrong at
        // random, the way QuizSession.grade drives the queue — misses included, since a requeued
        // priority question is exactly what used to surface late.
        repeat(50) { seed ->
            val random = Random(seed)
            val items = (0 until 40).map { if (random.nextInt(4) == 0) "P$it" else "O$it" }.shuffled(random)
            val queue = QuizQueue<String>()
            queue.build(
                items,
                typesFor = { listOf(QuestionType.MEANING, QuestionType.READING) },
                shuffle = true,
                cap = 10,
                isPriority = ::isP
            )
            val answered = mutableMapOf<String, Int>()
            var askedOrdinary = false
            var steps = 0
            while (!queue.isEmpty) {
                assertTrue(steps++ < 10_000, "seed $seed did not drain")
                val question = assertNotNull(queue.current, "seed $seed: questions in reserve but none in flight")
                assertTrue(
                    !(askedOrdinary && isP(question.item)),
                    "seed $seed: ${question.item} was asked after an ordinary item"
                )
                askedOrdinary = askedOrdinary || !isP(question.item)
                queue.removeCurrent()
                if (random.nextInt(3) == 0) {
                    queue.requeue(question)
                } else {
                    answered[question.item] = (answered[question.item] ?: 0) + 1
                }
                queue.admitNext(cap = 10, isPriority = ::isP)

                val inFlight = queue.toList().map { it.item }
                assertTrue(queue.inFlightItemCount <= 10, "seed $seed: over the cap")
                assertTrue(
                    inFlight.all(::isP) || inFlight.none(::isP),
                    "seed $seed: priority and ordinary items in flight together"
                )
            }
            assertEquals(items.toSet(), answered.filterValues { it == 2 }.keys, "seed $seed: not every item finished")
        }
    }

    @Test
    fun buildWithoutPriority_isUnchangedByTheNewParameter() {
        val queue = QuizQueue<String>()
        // No isPriority: the first `cap` items of the (unshuffled) list are admitted, exactly as before.
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
        // The same list as build_withPriority_admitsPriorityItemsFirst, but with isPriority
        // omitted — which is exactly how Review's DEFAULT mode calls build. A "B"
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
    fun restoreAndToList_roundTripsEntriesInOrder() {
        val queue = QuizQueue<String>()
        val entries = listOf(PendingQuestion("A", QuestionType.MEANING), PendingQuestion("B", QuestionType.READING))
        queue.restore(entries)
        assertEquals(entries, queue.toList())
        assertEquals(entries.first(), queue.current)
    }
}
