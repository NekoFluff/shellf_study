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
        assertEquals(2, SrsStageCalculator.nextStageOnIncorrect(3, srsSystem))
    }

    @Test
    fun incorrectAnswerAtGuruDropsTwoStages() {
        assertEquals(4, SrsStageCalculator.nextStageOnIncorrect(6, srsSystem))
    }

    @Test
    fun incorrectAnswerAtMasterDropsThreeStages() {
        assertEquals(4, SrsStageCalculator.nextStageOnIncorrect(7, srsSystem))
    }

    @Test
    fun incorrectAnswerAtEnlightenedDropsFourStages() {
        assertEquals(4, SrsStageCalculator.nextStageOnIncorrect(8, srsSystem))
    }

    @Test
    fun incorrectAnswerNeverDropsBelowTheStartingStage() {
        assertEquals(1, SrsStageCalculator.nextStageOnIncorrect(1, srsSystem))
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

    @Test
    fun incorrectAnswerAtGuruIDropsTwoStages() {
        assertEquals(3, SrsStageCalculator.nextStageOnIncorrect(5, srsSystem))
    }
}
