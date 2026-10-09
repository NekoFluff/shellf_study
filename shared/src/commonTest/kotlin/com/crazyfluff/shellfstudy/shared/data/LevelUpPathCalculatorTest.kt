package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.LevelUpPath
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpStep
import com.crazyfluff.shellfstudy.shared.database.LevelUpPathRow
import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import com.crazyfluff.shellfstudy.shared.network.SrsStageData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class LevelUpPathCalculatorTest {

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

    /** WaniKani's levels 1–2 system: the Apprentice intervals are roughly halved. */
    private val accelerated = SrsSystemEntity(
        id = 2, name = "Accelerated", unlockingStagePosition = 0, startingStagePosition = 1,
        passingStagePosition = 5, burningStagePosition = 9,
        stages = listOf(
            SrsStageData(position = 1, interval = 2, intervalUnit = "hours"),
            SrsStageData(position = 2, interval = 4, intervalUnit = "hours"),
            SrsStageData(position = 3, interval = 8, intervalUnit = "hours"),
            SrsStageData(position = 4, interval = 23, intervalUnit = "hours")
        )
    )

    private val systems = mapOf(1L to standard, 2L to accelerated)

    /** Deliberately not on the hour, so the hourly truncation is exercised. */
    private val now = Instant.parse("2026-10-04T10:37:00Z")

    private var nextId = 1L

    private fun kanji(
        srsStage: Int = 0,
        unlockedAt: String? = UNLOCKED,
        availableAt: String? = null,
        components: List<Long> = emptyList(),
        srsSystemId: Long = 1
    ) = LevelUpPathRow(nextId++, "kanji", srsSystemId, components, srsStage, unlockedAt, availableAt)

    private fun radical(id: Long, srsStage: Int, availableAt: String? = null, srsSystemId: Long = 1) =
        LevelUpPathRow(id, "radical", srsSystemId, emptyList(), srsStage, UNLOCKED, availableAt)

    private fun calculate(vararg rows: LevelUpPathRow): LevelUpPath =
        LevelUpPathCalculator.calculate(rows.toList(), systems, now)

    private fun singleGuruTime(row: LevelUpPathRow, vararg others: LevelUpPathRow): Instant =
        calculate(row, *others).upcomingGuruTimes.single()

    @Test
    fun kanjiAlreadyAtGuruCountsAsDoneAndHasNoUpcomingTime() {
        val path = calculate(kanji(srsStage = 5), kanji(srsStage = 7))

        assertEquals(2, path.alreadyGuruCount)
        assertEquals(emptyList(), path.upcomingGuruTimes)
    }

    @Test
    fun apprenticeFourReachesGuruAtItsNextReview() {
        assertEquals(
            Instant.parse("2026-10-04T15:00:00Z"),
            singleGuruTime(kanji(srsStage = 4, availableAt = "2026-10-04T15:00:00.000000Z"))
        )
    }

    @Test
    fun apprenticeThreeWaitsOutTheApprenticeFourInterval() {
        // Review at 03:00 → Apprentice IV, due 47h later; that review is the Guru one.
        assertEquals(
            Instant.parse("2026-10-07T02:00:00Z"),
            singleGuruTime(kanji(srsStage = 3, availableAt = "2026-10-05T03:00:00.000000Z"))
        )
    }

    @Test
    fun overdueReviewIsAssumedToHappenNowAndLaterReviewsLandOnTheHour() {
        // Reviewed now (10:37) → +23h = 09:37 → 09:00; +47h → Oct 7 08:00.
        assertEquals(
            Instant.parse("2026-10-07T08:00:00Z"),
            singleGuruTime(kanji(srsStage = 2, availableAt = "2026-10-04T08:00:00.000000Z"))
        )
    }

    @Test
    fun pendingLessonIsAssumedToBeDoneNow() {
        // 10:37 +4h → 14:00, +8h → 22:00, +23h → Oct 5 21:00, +47h → Oct 7 20:00.
        assertEquals(Instant.parse("2026-10-07T20:00:00Z"), singleGuruTime(kanji(srsStage = 0)))
    }

    @Test
    fun lockedKanjiUnlocksWhenItsSlowestRadicalReachesGuru() {
        val fast = radical(id = 100, srsStage = 4, availableAt = "2026-10-04T12:00:00.000000Z")
        val slow = radical(id = 101, srsStage = 4, availableAt = "2026-10-04T18:00:00.000000Z")
        val locked = kanji(unlockedAt = null, components = listOf(100, 101))

        // Unlocks 18:00 → 22:00 → Oct 5 06:00 → Oct 6 05:00 → Oct 8 04:00.
        assertEquals(Instant.parse("2026-10-08T04:00:00Z"), singleGuruTime(locked, fast, slow))
    }

    @Test
    fun lockedKanjiWhoseComponentIsNotAtThisLevelTreatsItAsMet() {
        assertEquals(
            Instant.parse("2026-10-07T20:00:00Z"),
            singleGuruTime(kanji(unlockedAt = null, components = listOf(999)))
        )
    }

    @Test
    fun levelUpIsTheNinetyPercentthEarliestKanji() {
        val rows = (1..10).map { hour ->
            kanji(srsStage = 4, availableAt = "2026-10-05T${hour.toString().padStart(2, '0')}:00:00.000000Z")
        }
        val path = LevelUpPathCalculator.calculate(rows.shuffled(), systems, now)

        assertEquals(9, path.requiredCount)
        assertEquals(Instant.parse("2026-10-05T09:00:00Z"), path.levelUpAt)
    }

    @Test
    fun alreadyGuruKanjiShortenHowManyUpcomingOnesAreNeeded() {
        // 33 kanji → 30 needed; 28 already there, so the 2nd upcoming one decides it.
        val rows = List(28) { kanji(srsStage = 5) } + listOf(
            kanji(srsStage = 4, availableAt = "2026-10-04T13:00:00.000000Z"),
            kanji(srsStage = 4, availableAt = "2026-10-04T16:00:00.000000Z"),
            kanji(srsStage = 4, availableAt = "2026-10-04T20:00:00.000000Z"),
            kanji(srsStage = 4, availableAt = "2026-10-04T23:00:00.000000Z"),
            kanji(srsStage = 0)
        )
        val path = LevelUpPathCalculator.calculate(rows, systems, now)

        assertEquals(30, path.requiredCount)
        assertEquals(Instant.parse("2026-10-04T16:00:00Z"), path.levelUpAt)
    }

    @Test
    fun levelThatIsAlreadyReadyHasNoLevelUpTime() {
        val rows = List(9) { kanji(srsStage = 5) } + kanji(srsStage = 1, availableAt = "2026-10-04T12:00:00.000000Z")

        assertNull(LevelUpPathCalculator.calculate(rows, systems, now).levelUpAt)
    }

    @Test
    fun acceleratedSystemUsesItsOwnIntervals() {
        // 10:37 +2h → 12:00, +4h → 16:00, +8h → Oct 5 00:00, +23h → Oct 5 23:00.
        assertEquals(Instant.parse("2026-10-05T23:00:00Z"), singleGuruTime(kanji(srsStage = 0, srsSystemId = 2)))
    }

    @Test
    fun kanjiWithUncachedSrsSystemIsLeftOutAndLevelUpTimeIsUnknown() {
        val path = calculate(kanji(srsStage = 0, srsSystemId = 42))

        assertEquals(1, path.kanjiTotal)
        assertEquals(emptyList(), path.upcomingGuruTimes)
        assertNull(path.levelUpAt)
    }

    @Test
    fun lockedKanjiWaitingOnRadicalWithUncachedSystemIsLeftOut() {
        val radical = radical(id = 100, srsStage = 2, srsSystemId = 42)

        val locked = kanji(unlockedAt = null, components = listOf(100))

        assertEquals(emptyList(), calculate(locked, radical).upcomingGuruTimes)
    }

    @Test
    fun emptyLevelHasNoKanjiAndNoLevelUpTime() {
        val path = calculate()

        assertEquals(0, path.kanjiTotal)
        assertNull(path.levelUpAt)
    }

    @Test
    fun guruTimesCoverRadicalsAndKanjiStillInProgressOnly() {
        val radical = radical(id = 100, srsStage = 4, availableAt = "2026-10-04T12:00:00.000000Z")
        val doneRadical = radical(id = 101, srsStage = 5)
        val pending = kanji(srsStage = 4, availableAt = "2026-10-04T15:00:00.000000Z")
        val done = kanji(srsStage = 6)

        val path = calculate(radical, doneRadical, pending, done)

        assertEquals(
            mapOf(
                100L to Instant.parse("2026-10-04T12:00:00Z"),
                pending.subjectId to Instant.parse("2026-10-04T15:00:00Z")
            ),
            path.guruAtBySubject
        )
    }

    @Test
    fun decidingKanjiAreTheOnesReachingGuruByTheLevelUpIncludingTies() {
        val hours = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 9, 11)
        val rows = hours.map { hour ->
            kanji(srsStage = 4, availableAt = "2026-10-05T${hour.toString().padStart(2, '0')}:00:00.000000Z")
        }
        val path = LevelUpPathCalculator.calculate(rows, systems, now)

        // 11 kanji → 10 needed; the 10th earliest is at 09:00, shared with the 9th.
        assertEquals(Instant.parse("2026-10-05T09:00:00Z"), path.levelUpAt)
        assertEquals(rows.take(10).map { it.subjectId }.toSet(), path.decidingSubjectIds)
    }

    @Test
    fun kanjiTiedOnTheLevelUpHourCountOnlyAsManyAsTheLevelUpNeeds() {
        // 11 kanji → 10 needed. Two reach Guru early and nine share 09:00, so 8 of those nine are
        // enough: counting all nine asked for a review the level-up didn't depend on.
        val early = listOf(1, 2).map { hour ->
            kanji(srsStage = 4, availableAt = "2026-10-05T0$hour:00:00.000000Z")
        }
        val tied = List(9) { kanji(srsStage = 4, availableAt = "2026-10-05T09:00:00.000000Z") }
        val path = LevelUpPathCalculator.calculate(early + tied, systems, now)

        assertEquals(Instant.parse("2026-10-05T09:00:00Z"), path.levelUpAt)
        assertEquals(10, path.decidingSubjectIds.size)
        assertTrue(path.decidingSubjectIds.containsAll(early.map { it.subjectId }))
    }

    @Test
    fun aTieIsSettledInFavourOfKanjiThatNeedNoRadicalsFirst() {
        // Both kanji reach Guru at the same time, but only one is needed: the unlocked one, not the
        // locked one whose radical would then count as deciding too.
        val radicalGate = radical(id = 100, srsStage = 5)
        val locked = kanji(unlockedAt = null, components = listOf(100))
        val unlocked = kanji(srsStage = 0)
        val alreadyGuru = List(8) { kanji(srsStage = 5) }

        val path = calculate(locked, unlocked, radicalGate, *alreadyGuru.toTypedArray())

        assertEquals(setOf(unlocked.subjectId), path.decidingSubjectIds)
    }

    @Test
    fun slowestRadicalGatingADecidingLockedKanjiIsDecidingAndFasterOneIsSpare() {
        val fast = radical(id = 100, srsStage = 4, availableAt = "2026-10-04T12:00:00.000000Z")
        val slow = radical(id = 101, srsStage = 4, availableAt = "2026-10-04T18:00:00.000000Z")
        val locked = kanji(unlockedAt = null, components = listOf(100, 101))

        val path = calculate(locked, fast, slow)

        assertEquals(setOf(locked.subjectId, 101L), path.decidingSubjectIds)
        assertEquals(LevelUpStep(Instant.parse("2026-10-04T18:00:00Z"), 1, 0, 0), path.nextDecidingStep)
    }

    @Test
    fun nextDecidingStepGroupsEveryDecidingItemDueInTheSameHour() {
        val path = calculate(
            kanji(srsStage = 3, availableAt = "2026-10-04T15:00:00.000000Z"),
            kanji(srsStage = 4, availableAt = "2026-10-04T15:00:00.000000Z"),
            kanji(srsStage = 1, availableAt = "2026-10-04T16:00:00.000000Z")
        )

        assertEquals(LevelUpStep(Instant.parse("2026-10-04T15:00:00Z"), 0, 2, 0), path.nextDecidingStep)
    }

    @Test
    fun pendingLessonsAndOverdueReviewsAreDueNow() {
        val path = calculate(
            kanji(srsStage = 0),
            kanji(srsStage = 2, availableAt = "2026-10-04T08:00:00.000000Z")
        )

        // Both can be done now; the step keeps the earliest time either became available (the lesson
        // unlocked on Oct 1), so recomputing it later doesn't make it look like a new session.
        assertEquals(LevelUpStep(Instant.parse(UNLOCKED), 0, 2, 1), path.nextDecidingStep)
    }

    @Test
    fun readyLevelHasNoDecidingItemsOrNextStep() {
        val rows = List(9) { kanji(srsStage = 5) } + kanji(srsStage = 1, availableAt = "2026-10-04T12:00:00.000000Z")
        val path = LevelUpPathCalculator.calculate(rows, systems, now)

        assertEquals(emptySet(), path.decidingSubjectIds)
        assertNull(path.nextDecidingStep)
    }

    private companion object {
        const val UNLOCKED = "2026-10-01T00:00:00.000000Z"
    }
}
