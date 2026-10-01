package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.PersistedItemProgress
import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QuizSessionTest {

    private fun kanji(id: Long) = ReviewItem(
        assignmentId = id, subjectId = id, subjectType = SubjectType.KANJI, characters = "字",
        level = 1, srsStage = 1, meanings = listOf("Character"), readings = listOf("じ")
    )

    private fun radical(id: Long) = ReviewItem(
        assignmentId = id, subjectId = id, subjectType = SubjectType.RADICAL, characters = "一",
        level = 1, srsStage = 1, meanings = listOf("Ground"), readings = emptyList()
    )

    private fun sessionOf(vararg items: ReviewItem, cap: Int? = null) =
        QuizSession<ReviewItem>().withQuestionsFor(items.toList(), shuffle = false, cap = cap)

    @Test
    fun buildingAsksEveryQuestionTypeOfEveryItem() {
        val session = sessionOf(kanji(1), radical(2))

        assertEquals(3, session.totalQuestions)
        assertEquals(
            listOf(1L to QuestionType.MEANING, 1L to QuestionType.READING, 2L to QuestionType.MEANING),
            session.inFlight.map { it.item.assignmentId to it.type }
        )
    }

    @Test
    fun aCorrectAnswerTakesTheQuestionOutAndMarksItDone() {
        val graded = sessionOf(kanji(1)).grade(isCorrect = true, elapsedMs = 100)

        assertEquals(listOf(QuestionType.READING), graded.inFlight.map { it.type })
        assertTrue(graded.progress.getValue(1).meaningDone)
        assertFalse(graded.lastGraded!!.completedItem)
    }

    @Test
    fun aWrongAnswerRecordsTheMissAndSendsTheQuestionToTheBack() {
        val graded = sessionOf(kanji(1), kanji(2)).grade(isCorrect = false, elapsedMs = 100)

        assertEquals(1L to QuestionType.MEANING, graded.inFlight.last().let { it.item.assignmentId to it.type })
        assertEquals(1, graded.progress.getValue(1).incorrectMeaningAttempts)
        assertEquals(4, graded.remainingQuestions)
    }

    @Test
    fun answeringEveryTypeCorrectlyCompletesTheItem() {
        val done = sessionOf(kanji(1))
            .grade(isCorrect = true, elapsedMs = 1)
            .grade(isCorrect = true, elapsedMs = 1)

        assertTrue(done.lastGraded!!.completedItem)
        assertTrue(done.isEmpty)
    }

    @Test
    fun deferringTheSiblingMovesTheItemsOtherTypeToTheBack() {
        val graded = sessionOf(kanji(1), kanji(2)).grade(isCorrect = true, elapsedMs = 1, deferSiblingOnCorrect = true)

        assertEquals(1L to QuestionType.READING, graded.inFlight.last().let { it.item.assignmentId to it.type })
    }

    @Test
    fun aCapAdmitsTheNextItemOnlyOnceAnItemFinishes() {
        val start = sessionOf(radical(1), radical(2), cap = 1)
        assertEquals(listOf(1L), start.inFlight.map { it.item.assignmentId })

        val afterMiss = start.grade(isCorrect = false, elapsedMs = 1, cap = 1)
        assertEquals(listOf(1L), afterMiss.inFlight.map { it.item.assignmentId })

        val afterPass = afterMiss.grade(isCorrect = true, elapsedMs = 1, cap = 1)
        assertEquals(listOf(2L), afterPass.inFlight.map { it.item.assignmentId })
    }

    @Test
    fun undoingAWrongAnswerRestoresTheQuestionAndTheCount() {
        val start = sessionOf(kanji(1), kanji(2))
        val undone = start.grade(isCorrect = false, elapsedMs = 1).undoLastGrade()!!

        assertEquals(start.inFlight, undone.inFlight)
        assertEquals(0, undone.progress.getValue(1).incorrectMeaningAttempts)
        assertTrue(undone.answered.isEmpty())
        assertNull(undone.lastGraded)
    }

    @Test
    fun undoingOnlyRevertsTheAttemptItUndoes() {
        val twiceMissed = sessionOf(radical(1))
            .grade(isCorrect = false, elapsedMs = 1)
            .grade(isCorrect = false, elapsedMs = 1)

        assertEquals(1, twiceMissed.undoLastGrade()!!.progress.getValue(1).incorrectMeaningAttempts)
    }

    @Test
    fun undoingACorrectAnswerPutsTheQuestionBackInFront() {
        val start = sessionOf(kanji(1), kanji(2))
        val undone = start.grade(isCorrect = true, elapsedMs = 1).undoLastGrade()!!

        assertEquals(start.inFlight, undone.inFlight)
        assertFalse(undone.progress.getValue(1).meaningDone)
    }

    @Test
    fun thereIsNothingToUndoBeforeAGradeOrTwiceInARow() {
        val session = sessionOf(kanji(1))

        assertNull(session.undoLastGrade())
        assertNull(session.grade(isCorrect = false, elapsedMs = 1).undoLastGrade()!!.undoLastGrade())
    }

    @Test
    fun gradingWithNothingLeftToAskChangesNothing() {
        val empty = QuizSession<ReviewItem>()

        assertSame(empty, empty.grade(isCorrect = true, elapsedMs = 1))
    }

    @Test
    fun wrappingUpKeepsOnlyTheCurrentAndAttemptedItems() {
        val attempted = sessionOf(radical(1), radical(2), radical(3), radical(4))
            .grade(isCorrect = false, elapsedMs = 1) // 1 missed, requeued behind 2, 3, 4

        val wrapped = attempted.wrappedUp()

        // 2 is current, 1 was attempted; 3 and 4 were never touched.
        assertEquals(listOf(2L, 1L), wrapped.inFlight.map { it.item.assignmentId })
        assertEquals(2, wrapped.totalQuestions)
    }

    @Test
    fun aNewPassKeepsProgressAndAnswersButReplacesTheQuestions() {
        val firstPass = sessionOf(radical(1)).grade(isCorrect = false, elapsedMs = 5).grade(isCorrect = true, elapsedMs = 5)

        val secondPass = firstPass.withQuestionsFor(listOf(radical(2)), shuffle = false)

        assertEquals(listOf(2L), secondPass.inFlight.map { it.item.assignmentId })
        assertEquals(1, secondPass.totalQuestions)
        assertEquals(setOf(1L), secondPass.progress.keys)
        assertEquals(2, secondPass.answered.size)
    }

    @Test
    fun seedingProgressKeepsWhatIsAlreadyThere() {
        val missed = sessionOf(radical(1)).grade(isCorrect = false, elapsedMs = 1)

        val seeded = missed.withProgressFor(listOf(radical(1), radical(2)))

        assertEquals(1, seeded.progress.getValue(1).incorrectMeaningAttempts)
        assertFalse(seeded.progress.getValue(2).hasAnyProgress)
    }

    @Test
    fun aSessionSurvivesPersistingAndRestoring() {
        val items = listOf(kanji(1), kanji(2), kanji(3))
        val session = QuizSession<ReviewItem>().withQuestionsFor(items, shuffle = false, cap = 2)
            .grade(isCorrect = false, elapsedMs = 10, cap = 2)
            .grade(isCorrect = true, elapsedMs = 20, cap = 2)

        val restored = QuizSession.restore(
            itemsById = items.associateBy { it.assignmentId },
            inFlight = session.persistedInFlight(),
            reserve = session.persistedReserve(),
            progress = session.persistedProgress(),
            answered = session.persistedAnswers(),
            totalQuestions = session.totalQuestions,
            priorityIds = session.priorityIds
        )

        assertNotNull(restored)
        assertEquals(session.inFlight, restored.inFlight)
        assertEquals(session.reserve, restored.reserve)
        assertEquals(session.progress, restored.progress)
        assertEquals(session.answered, restored.answered)
        assertEquals(session.totalQuestions, restored.totalQuestions)
        assertEquals(session.priorityIds, restored.priorityIds)
    }

    @Test
    fun priorityIdsSurvivePersistingAndKeepHoldingOrdinaryItemsBackAfterARestore() {
        val items = (1L..8L).map(::radical)
        val session = QuizSession<ReviewItem>()
            .withQuestionsFor(items, shuffle = false, cap = 4, priorityIds = setOf(7L, 8L))
        // Only the priority items, though the cap has room for two more.
        assertEquals(listOf(7L, 8L), session.inFlight.map { it.item.assignmentId })

        val restored = QuizSession.restore(
            itemsById = items.associateBy { it.assignmentId },
            inFlight = session.persistedInFlight(),
            reserve = session.persistedReserve(),
            priorityIds = session.priorityIds
        )!!
        // Finishing one priority item frees a slot, but the other is still in flight: nothing enters.
        val afterOne = restored.grade(isCorrect = true, elapsedMs = 1, cap = 4)
        assertEquals(listOf(8L), afterOne.inFlight.map { it.item.assignmentId })
        // Finishing the last one fills the working set with ordinary items, up to the cap.
        val afterBoth = afterOne.grade(isCorrect = true, elapsedMs = 1, cap = 4)
        assertEquals(setOf(1L, 2L, 3L, 4L), afterBoth.inFlight.map { it.item.assignmentId }.toSet())
    }

    @Test
    fun undoingAGradeThatAdmittedItemsPutsTheQueueBackExactlyAsItWas() {
        val items = (1L..8L).map(::radical)
        // Two priority items in flight; the ordinary ones all wait.
        val start = QuizSession<ReviewItem>()
            .withQuestionsFor(items, shuffle = true, cap = 6, priorityIds = setOf(7L, 8L))
        val onLastPriority = start.grade(isCorrect = true, elapsedMs = 1, cap = 6)

        // Finishing the last priority item admits six ordinary items at once.
        val graded = onLastPriority.grade(isCorrect = true, elapsedMs = 1, cap = 6)
        assertEquals(6, graded.inFlight.map { it.item.assignmentId }.distinct().size)

        val undone = graded.undoLastGrade()!!
        assertEquals(onLastPriority.inFlight, undone.inFlight)
        assertEquals(onLastPriority.reserve, undone.reserve)
    }

    @Test
    fun aQueuedQuestionThatNoLongerResolvesMakesTheSnapshotUnrecoverable() {
        val restored = QuizSession.restore<ReviewItem>(
            itemsById = emptyMap(),
            inFlight = listOf(PersistedQuestion(assignmentId = 1, questionType = "MEANING"))
        )

        assertNull(restored)
    }

    @Test
    fun aSnapshotFromBeforeCountsWerePersistedStillRecordsTheMiss() {
        val restored = QuizSession.restore(
            itemsById = mapOf(1L to kanji(1)),
            progress = listOf(
                PersistedItemProgress(
                    assignmentId = 1, meaningDone = false, readingDone = true,
                    hadIncorrectMeaning = true, hadIncorrectReading = false
                )
            )
        )!!

        assertEquals(1, restored.progress.getValue(1).incorrectMeaningAttempts)
        assertEquals(0, restored.progress.getValue(1).incorrectReadingAttempts)
    }
}
