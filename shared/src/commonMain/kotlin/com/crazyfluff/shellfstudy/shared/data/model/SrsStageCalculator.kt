package com.crazyfluff.shellfstudy.shared.data.model

import com.crazyfluff.shellfstudy.shared.database.SrsSystemEntity
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

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

    /** Mirrors WaniKani's own SRS penalty table: Apprentice drops 1 stage, Guru drops 2, Master
     *  drops 3, Enlightened drops 4 — never below the starting (unlocked-review) stage. */
    fun nextStageOnIncorrect(currentStage: Int, srsSystem: SrsSystemEntity): Int {
        val decrement = when {
            currentStage <= srsSystem.startingStagePosition -> 0
            currentStage <= 4 -> 1
            currentStage <= 6 -> 2
            currentStage == 7 -> 3
            else -> 4
        }
        return (currentStage - decrement).coerceAtLeast(srsSystem.startingStagePosition)
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
