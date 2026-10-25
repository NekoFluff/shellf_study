package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.LevelUpPath
import com.crazyfluff.shellfstudy.shared.database.LevelUpProjectionRow
import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.network.SrsStageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class LevelUpProjectionTest {

    private val standard = SrsSystemEntity(
        id = 1, name = "Standard", unlockingStagePosition = 0, startingStagePosition = 1,
        passingStagePosition = 5, burningStagePosition = 9,
        stages = listOf(
            SrsStageData(position = 1, interval = 4, intervalUnit = "hours"),
            SrsStageData(position = 2, interval = 8, intervalUnit = "hours"),
            SrsStageData(position = 3, interval = 23, intervalUnit = "hours"),
            SrsStageData(position = 4, interval = 47, intervalUnit = "hours"),
            SrsStageData(position = 5, interval = 1, intervalUnit = "weeks")
        )
    )

    private val systems = mapOf(1L to standard)

    /** Lesson to Guru on the standard system: 4 + 8 + 23 + 47 hours. */
    private val climb = 82.hours

    private val now = Instant.parse("2026-10-04T10:37:00Z")

    /** On the hour, so the climb isn't shifted by hourly truncation. */
    private val currentLevelUpAt = Instant.parse("2026-10-08T15:00:00Z")

    private val currentPath = LevelUpPath(
        kanjiTotal = 10,
        alreadyGuruCount = 5,
        upcomingGuruTimes = List(5) { currentLevelUpAt },
        computedAt = now
    )

    private var nextId = 1L

    /** One radical and one kanji built from it — the kanji waits for the radical's Guru. */
    private fun gatedLevel(level: Int, srsSystemId: Long = 1): List<LevelUpProjectionRow> {
        val radicalId = nextId++
        return listOf(
            LevelUpProjectionRow(level, radicalId, "radical", srsSystemId, emptyList()),
            LevelUpProjectionRow(level, nextId++, "kanji", srsSystemId, listOf(radicalId))
        )
    }

    private fun project(rows: List<LevelUpProjectionRow>, through: Int, current: LevelUpPath = currentPath) =
        LevelUpPathCalculator.projectFutureLevels(current, currentLevel = 5, rows, systems, through)

    @Test
    fun nextLevelUnlocksAtTheCurrentLevelUpAndItsKanjiWaitForTheirRadicals() {
        val projected = project(gatedLevel(6), through = 6).single()

        assertEquals(6, projected.level)
        assertEquals(currentLevelUpAt, projected.unlockAt)
        assertEquals(currentLevelUpAt + climb + climb, projected.levelUpAt)
        assertEquals(now, projected.computedAt)
    }

    @Test
    fun kanjiWithRadicalsFromEarlierLevelsStartRightAway() {
        val rows = listOf(LevelUpProjectionRow(6, 100, "kanji", 1, listOf(1)))

        assertEquals(currentLevelUpAt + climb, project(rows, through = 6).single().levelUpAt)
    }

    @Test
    fun levelsChainEachUnlockingAtThePreviousLevelUp() {
        val projected = project(gatedLevel(6) + gatedLevel(7) + gatedLevel(8), through = 8)

        assertEquals(listOf(6, 7, 8), projected.map { it.level })
        projected.zipWithNext { previous, next -> assertEquals(previous.levelUpAt, next.unlockAt) }
        assertEquals(currentLevelUpAt + climb * 6, projected.last().levelUpAt)
    }

    @Test
    fun chainStopsAfterTheFirstLevelWhoseLevelUpIsUnknown() {
        // Level 7's SRS system isn't cached, so level 8 can't be placed.
        val projected = project(gatedLevel(6) + gatedLevel(7, srsSystemId = 99) + gatedLevel(8), through = 8)

        assertEquals(listOf(6, 7), projected.map { it.level })
        assertNull(projected.last().levelUpAt)
    }

    @Test
    fun levelWithNoCachedSubjectsStopsTheChain() {
        val projected = project(gatedLevel(7), through = 7)

        assertEquals(listOf(6), projected.map { it.level })
        assertNull(projected.single().levelUpAt)
    }

    @Test
    fun readyCurrentLevelStartsTheChainNow() {
        val ready = currentPath.copy(alreadyGuruCount = 9, upcomingGuruTimes = emptyList())

        assertEquals(now, project(gatedLevel(6), through = 6, current = ready).single().unlockAt)
    }

    @Test
    fun unknownCurrentLevelUpProjectsNothing() {
        val unknown = currentPath.copy(upcomingGuruTimes = emptyList())

        assertTrue(project(gatedLevel(6), through = 6, current = unknown).isEmpty())
    }
}
