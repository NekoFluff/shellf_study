package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.model.QuizDisplayItem
import kotlin.time.Clock

/**
 * Reverts the most recent incorrect answer — for a typo, not a genuine miss. Shared mutation core
 * for LessonViewModel.undoLastAnswer()/ReviewViewModel.undoLastAnswer(), which previously each
 * carried an identical ~25-line copy of this. Each ViewModel still owns its own guard clauses
 * (current item/question type/feedback) and its own `_uiState.update` (field names differ), but
 * delegates the queue/progress mutation and persistence here.
 *
 * Returns the new "question shown at" timestamp to store, or null if there was no progress entry
 * to revert (nothing to do — the caller's `viewModelScope.launch` should just return).
 */
suspend fun <T : QuizDisplayItem> undoLastIncorrectAnswer(
    queue: QuizQueue<T>,
    progressByAssignmentId: Map<Long, QuizItemProgress<T>>,
    answeredQuestions: MutableList<AnsweredQuestionRecord<T>>,
    item: T,
    questionType: QuestionType,
    persist: suspend () -> Unit
): Long? {
    val itemProgress = progressByAssignmentId[item.assignmentId] ?: return null
    when (questionType) {
        QuestionType.MEANING -> itemProgress.hadIncorrectMeaning = false
        QuestionType.READING -> itemProgress.hadIncorrectReading = false
    }
    // The wrong submission moved this question to the back of the queue via requeue(); move it
    // back to the front so it stays "current" (queue.current == the item passed in is the
    // invariant the calling ViewModel's advance function relies on), rather than dropping it
    // entirely.
    queue.moveMatchingToFront { it.item.assignmentId == item.assignmentId && it.type == questionType }

    // Undo removes the incorrect attempt just recorded by gradeAnswer, and restarts this
    // question's clock so the retry's timing doesn't inherit time spent before the undo.
    answeredQuestions.removeLastOrNull()
    val newQuestionShownAtMs = Clock.System.now().toEpochMilliseconds()

    persist()
    return newQuestionShownAtMs
}
