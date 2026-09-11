package com.crazyfluff.shellfstudy.shared.feature.lesson

import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.network.SubjectType

/** How the picker orders the lesson queue. [DEFAULT] is [LessonPrioritizer]'s level-up-aware cadence;
 *  [KANJI_FIRST] is the opt-in override for a learner who wants this level's kanji out of the way
 *  before anything else. */
enum class LessonSort(val label: String) {
    DEFAULT("Default"),
    KANJI_FIRST("Kanji first")
}

/** Orders the available-lesson queue so the default batch (and any "select first N") favors
 *  leveling up quickly without dumping too much new material on the user at once.
 *
 *  Under [LessonSort.DEFAULT], radicals always come first — they're few, and every kanji depends on
 *  its radicals passing, so clearing them is the single highest-leverage thing to do. Kanji are
 *  interleaved with everything else (vocabulary, plus kanji from other levels) at a ratio that keeps
 *  kanji progressing without letting the review queue spike the way a kanji-only cram would a few
 *  days later — mirrors the ~1:2 kanji:vocab cadence WaniKani community strategy guides converge on.
 *  Once this level's 90% kanji-Guru threshold is already met, remaining kanji no longer block
 *  leveling up, so they're folded into the same low-urgency bucket as vocabulary.
 *
 *  [LessonSort.KANJI_FIRST] deliberately ignores all of that, level-up-ready check included: the
 *  learner asked for kanji, so every kanji comes first (in level/lesson-position order) and the rest
 *  keeps [LessonSort.DEFAULT]'s ordering. */
object LessonPrioritizer {
    private const val KANJI_INTERLEAVE_STRIDE = 3          // 1 kanji per 3 items (1 : 2 kanji:filler)
    private const val STRAINED_KANJI_INTERLEAVE_STRIDE = 5 // 1 kanji per 5 items once today's goal is hit

    fun prioritize(
        items: List<LessonItem>,
        levelUpProgress: LevelUpProgress,
        isStrained: Boolean,
        sort: LessonSort = LessonSort.DEFAULT
    ): List<LessonItem> {
        val sorted = items.sortedWith(compareBy({ it.level }, { it.lessonPosition }, { it.assignmentId }))
        return when (sort) {
            LessonSort.DEFAULT -> interleaveByLevelUpPriority(sorted, levelUpProgress, isStrained)
            LessonSort.KANJI_FIRST ->
                sorted.filter { it.subjectType == SubjectType.KANJI } +
                    interleaveByLevelUpPriority(
                        sorted.filterNot { it.subjectType == SubjectType.KANJI },
                        levelUpProgress,
                        isStrained
                    )
        }
    }

    private fun interleaveByLevelUpPriority(
        sorted: List<LessonItem>,
        levelUpProgress: LevelUpProgress,
        isStrained: Boolean
    ): List<LessonItem> {
        val radicals = sorted.filter { it.subjectType == SubjectType.RADICAL }
        // Once level-up is already secured, further kanji no longer gate anything — treat them as
        // ordinary backlog instead of racing them ahead of vocabulary.
        val kanjiPool = sorted.filter { it.subjectType == SubjectType.KANJI && !levelUpProgress.isLevelUpReady }
        val filler = sorted - radicals.toSet() - kanjiPool.toSet()

        val stride = if (isStrained) STRAINED_KANJI_INTERLEAVE_STRIDE else KANJI_INTERLEAVE_STRIDE
        return radicals + interleave(kanjiPool, filler, stride)
    }

    /** One item from [primary] every [stride] slots, [secondary] filling the rest; once either list
     * runs out, the remainder of the other is appended as-is. */
    private fun interleave(primary: List<LessonItem>, secondary: List<LessonItem>, stride: Int): List<LessonItem> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary

        val result = ArrayList<LessonItem>(primary.size + secondary.size)
        var primaryIndex = 0
        var secondaryIndex = 0
        while (primaryIndex < primary.size || secondaryIndex < secondary.size) {
            if (primaryIndex < primary.size && result.size % stride == 0) {
                result.add(primary[primaryIndex++])
            } else if (secondaryIndex < secondary.size) {
                result.add(secondary[secondaryIndex++])
            } else {
                result.add(primary[primaryIndex++])
            }
        }
        return result
    }
}
