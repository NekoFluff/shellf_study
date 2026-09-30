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
 *  Under [ReviewPriority.RANK_UP] this level's radicals and kanji that haven't reached Guru yet come
 *  first. The kanji are what move the level-up bar (90% of a level's kanji at Guru+, per
 *  [LevelUpProgress]). The radicals gate those kanji: a kanji only unlocks once its radicals reach
 *  Guru. Together they're everything standing between the learner and the next level. Both are
 *  admitted as one group, keeping their relative due order, and ordinary items only backfill a
 *  small working set until they're all finished (see QuizQueue.admitNext).
 *
 *  Everything else — vocabulary, other levels' items, and this level's radicals and kanji already
 *  past Guru — keeps its relative due order behind them. Already-passed items are dropped from the
 *  priority group rather than merely deprioritized, so a session can't keep racing an item that has
 *  stopped gating anything.
 *
 *  Whether the level-up bar is already met is deliberately *not* a factor here, unlike
 *  [com.crazyfluff.shellfstudy.shared.feature.lesson.LessonSort.KANJI_FIRST]'s `isLevelUpReady` check:
 *  once the bar is met WaniKani lets a new level's items unlock, and those arrive as fresh reviews —
 *  so racing them stays the right call either way. */
object ReviewPrioritizer {

    /** WaniKani's own Guru threshold — matching [com.crazyfluff.shellfstudy.shared.data.AssignmentRepository]'s
     *  private `GURU_SRS_STAGE`, and shared with [LevelUpProgress]'s counting rule. */
    private val GURU_SRS_STAGE = SrsStage.GURU_1.raw

    /** Kanji count toward the level-up bar, and radicals unlock the kanji. Vocabulary does neither. */
    private val LEVEL_GATING_TYPES = setOf(SubjectType.RADICAL, SubjectType.KANJI)

    /**
     * The assignment ids of [items] that go ahead of the rest — what
     * [com.crazyfluff.shellfstudy.shared.quiz.QuizSession.withQuestionsFor] takes as `priorityIds`.
     * Computed once when a session is built and persisted with it, so an item that reaches Guru (or a
     * level-up) mid-session doesn't reshuffle the split the reserve was sorted by. Callers ask only for
     * [ReviewPriority.RANK_UP]; DEFAULT passes no ids at all, keeping the queue on its original path.
     */
    fun priorityIds(items: List<ReviewItem>, currentLevel: Int?): Set<Long> =
        items.filter { gatesLevelUp(it, currentLevel) }.mapTo(mutableSetOf()) { it.assignmentId }

    /** A null [currentLevel] means "level unknown" (no level progression cached yet) — it degrades to
     *  nothing being special rather than guessing level 0, which would match nothing either but look
     *  like a deliberate choice. */
    fun gatesLevelUp(item: ReviewItem, currentLevel: Int?): Boolean =
        currentLevel != null &&
            item.subjectType in LEVEL_GATING_TYPES &&
            item.level == currentLevel &&
            item.srsStage < GURU_SRS_STAGE
}
