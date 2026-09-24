package com.crazyfluff.shellfstudy.core.data

import com.crazyfluff.shellfstudy.fakes.TestRepositories
import com.crazyfluff.shellfstudy.fakes.buildTestRepositories
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.network.SubjectType
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Regression lock for the rank up/down chip: drives the exact prediction the chip renders
 * ([AssignmentRepository.computeReviewRankChange], surfaced as [RankChange] through
 * ReviewViewModel.gradeAnswer -> QuizQuestionContent -> RankChangeChip) and compares it against
 * WaniKani's own published demotion rule.
 *
 * WaniKani's rule (knowledge base "WaniKani's SRS Stages", corroborated by wanilog.com's demotion
 * table):
 *
 *     newStage = max(1, stage - ceil(incorrect / 2) * penalty)
 *     penalty  = 2 when stage >= 5 (Guru I and above), else 1
 *
 * where `incorrect` is the number of wrong answers given for that item in the session.
 */
class RankChangeChipVerificationTest {

    private lateinit var server: MockWebServer
    private lateinit var repositories: TestRepositories

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repositories = buildTestRepositories(server.url("/").toString())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun reviewItemAtSrsStage(stage: Int) = ReviewItem(
        assignmentId = 101,
        subjectId = 1,
        subjectType = SubjectType.KANJI,
        characters = "口",
        level = 3,
        srsStage = stage,
        meanings = listOf("Mouth"),
        readings = listOf("くち"),
        srsSystemId = 0 // DEFAULT_TEST_SRS_SYSTEM — WaniKani's real stage layout, burning at 9
    )

    /** WaniKani's own answer for [incorrect] misses on an item at [stage]. */
    private fun waniKaniStageAfterWrongAnswers(stage: Int, incorrect: Int): Int {
        val penalty = if (stage >= 5) 2 else 1
        val adjustment = (incorrect + 1) / 2 // ceil(incorrect / 2)
        return (stage - adjustment * penalty).coerceAtLeast(1)
    }

    /** The `to` field of the chip's [RankChange] for a review of an item at [stage]. */
    private suspend fun chipPredictedStage(stage: Int, grade: ReviewGrade): Int? {
        repositories.assignmentRepository.warmSrsSystemCache()
        return repositories.assignmentRepository.computeReviewRankChange(reviewItemAtSrsStage(stage), grade)?.to?.raw
    }

    private fun gradeWithMisses(meaningMisses: Int, readingMisses: Int = 0) = ReviewGrade(
        meaningCorrect = meaningMisses == 0,
        readingCorrect = readingMisses == 0,
        incorrectMeaning = meaningMisses,
        incorrectReading = readingMisses
    )

    /**
     * The chip's direction: a clean pass always raises the stage, a miss never does — at every stage,
     * so the up/down arrow and `isRankUp` can't invert.
     */
    @Test
    fun `rank-down direction and rank-up direction are correct at every stage`() = runTest {
        for (stage in 1..9) {
            val down = repositories.assignmentRepository.computeReviewRankChange(
                reviewItemAtSrsStage(stage),
                gradeWithMisses(meaningMisses = 1)
            )
            val up = repositories.assignmentRepository.computeReviewRankChange(
                reviewItemAtSrsStage(stage),
                gradeWithMisses(meaningMisses = 0)
            )

            // Never a rank up on a miss...
            assertThat(down == null || !down.isRankUp).isTrue()
            // ...and never a rank down on a clean pass (an already-Burned item simply stays put).
            assertThat(up == null || up.to.raw >= stage).isTrue()
            assertThat(up == null || !up.isRankUp || up.to.raw > stage).isTrue()
        }
    }

    /**
     * The regression this file was created for: the old hardcoded penalty table dropped Master to
     * Apprentice IV (a 3-stage fall) and Enlightened/Burned by 4, where WaniKani drops every Guru+
     * item by exactly two.
     */
    @Test
    fun `rank-down magnitude matches WaniKani's demotion table for a single miss at every stage`() = runTest {
        val expected = mapOf(1 to 1, 2 to 1, 3 to 2, 4 to 3, 5 to 3, 6 to 4, 7 to 5, 8 to 6, 9 to 7)

        expected.forEach { (stage, expectedStage) ->
            val actual = chipPredictedStage(stage, gradeWithMisses(meaningMisses = 1))
            assertThat(actual).isEqualTo(expectedStage)
            assertThat(waniKaniStageAfterWrongAnswers(stage, 1)).isEqualTo(expectedStage)
        }
    }

    /**
     * The second divergence: the app used to report every miss as a single wrong answer, so a
     * question missed several times under-dropped against WaniKani's `ceil(incorrect / 2)` rule.
     * The chip must now track the real count.
     */
    @Test
    fun `rank-down magnitude matches WaniKani for repeated misses on the same question`() = runTest {
        for (stage in 1..9) {
            for (incorrect in 1..4) {
                val actual = chipPredictedStage(stage, gradeWithMisses(meaningMisses = incorrect))
                assertThat(actual).isEqualTo(waniKaniStageAfterWrongAnswers(stage, incorrect))
            }
        }
    }

    /** Meaning and reading misses combine into one count before WaniKani's single penalty is applied. */
    @Test
    fun `rank-down magnitude combines meaning and reading misses into one count`() = runTest {
        // Two misses total (one per question) is a single penalty step, not two.
        val actual = chipPredictedStage(5, gradeWithMisses(meaningMisses = 1, readingMisses = 1))
        assertThat(actual).isEqualTo(3)
        assertThat(actual).isEqualTo(waniKaniStageAfterWrongAnswers(5, 2))

        // Three misses total (meaning twice, reading once) is two penalty steps.
        val actualTriple = chipPredictedStage(5, gradeWithMisses(meaningMisses = 2, readingMisses = 1))
        assertThat(actualTriple).isEqualTo(waniKaniStageAfterWrongAnswers(5, 3))
    }

    /**
     * A miss at Apprentice I costs nothing under WaniKani's stage-1 floor, so there is genuinely no
     * rank change to show — `gradeAnswer` filters `from == to` and the chip never appears.
     */
    @Test
    fun `a miss at Apprentice I produces no rank change at all`() = runTest {
        repositories.assignmentRepository.warmSrsSystemCache()
        val change = repositories.assignmentRepository.computeReviewRankChange(
            reviewItemAtSrsStage(SrsStage.APPRENTICE_1.raw),
            gradeWithMisses(meaningMisses = 3)
        )

        // Present but idempotent — the caller's `from != to` guard is what hides the chip.
        assertThat(change?.from).isEqualTo(SrsStage.APPRENTICE_1)
        assertThat(change?.to).isEqualTo(SrsStage.APPRENTICE_1)
    }
}
