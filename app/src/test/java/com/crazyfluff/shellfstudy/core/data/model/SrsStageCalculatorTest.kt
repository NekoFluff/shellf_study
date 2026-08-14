package com.crazyfluff.shellfstudy.core.data.model

import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.network.SrsStageData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Duration
import java.time.Instant

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
    fun `correct answer advances one stage`() {
        assertThat(SrsStageCalculator.nextStageOnCorrect(3, srsSystem)).isEqualTo(4)
    }

    @Test
    fun `correct answer never advances past burning`() {
        assertThat(SrsStageCalculator.nextStageOnCorrect(9, srsSystem)).isEqualTo(9)
    }

    @Test
    fun `incorrect answer at apprentice drops one stage`() {
        assertThat(SrsStageCalculator.nextStageOnIncorrect(3, srsSystem)).isEqualTo(2)
    }

    @Test
    fun `incorrect answer at guru drops two stages`() {
        assertThat(SrsStageCalculator.nextStageOnIncorrect(6, srsSystem)).isEqualTo(4)
    }

    @Test
    fun `incorrect answer at master drops three stages`() {
        assertThat(SrsStageCalculator.nextStageOnIncorrect(7, srsSystem)).isEqualTo(4)
    }

    @Test
    fun `incorrect answer at enlightened drops four stages`() {
        assertThat(SrsStageCalculator.nextStageOnIncorrect(8, srsSystem)).isEqualTo(4)
    }

    @Test
    fun `incorrect answer never drops below the starting stage`() {
        assertThat(SrsStageCalculator.nextStageOnIncorrect(1, srsSystem)).isEqualTo(1)
    }

    @Test
    fun `availableAtFor adds the stage's interval`() {
        val from = Instant.parse("2026-01-01T00:00:00.00Z")
        val result = SrsStageCalculator.availableAtFor(1, srsSystem, from)
        assertThat(result).isEqualTo(from.plus(Duration.ofHours(4)))
    }

    @Test
    fun `availableAtFor is null for a burned stage with no interval`() {
        assertThat(SrsStageCalculator.availableAtFor(9, srsSystem, Instant.now())).isNull()
    }

    @Test
    fun `availableAtFor is null for a stage position the srs system has no entry for`() {
        assertThat(SrsStageCalculator.availableAtFor(3, srsSystem, Instant.now())).isNull()
    }
}
