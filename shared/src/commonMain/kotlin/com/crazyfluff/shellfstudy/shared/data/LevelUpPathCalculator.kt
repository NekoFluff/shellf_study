package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.data.model.LevelUpPath
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpStep
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.data.model.SrsStageCalculator
import com.crazyfluff.shellfstudy.shared.database.LevelUpPathRow
import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import kotlin.time.Instant

/** Guru I — the stage that counts toward leveling up, matching [AssignmentStatsRepository.observeLevelUpProgress]. */
private val GURU_STAGE = SrsStage.GURU_1.raw

/**
 * Works out the fastest possible level-up: the time each of a level's kanji could reach Guru if every
 * lesson is done the moment it unlocks and every review is answered correctly the moment it comes due.
 *
 * Per item, from where it stands now:
 *  - **In reviews** at stage `c`: the next review happens at `max(now, availableAt)`, and each pass
 *    after that waits out the interval of the stage it lands on, until the review that takes it from
 *    Apprentice IV to Guru. That review's time is the item's Guru time.
 *  - **Lesson pending**: the lesson happens now, then the same climb from stage 1.
 *  - **Locked kanji**: unlocks when the slowest of its radicals at this level reaches Guru, then
 *    climbs like a pending lesson. A component that isn't at this level (or isn't cached) is taken as
 *    already met — WaniKani only ever gates a kanji on radicals, and those almost always come from
 *    this level or earlier ones the user has long since passed.
 *
 * Each scheduled review is truncated to the start of its hour, as WaniKani does: a lesson done at
 * 10:37 with a 4-hour interval comes due at 14:00, not 14:37.
 *
 * The level-up time is then the [LevelUpPath.requiredCount]-th earliest kanji — WaniKani's 90% rule.
 */
object LevelUpPathCalculator {

    fun calculate(rows: List<LevelUpPathRow>, srsSystems: Map<Long, SrsSystemEntity>, now: Instant): LevelUpPath {
        val radicals = rows.filter { it.subjectType == RADICAL }
        val kanji = rows.filter { it.subjectType == KANJI }

        // A radical's guruAt is null when its path can't be worked out (its SRS system isn't cached);
        // a kanji waiting on it is then unknown too, rather than silently treated as unblocked.
        val radicalPaths = radicals.associate { row -> row.subjectId to itemPath(row, srsSystems, now, unlockAt = now) }
        val radicalGuruAt = radicalPaths.mapValues { it.value.guruAt }
        val kanjiPaths = kanji.filter { it.srsStage < GURU_STAGE }.associate { row ->
            val unlockAt = if (row.isLocked) lockedUnlockAt(row, radicalGuruAt, now) else now
            row.subjectId to (unlockAt?.let { itemPath(row, srsSystems, now, it) } ?: ItemPath.UNKNOWN)
        }

        val pending = (radicalPaths + kanjiPaths).filterValues { it.inProgress }
        val path = LevelUpPath(
            kanjiTotal = kanji.size,
            alreadyGuruCount = kanji.count { it.srsStage >= GURU_STAGE },
            upcomingGuruTimes = kanjiPaths.values.mapNotNull { it.guruAt }.sorted(),
            computedAt = now,
            guruAtBySubject = pending.mapNotNull { (id, p) -> p.guruAt?.let { id to it } }.toMap()
        )
        if (path.levelUpAt == null) return path

        val decidingKanji = decidingKanji(kanji, kanjiPaths, stillNeeded = path.requiredCount - path.alreadyGuruCount)
        val decidingRadicals = decidingKanji.filter { it.isLocked }.flatMap { row ->
            slowestPendingComponents(row, radicalPaths)
        }
        val deciding = decidingKanji.map { it.subjectId }.toSet() + decidingRadicals
        return path.copy(
            decidingSubjectIds = deciding,
            nextDecidingStep = nextStep(deciding, rows, pending)
        )
    }

    /**
     * Exactly the [stillNeeded] kanji the level-up waits on: the earliest to reach Guru.
     *
     * Not "every kanji at Guru by the level-up time". Kanji learned together reach Guru in the same
     * hour, so the level-up hour is usually shared by more kanji than are needed, and counting them all
     * asked the learner for reviews the level-up didn't depend on, along with the radicals gating the
     * spare kanji. Among kanji tied on that hour, the ones that are cheapest to get there win:
     * already unlocked (no radicals to wait on), then furthest along, then by id so the choice is
     * stable from one computation to the next.
     */
    private fun decidingKanji(
        kanji: List<LevelUpPathRow>,
        kanjiPaths: Map<Long, ItemPath>,
        stillNeeded: Int
    ): List<LevelUpPathRow> = kanji
        .mapNotNull { row -> kanjiPaths[row.subjectId]?.guruAt?.let { row to it } }
        .sortedWith(
            compareBy<Pair<LevelUpPathRow, Instant>> { (_, guruAt) -> guruAt }
                .thenBy { (row, _) -> row.isLocked }
                .thenByDescending { (row, _) -> row.srsStage }
                .thenBy { (row, _) -> row.subjectId }
        )
        .take(stillNeeded)
        .map { (row, _) -> row }

    /** The not-yet-Guru radicals that set a locked kanji's unlock time: those reaching Guru last. */
    private fun slowestPendingComponents(row: LevelUpPathRow, radicalPaths: Map<Long, ItemPath>): List<Long> {
        val pendingComponents = row.componentSubjectIds.mapNotNull { id ->
            radicalPaths[id]?.takeIf { it.inProgress }?.guruAt?.let { id to it }
        }
        val latest = pendingComponents.maxOfOrNull { it.second } ?: return emptyList()
        return pendingComponents.filter { it.second == latest }.map { it.first }
    }

    /** The soonest lesson or review among [deciding] items, plus every deciding item that can be done
     *  in the same clock hour (everything already due counts as "now"). Locked kanji have no step of
     *  their own yet — the radicals gating them are in [deciding] and carry it.
     *
     *  The step's time is the earliest *due* time in the group, not the clamped "now": an overdue
     *  session keeps the same [LevelUpStep.at] however often it's recomputed, which is what lets the
     *  level-up reminder recognise a session it has already announced. */
    private fun nextStep(deciding: Set<Long>, rows: List<LevelUpPathRow>, pending: Map<Long, ItemPath>): LevelUpStep? {
        val steps = rows.filter { it.subjectId in deciding }.mapNotNull { row ->
            val item = pending[row.subjectId]
            val doAt = item?.firstStepAt
            val dueAt = item?.dueAt
            if (doAt == null || dueAt == null) {
                null
            } else {
                StepCandidate(row.subjectType, doAt, dueAt, item.firstStepIsLesson)
            }
        }
        val firstHour = steps.minOfOrNull { it.doAt }?.truncatedToHour() ?: return null
        val inHour = steps.filter { it.doAt.truncatedToHour() == firstHour }
        return LevelUpStep(
            at = inHour.minOf { it.dueAt },
            radicalCount = inHour.count { it.subjectType == RADICAL },
            kanjiCount = inHour.count { it.subjectType == KANJI },
            lessonCount = inHour.count { it.isLesson }
        )
    }

    private class StepCandidate(val subjectType: String, val doAt: Instant, val dueAt: Instant, val isLesson: Boolean)

    /** When a locked kanji unlocks: once the slowest of its radicals at this level reaches Guru. Null
     *  when one of those radicals' own path is unknown. */
    private fun lockedUnlockAt(row: LevelUpPathRow, radicalGuruAt: Map<Long, Instant?>, now: Instant): Instant? {
        val componentTimes = row.componentSubjectIds.filter { it in radicalGuruAt }.map { radicalGuruAt[it] }
        return if (null in componentTimes) null else (componentTimes.filterNotNull() + now).max()
    }

    /** Where [row] stands on the fastest path. A locked kanji has no first step yet (it can't be
     *  studied until [unlockAt]), and an item whose SRS system isn't cached has no Guru time. */
    private fun itemPath(
        row: LevelUpPathRow,
        srsSystems: Map<Long, SrsSystemEntity>,
        now: Instant,
        unlockAt: Instant
    ): ItemPath {
        if (row.srsStage >= GURU_STAGE) return ItemPath(guruAt = now, firstStepAt = null, inProgress = false)
        val system = srsSystems[row.srsSystemId]
        val isLesson = row.srsStage == 0
        // The first step is the lesson (stage 0) or the next review; it moves the item up one stage.
        // It's due when the item unlocked (lesson) or comes up for review, and happens no earlier
        // than now.
        val dueAt = if (isLesson) {
            row.unlockedAt?.let(Instant::parse) ?: unlockAt
        } else {
            row.availableAt?.let(Instant::parse) ?: now
        }
        val firstStepAt = maxOf(now, dueAt)
        val canStudyYet = !(row.subjectType == KANJI && row.isLocked && firstStepAt > now)
        return ItemPath(
            guruAt = system?.let { climbToGuru(row.srsStage, firstStepAt, it) },
            firstStepAt = firstStepAt.takeIf { canStudyYet },
            dueAt = dueAt.takeIf { canStudyYet },
            inProgress = true,
            firstStepIsLesson = isLesson
        )
    }

    /** Each step lands on a stage and waits out that stage's interval before the next one; the step
     *  that lands on Guru is the answer. From Apprentice IV that's the very first step. */
    private fun climbToGuru(fromStage: Int, firstStepAt: Instant, system: SrsSystemEntity): Instant? =
        ((fromStage + 1) until GURU_STAGE).fold<Int, Instant?>(firstStepAt) { time, stage ->
            time?.let { SrsStageCalculator.availableAtFor(stage, system, it)?.truncatedToHour() }
        }

    private data class ItemPath(
        val guruAt: Instant?,
        /** When the first step can happen: [dueAt], or now if that's passed. */
        val firstStepAt: Instant?,
        /** When the first step became (or becomes) available. */
        val dueAt: Instant? = null,
        /** Not yet at Guru — still has steps to take. */
        val inProgress: Boolean,
        val firstStepIsLesson: Boolean = false
    ) {
        companion object {
            val UNKNOWN = ItemPath(guruAt = null, firstStepAt = null, inProgress = true)
        }
    }

    /** Locked = no assignment yet, or one WaniKani created but hasn't unlocked. Radicals are never
     *  treated as locked: a level's radicals all unlock with the level itself. */
    private val LevelUpPathRow.isLocked: Boolean get() = srsStage == 0 && unlockedAt == null

    private fun Instant.truncatedToHour(): Instant =
        Instant.fromEpochSeconds((epochSeconds / SECONDS_PER_HOUR) * SECONDS_PER_HOUR)

    private const val RADICAL = "radical"
    private const val KANJI = "kanji"
    private const val SECONDS_PER_HOUR = 3600L
}
