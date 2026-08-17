package com.crazyfluff.shellfstudy.shared.feature.lesson

import com.crazyfluff.shellfstudy.shared.data.model.LessonItem
import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.network.SubjectType

/** Orders the available-lesson queue so the default batch (and any "select first N") favors
 *  leveling up quickly without dumping too much new material on the user at once.
 *
 *  Radicals always come first — they're few, and every kanji depends on its radicals passing, so
 *  clearing them is the single highest-leverage thing to do. Kanji are interleaved with everything
 *  else (vocabulary, plus kanji from other levels) at a ratio that keeps kanji progressing without
 *  letting the review queue spike the way a kanji-only cram would a few days later — mirrors the
 *  ~1:2 kanji:vocab cadence WaniKani community strategy guides converge on. Once this level's 90%
 *  kanji-Guru threshold is already met, remaining kanji no longer block leveling up, so they're
 *  folded into the same low-urgency bucket as vocabulary. */
object LessonPrioritizer {
    private const val KANJI_INTERLEAVE_STRIDE = 3          // 1 kanji per 3 items (1 : 2 kanji:filler)
    private const val STRAINED_KANJI_INTERLEAVE_STRIDE = 5 // 1 kanji per 5 items once today's goal is hit

    fun prioritize(
        items: List<LessonItem>,
        levelUpProgress: LevelUpProgress,
        isStrained: Boolean
    ): List<LessonItem> {
        val sorted = items.sortedWith(compareBy({ it.level }, { it.lessonPosition }, { it.assignmentId }))
        val radicals = sorted.filter { it.subjectType == SubjectType.RADICAL }
        // Once level-up is already secured, further kanji no longer gate anything — treat them as
        // ordinary backlog instead of racing them ahead of vocabulary.
        val kanjiPool = sorted.filter { it.subjectType == SubjectType.KANJI && !levelUpProgress.isLevelUpReady }
        val filler = sorted - radicals.toSet() - kanjiPool.toSet()

        val stride = if (isStrained) STRAINED_KANJI_INTERLEAVE_STRIDE else KANJI_INTERLEAVE_STRIDE
        return radicals + interleave(kanjiPool, filler, stride)
    }

    /** One item from [primary] every [stride] slots, [secondary] filling the rest; once either list
     *  runs out, the remainder of the other is appended as-is. */
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
