package com.crazyfluff.shellfstudy.shared.quiz

import kotlin.test.Test
import kotlin.test.assertEquals

class QuizAnsweringTest {

    private fun meaning(input: String, closeEnough: Boolean = true) = evaluateAnswer(
        input, QuestionType.MEANING,
        meanings = listOf("Ascend", "Rise"),
        auxiliaryMeanings = listOf("Go Up"),
        readings = listOf("のぼる"),
        closeEnoughEnabled = closeEnough,
        blacklistedMeanings = listOf("Descend")
    )

    @Test
    fun `an exact meaning names the answer it equals`() {
        assertEquals(AnswerOutcome.Graded(isCorrect = true, wasCloseMatch = false, matchedAnswer = "Rise"), meaning("rise"))
    }

    @Test
    fun `a close meaning names the answer it was close to`() {
        assertEquals(AnswerOutcome.Graded(isCorrect = true, wasCloseMatch = true, matchedAnswer = "Ascend"), meaning("acsend"))
    }

    @Test
    fun `a whitelisted auxiliary meaning is matched too`() {
        assertEquals(AnswerOutcome.Graded(isCorrect = true, wasCloseMatch = false, matchedAnswer = "Go Up"), meaning("go-up"))
    }

    /** User synonyms reach grading as auxiliary meanings — AssignmentRepository appends them to the
     *  whitelisted alternates — so they are accepted exactly and close-matched, as on WaniKani. */
    private fun withSynonyms(input: String, synonyms: List<String>) = evaluateAnswer(
        input, QuestionType.MEANING,
        meanings = listOf("Ascend", "Rise"),
        auxiliaryMeanings = listOf("Go Up") + synonyms,
        readings = listOf("のぼる"),
        blacklistedMeanings = listOf("Descend")
    )

    @Test
    fun `a user synonym is accepted exactly`() {
        assertEquals(
            AnswerOutcome.Graded(isCorrect = true, wasCloseMatch = false, matchedAnswer = "Climb"),
            withSynonyms("climb", listOf("Climb"))
        )
    }

    @Test
    fun `a user synonym is close-matched like any accepted meaning`() {
        assertEquals(
            AnswerOutcome.Graded(isCorrect = true, wasCloseMatch = true, matchedAnswer = "Mount up"),
            withSynonyms("mount uo", listOf("Mount up"))
        )
    }

    @Test
    fun `without the synonym the same answer is wrong`() {
        assertEquals(AnswerOutcome.Graded(isCorrect = false, wasCloseMatch = false), withSynonyms("climb", emptyList()))
    }

    @Test
    fun `a blacklisted decoy is graded incorrect rather than close`() {
        assertEquals(AnswerOutcome.Graded(isCorrect = false, wasCloseMatch = false), meaning("descend"))
    }

    @Test
    fun `close matching off grades a typo incorrect`() {
        assertEquals(AnswerOutcome.Graded(isCorrect = false, wasCloseMatch = false), meaning("acsend", closeEnough = false))
    }

    @Test
    fun `kana typed for a meaning is a type mismatch`() {
        assertEquals(AnswerOutcome.TypeMismatch, meaning("のぼる"))
    }

    @Test
    fun `romaji of the reading typed for a meaning is a type mismatch`() {
        assertEquals(AnswerOutcome.TypeMismatch, meaning("noboru"))
    }

    @Test
    fun `a reading must match exactly after romaji conversion`() {
        val readings = listOf("のぼる")
        fun reading(input: String) = evaluateAnswer(input, QuestionType.READING, listOf("Ascend"), emptyList(), readings)
        assertEquals(AnswerOutcome.Graded(isCorrect = true, wasCloseMatch = false, matchedAnswer = "のぼる"), reading("noboru"))
        assertEquals(AnswerOutcome.Graded(isCorrect = false, wasCloseMatch = false), reading("nobori"))
        assertEquals(AnswerOutcome.TypeMismatch, reading("ascend"))
    }

    @Test
    fun `a correct grade lists everything but the matched answer as also accepted`() {
        val feedback = QuizGrade.of(
            isCorrect = true, candidates = listOf("Ascend", "Rise", "Go Up"), wasCloseMatch = true,
            matchedAnswer = "Ascend", answerRevealed = true, rankChange = null
        ).feedback
        assertEquals("Rise, Go Up", feedback.alsoAccepted)
        assertEquals("Ascend", feedback.matchedAnswer)
    }

    @Test
    fun `a wrong grade lists every accepted answer and nothing as also accepted`() {
        val feedback = QuizGrade.of(
            isCorrect = false, candidates = listOf("Ascend", "Rise"), wasCloseMatch = false,
            matchedAnswer = null, answerRevealed = true, rankChange = null
        ).feedback
        assertEquals("Ascend, Rise", feedback.correctAnswer)
        assertEquals("", feedback.alsoAccepted)
    }
}
