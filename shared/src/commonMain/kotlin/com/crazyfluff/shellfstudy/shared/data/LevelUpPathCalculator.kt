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
        return path.copy(nextStep = nextStep(rows, pending, kanjiLeft = path.requiredCount - path.alreadyGuruCount))
    }

    /**
     * The soonest lesson or review session among the level's radicals and kanji still below Guru,
     * counting every one of them that can be done in the same clock hour (everything already due
     * counts as "now"). Locked kanji have no step of their own yet.
     *
     * Every radical below Guru counts, not just those gating a kanji: they're the same items the
     * rank-up review order pulls forward, so the reminder and the review queue agree on what's
     * waiting.
     *
     * The step's time is the earliest *due* time in the group, not the clamped "now": an overdue
     * session keeps the same [LevelUpStep.at] however often it's recomputed, which is what lets the
     * level-up reminder recognise a session it has already announced.
     */
    private fun nextStep(rows: List<LevelUpPathRow>, pending: Map<Long, ItemPath>, kanjiLeft: Int): LevelUpStep? {
        val steps = rows.mapNotNull { row ->
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
        fun count(type: String, lesson: Boolean) = inHour.count { it.subjectType == type && it.isLesson == lesson }
        return LevelUpStep(
            at = inHour.minOf { it.dueAt },
            kanjiReviews = count(KANJI, lesson = false),
            kanjiLessons = count(KANJI, lesson = true),
            radicalReviews = count(RADICAL, lesson = false),
            radicalLessons = count(RADICAL, lesson = true),
            kanjiLeft = kanjiLeft
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
