package com.crazyfluff.shellfstudy.shared.feature.review

import com.crazyfluff.shellfstudy.shared.data.model.LevelUpProgress
import com.crazyfluff.shellfstudy.shared.data.model.ReviewItem
import com.crazyfluff.shellfstudy.shared.data.model.ReviewPriority
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.network.SubjectType

/** Reorders a due-review queue so the in-flight batch favors the items whose SRS rank change is
 *  worth the most, mirroring [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonPrioritizer]'s
 *  level-up logic on the review side.
 *
 *  Under [ReviewPriority.RANK_UP] this level's kanji that haven't reached Guru yet come first: they
 *  are the only items that can move the level-up bar (90% of a level's kanji at Guru+, per
 *  [LevelUpProgress]), so clearing them is the single highest-leverage thing a review session can do.
 *  Everything else — vocabulary, radicals, other levels' items, and this level's kanji that are
 *  already past Guru — keeps its relative due order. Already-passed kanji are dropped from the
 *  priority tier rather than merely deprioritized, so a session can't keep racing an item that has
 *  stopped gating anything.
 *
 *  Whether the level-up bar is already met is deliberately *not* a factor here, unlike
 *  [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonSort.KANJI_FIRST]'s `isLevelUpReady` check:
 *  once the bar is met WaniKani lets a new level's items unlock, and those arrive as fresh reviews —
 *  so racing them stays the right call either way. */
object ReviewPrioritizer {

    /** Highest tier, admitted first. Not [Int.MIN_VALUE] so it can sit under [DEFAULT_TIER] if a
     *  middle tier is ever needed. */
    private const val LEVEL_UP_TIER = 0

    /** Everything else, in its existing relative order. */
    private const val DEFAULT_TIER = 1

    /** WaniKani's own Guru threshold — matching [com.crazyfluff.shellfstudy.shared.data.AssignmentRepository]'s
     *  private `GURU_SRS_STAGE`, and shared with [LevelUpProgress]'s counting rule. */
    private val GURU_SRS_STAGE = SrsStage.GURU_1.raw

    /**
     * The tier key [com.crazyfluff.shellfstudy.shared.quiz.QuizQueue.build] sorts the queue by —
     * lower is admitted first. Handed over as a function rather than a pre-sorted list so the queue
     * can order the *whole* queue, keeping [com.crazyfluff.shellfstudy.shared.quiz.QuizQueue]'s
     * reserve in priority order too, not just the ten items admitted up front. Callers pass it only
     * for [ReviewPriority.RANK_UP]; omitting it entirely is what keeps DEFAULT on the queue's
     * original path.
     */
    fun tierSelector(currentLevel: Int?): (ReviewItem) -> Int =
        { item -> priorityOf(item, currentLevel) }

    /** A null [currentLevel] means "level unknown" (no level progression cached yet) — it degrades to
     *  no tier being special rather than guessing level 0, which would match nothing and silently make
     *  the setting inert. */
    private fun priorityOf(item: ReviewItem, currentLevel: Int?): Int {
        if (currentLevel == null) return DEFAULT_TIER
        val gating = item.subjectType == SubjectType.KANJI &&
            item.level == currentLevel &&
            item.srsStage < GURU_SRS_STAGE
        return if (gating) LEVEL_UP_TIER else DEFAULT_TIER
    }
}
