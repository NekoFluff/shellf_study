package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.FriendStats
import com.crazyfluff.shellfstudy.shared.data.model.LevelTimelinePoint
import com.crazyfluff.shellfstudy.shared.data.model.SrsCounts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FriendDetailsStatsTest {

    private fun stats(level: Int, daysSinceStart: Int?, timeline: List<LevelTimelinePoint>) = FriendStats(
        friendEntryId = "f",
        nickname = "Mei",
        username = "mei",
        level = level,
        reviewAccuracy = null,
        avgDaysPerLevel = null,
        daysSinceStart = daysSinceStart,
        levelTimeline = timeline,
        isCurrentUser = false,
        rosterIndex = 1
    )

    @Test
    fun daysOnCurrentLevel_countsFromTheLastLevelUpToIt() {
        val timeline = listOf(LevelTimelinePoint(0, 1), LevelTimelinePoint(9, 2), LevelTimelinePoint(20, 3))

        assertEquals(12, stats(level = 3, daysSinceStart = 32, timeline = timeline).daysOnCurrentLevel)
    }

    @Test
    fun daysOnCurrentLevel_usesTheLatestUnlockAfterAReset() {
        // Reset from level 3 back to 2, then levelled up to 3 again on day 50.
        val timeline = listOf(
            LevelTimelinePoint(0, 2),
            LevelTimelinePoint(10, 3),
            LevelTimelinePoint(40, 2),
            LevelTimelinePoint(50, 3)
        )

        assertEquals(5, stats(level = 3, daysSinceStart = 55, timeline = timeline).daysOnCurrentLevel)
    }

    @Test
    fun daysOnCurrentLevel_isUnknownWithoutTheLevelsStart() {
        assertNull(stats(level = 3, daysSinceStart = 30, timeline = emptyList()).daysOnCurrentLevel)
        val levelThreeOnly = listOf(LevelTimelinePoint(0, 3))
        assertNull(stats(level = 4, daysSinceStart = 30, timeline = levelThreeOnly).daysOnCurrentLevel)
        assertNull(stats(level = 3, daysSinceStart = null, timeline = levelThreeOnly).daysOnCurrentLevel)
    }

    @Test
    fun srsTotals_bucketTheSameWayAsSingleStages() {
        assertEquals(
            SrsCounts(apprentice = 7, guru = 3, master = 0, enlightened = 2, burned = 10),
            countSrsStagesFromTotals(mapOf(0 to 50, 1 to 3, 4 to 4, 5 to 1, 6 to 2, 8 to 2, 9 to 10))
        )
    }

    @Test
    fun srsStages_bucketAsWaniKaniDoes_andLockedCountsForNothing() {
        val stages = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 9)

        assertEquals(
            SrsCounts(apprentice = 4, guru = 2, master = 1, enlightened = 1, burned = 2),
            countSrsStages(stages)
        )
    }
}
