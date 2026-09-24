package com.crazyfluff.shellfstudy.shared.data.model

import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Guru I — the stage at which WaniKani's demotion penalty doubles, and the stage that counts
 *  toward level progression. */
private const val GURU_STAGE_POSITION = 5

/**
 * Predicts the next SRS stage locally, before any network round trip — used to patch the cached
 * assignment immediately when an item is graded/started, so the UI reflects progress instantly
 * regardless of connectivity. This is only ever a prediction: the server's authoritative response
 * (once the outbox actually syncs) always overwrites it, so a wrong guess here is at worst a few
 * seconds of UI flicker, never a lasting inconsistency.
 */
object SrsStageCalculator {

    fun nextStageOnCorrect(currentStage: Int, srsSystem: SrsSystemEntity): Int =
        (currentStage + 1).coerceAtMost(srsSystem.burningStagePosition)

    /**
     * WaniKani's own published demotion rule (knowledge base, "WaniKani's SRS Stages"):
     *
     *     newStage = max(startingStage, currentStage - ceil(incorrect / 2) * penalty)
     *     penalty  = 2 when currentStage >= Guru I (5), else 1
     *
     * Two consequences worth spelling out, because neither is obvious from the arithmetic:
     *
     *  - `ceil(incorrect / 2)` means a *pair* of misses costs the same as one, so the penalty only
     *    ever doubles at Guru and above. That is why a single miss on a Guru+ item costs exactly two
     *    stages (Guru I -> Apprentice III, Master -> Guru I, Enlightened -> Guru II) and never the
     *    3- or 4-stage plunge this used to hardcode for Master/Enlightened/Burned.
     *  - The floor is WaniKani's start-of-reviews stage (1), not zero: a miss at Apprentice I costs
     *    nothing. The caller's `from != to` guard is what suppresses the chip in that case.
     *
     * [incorrect] is the number of wrong answers given for this item *in this session*, which
     * WaniKani receives in `incorrect_meaning_answers` + `incorrect_reading_answers`. Passing a
     * boolean-derived 0/1 here (the old behaviour) silently under-drops any item the user missed
     * more than once, so callers must thread the real count through.
     */
    fun nextStageOnIncorrect(currentStage: Int, incorrect: Int, srsSystem: SrsSystemEntity): Int {
        if (incorrect <= 0) return currentStage
        val penalty = if (currentStage >= GURU_STAGE_POSITION) 2 else 1
        val adjustment = (incorrect + 1) / 2 // ceil(incorrect / 2), integer arithmetic
        return (currentStage - adjustment * penalty).coerceAtLeast(srsSystem.startingStagePosition)
    }

    /** Null if [stagePosition] is burned (or otherwise has no interval) — the item never becomes due again. */
    fun availableAtFor(stagePosition: Int, srsSystem: SrsSystemEntity, from: Instant): Instant? {
        val stage = srsSystem.stages.firstOrNull { it.position == stagePosition } ?: return null
        val interval = stage.interval ?: return null
        val duration = when (stage.intervalUnit) {
            "seconds" -> interval.seconds
            "minutes" -> interval.minutes
            "hours" -> interval.hours
            "days" -> interval.days
            "weeks" -> (interval * 7).days
            "months" -> (interval * 30).days
            else -> return null
        }
        return from + duration
    }
}
