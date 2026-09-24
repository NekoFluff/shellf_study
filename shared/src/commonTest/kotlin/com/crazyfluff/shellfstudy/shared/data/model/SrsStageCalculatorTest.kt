package com.crazyfluff.shellfstudy.shared.data.model

import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.network.SrsStageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SrsStageCalculatorTest {

    private val srsSystem = SrsSystemEntity(
        id = 0,
        name = "Default",
        unlockingStagePosition = 0,
        startingStagePosition = 1,
        passingStagePosition = 5,
        burningStagePosition = 9,
        stages = listOf(
            SrsStageData(position = 1, interval = 4, intervalUnit = "hours"),
            SrsStageData(position = 2, interval = 8, intervalUnit = "hours"),
            SrsStageData(position = 5, interval = 1, intervalUnit = "weeks"),
            SrsStageData(position = 9, interval = null, intervalUnit = null)
        )
    )

    @Test
    fun correctAnswerAdvancesOneStage() {
        assertEquals(4, SrsStageCalculator.nextStageOnCorrect(3, srsSystem))
    }

    @Test
    fun correctAnswerNeverAdvancesPastBurning() {
        assertEquals(9, SrsStageCalculator.nextStageOnCorrect(9, srsSystem))
    }

    @Test
    fun incorrectAnswerAtApprenticeDropsOneStage() {
        assertEquals(2, SrsStageCalculator.nextStageOnIncorrect(3, incorrect = 1, srsSystem = srsSystem))
    }

    /**
     * WaniKani's penalty doubles at Guru I, and `ceil(incorrect / 2)` means a *pair* of misses costs
     * the same as one — so every Guru-and-above miss costs exactly two stages, never the 3- or
     * 4-stage plunge the old hardcoded table produced for Master/Enlightened.
     */
    @Test
    fun incorrectAnswerAtGuruOrAboveAlwaysDropsExactlyTwoStages() {
        for (stage in 5..9) {
            assertEquals(
                stage - 2,
                SrsStageCalculator.nextStageOnIncorrect(stage, incorrect = 1, srsSystem = srsSystem),
                "one miss from stage $stage"
            )
            assertEquals(
                stage - 2,
                SrsStageCalculator.nextStageOnIncorrect(stage, incorrect = 2, srsSystem = srsSystem),
                "two misses from stage $stage (ceil(2/2) == ceil(1/2))"
            )
        }
    }

    @Test
    fun incorrectAnswerAtMasterDropsToGuruOne() {
        assertEquals(5, SrsStageCalculator.nextStageOnIncorrect(7, incorrect = 1, srsSystem = srsSystem))
    }

    @Test
    fun incorrectAnswerAtEnlightenedDropsToGuruTwo() {
        assertEquals(6, SrsStageCalculator.nextStageOnIncorrect(8, incorrect = 1, srsSystem = srsSystem))
    }

    @Test
    fun incorrectAnswerNeverDropsBelowTheStartingStage() {
        assertEquals(1, SrsStageCalculator.nextStageOnIncorrect(1, incorrect = 1, srsSystem = srsSystem))
        // Even a triple miss floors rather than going negative.
        assertEquals(1, SrsStageCalculator.nextStageOnIncorrect(5, incorrect = 9, srsSystem = srsSystem))
    }

    @Test
    fun incorrectAnswerAtGuruIDropsTwoStages() {
        assertEquals(3, SrsStageCalculator.nextStageOnIncorrect(5, incorrect = 1, srsSystem = srsSystem))
    }

    @Test
    fun eachAdditionalPairOfMissesCostsAnotherPenaltyStep() {
        // Guru I (5): ceil(n/2) * 2, floored at 1.
        assertEquals(3, SrsStageCalculator.nextStageOnIncorrect(5, incorrect = 1, srsSystem = srsSystem))
        assertEquals(3, SrsStageCalculator.nextStageOnIncorrect(5, incorrect = 2, srsSystem = srsSystem))
        assertEquals(1, SrsStageCalculator.nextStageOnIncorrect(5, incorrect = 3, srsSystem = srsSystem))
        assertEquals(1, SrsStageCalculator.nextStageOnIncorrect(5, incorrect = 4, srsSystem = srsSystem))
        // Apprentice III (3) is below Guru, so the penalty factor stays 1.
        assertEquals(2, SrsStageCalculator.nextStageOnIncorrect(3, incorrect = 1, srsSystem = srsSystem))
        assertEquals(2, SrsStageCalculator.nextStageOnIncorrect(3, incorrect = 2, srsSystem = srsSystem))
        assertEquals(1, SrsStageCalculator.nextStageOnIncorrect(3, incorrect = 3, srsSystem = srsSystem))
    }

    /** A grade with no wrong answers must not demote, however it reached this function. */
    @Test
    fun zeroIncorrectLeavesTheStageUnchanged() {
        for (stage in 1..9) {
            assertEquals(stage, SrsStageCalculator.nextStageOnIncorrect(stage, incorrect = 0, srsSystem = srsSystem))
        }
    }

    /**
     * The full WaniKani demotion table, stage by stage — the regression lock for the rank-down chip.
     * Source: WaniKani knowledge base "WaniKani's SRS Stages".
     */
    @Test
    fun oneMissMatchesWaniKaniPublishedDemotionTable() {
        val expected = mapOf(1 to 1, 2 to 1, 3 to 2, 4 to 3, 5 to 3, 6 to 4, 7 to 5, 8 to 6, 9 to 7)
        expected.forEach { (from, to) ->
            assertEquals(to, SrsStageCalculator.nextStageOnIncorrect(from, incorrect = 1, srsSystem = srsSystem), "from $from")
        }
    }

    @Test
    fun availableAtForAddsTheStagesInterval() {
        val from = Instant.parse("2026-01-01T00:00:00.00Z")
        val result = SrsStageCalculator.availableAtFor(1, srsSystem, from)
        assertEquals(from + 4.hours, result)
    }

    @Test
    fun availableAtForIsNullForABurnedStageWithNoInterval() {
        assertNull(SrsStageCalculator.availableAtFor(9, srsSystem, Clock.System.now()))
    }

    @Test
    fun availableAtForIsNullForAStagePositionTheSrsSystemHasNoEntryFor() {
        assertNull(SrsStageCalculator.availableAtFor(3, srsSystem, Clock.System.now()))
    }

    @Test
    fun availableAtForConvertsWeeksIntervalToSevenDays() {
        val from = Instant.parse("2026-01-01T00:00:00.00Z")
        val result = SrsStageCalculator.availableAtFor(5, srsSystem, from)
        assertEquals(from + 7.days, result)
    }

    @Test
    fun availableAtForConvertsMonthsIntervalToThirtyDays() {
        val monthsSrsSystem = SrsSystemEntity(
            id = 0, name = "Test", unlockingStagePosition = 0, startingStagePosition = 1,
            passingStagePosition = 5, burningStagePosition = 9,
            stages = listOf(SrsStageData(position = 3, interval = 1, intervalUnit = "months"))
        )
        val from = Instant.parse("2026-01-01T00:00:00.00Z")
        val result = SrsStageCalculator.availableAtFor(3, monthsSrsSystem, from)
        assertEquals(from + 30.days, result)
    }

    @Test
    fun availableAtForReturnsNullForUnknownIntervalUnit() {
        val unknownSrsSystem = SrsSystemEntity(
            id = 0, name = "Test", unlockingStagePosition = 0, startingStagePosition = 1,
            passingStagePosition = 5, burningStagePosition = 9,
            stages = listOf(SrsStageData(position = 3, interval = 1, intervalUnit = "fortnights"))
        )
        assertNull(SrsStageCalculator.availableAtFor(3, unknownSrsSystem, Clock.System.now()))
    }

    @Test
    fun correctAnswerAtStartingStageAdvancesToNextStage() {
        assertEquals(2, SrsStageCalculator.nextStageOnCorrect(1, srsSystem))
    }
}
