package com.crazyfluff.shellfstudy.shared.designsystem.performance

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The lesson and review screens reported their jank tags from two hand-written copies that happened to
 * agree. They are now one mapping with per-screen phase names, and these pin what a log reader sees:
 * the keys, their order, and that "no question on screen" reads as `n/a` rather than as an answer
 * state that never happened.
 */
class QuizSessionJankTagsTest {

    @Test
    fun `reports the screen phase answer state and rank change in that order`() {
        val tags = quizSessionJankTags(
            screen = "review",
            phaseName = "active",
            answerState = QuizAnswerJankState.Answering,
            hasRankChange = false
        )

        assertEquals(
            listOf(
                JANK_STATE_SCREEN to "review",
                "phase" to "active",
                "answer" to "answering",
                "rankChange" to "none"
            ),
            tags
        )
    }

    @Test
    fun `each answer state reports its own tag`() {
        fun tagFor(state: QuizAnswerJankState) = quizSessionJankTags(
            screen = "lesson",
            phaseName = "quiz",
            answerState = state,
            hasRankChange = false
        ).first { it.first == "answer" }.second

        assertEquals("n/a", tagFor(QuizAnswerJankState.NotAnswering))
        assertEquals("answering", tagFor(QuizAnswerJankState.Answering))
        assertEquals("feedback_hidden", tagFor(QuizAnswerJankState.FeedbackHidden))
        assertEquals("feedback_revealed", tagFor(QuizAnswerJankState.FeedbackRevealed))
    }

    @Test
    fun `a rank change is reported as shown`() {
        val tags = quizSessionJankTags("review", "active", QuizAnswerJankState.FeedbackHidden, hasRankChange = true)

        assertEquals("shown", tags.first { it.first == "rankChange" }.second)
    }

    /**
     * The harness merges tags by key, so two screens reporting the same phase must still be
     * distinguishable — that is what `screen` is for — and neither may drift into a different key set,
     * which is what would make one screen's log lines incomparable with the other's.
     */
    @Test
    fun `both screens report the same key set`() {
        val lesson = quizSessionJankTags("lesson", "study", QuizAnswerJankState.NotAnswering, false)
        val review = quizSessionJankTags("review", "active", QuizAnswerJankState.NotAnswering, false)

        assertEquals(lesson.map { it.first }, review.map { it.first })
        assertEquals(setOf(JANK_STATE_SCREEN, "phase", "answer", "rankChange"), lesson.map { it.first }.toSet())
    }
}
