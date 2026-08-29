package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.model.QuizDisplayItem

/**
 * Reverts the most recent incorrect answer — for a typo, not a genuine miss. Shared mutation core
 * for LessonViewModel.undoLastAnswer()/ReviewViewModel.undoLastAnswer(), which previously each
 * carried an identical ~25-line copy of this. Each ViewModel still owns its own guard clauses
 * (current item/question type/feedback) and its own `_uiState.update` (field names differ), but
 * delegates the queue/progress mutation and persistence here.
 *
 * Returns true if there was a progress entry to revert, false if there was nothing to do — the
 * caller's `viewModelScope.launch` should just return in that case. The caller is responsible for
 * restarting its own per-question timer (e.g. via `QuizSessionTiming.restart()`) on a true result;
 * that's presentation-layer state this shared core doesn't own.
 */
suspend fun <T : QuizDisplayItem> undoLastIncorrectAnswer(
    queue: QuizQueue<T>,
    progressByAssignmentId: Map<Long, QuizItemProgress<T>>,
    answeredQuestions: MutableList<AnsweredQuestionRecord<T>>,
    item: T,
    questionType: QuestionType,
    persist: suspend () -> Unit
): Boolean {
    val itemProgress = progressByAssignmentId[item.assignmentId] ?: return false
    when (questionType) {
        QuestionType.MEANING -> itemProgress.hadIncorrectMeaning = false
        QuestionType.READING -> itemProgress.hadIncorrectReading = false
    }
    // The wrong submission moved this question to the back of the queue via requeue(); move it
    // back to the front so it stays "current" (queue.current == the item passed in is the
    // invariant the calling ViewModel's advance function relies on), rather than dropping it
    // entirely.
    queue.moveMatchingToFront { it.item.assignmentId == item.assignmentId && it.type == questionType }

    // Undo removes the incorrect attempt just recorded by gradeAnswer.
    answeredQuestions.removeLastOrNull()

    persist()
    return true
}

/**
 * Reverts the most recent CORRECT answer — the counterpart to [undoLastIncorrectAnswer]. Used by
 * ReviewViewModel.undoLastAnswer() while a correct answer's grade is still only pending (submission
 * to WaniKani is deferred until the user presses Continue — see ReviewViewModel.commitPendingSubmission),
 * so retracting it here is just an in-memory revert with nothing to unwind server-side. The caller
 * is responsible for clearing its own pending-submission bookkeeping before calling [persist].
 *
 * Returns true if there was a progress entry to revert, false if there was nothing to do.
 */
suspend fun <T : QuizDisplayItem> undoLastCorrectAnswer(
    queue: QuizQueue<T>,
    progressByAssignmentId: Map<Long, QuizItemProgress<T>>,
    answeredQuestions: MutableList<AnsweredQuestionRecord<T>>,
    item: T,
    questionType: QuestionType,
    persist: suspend () -> Unit
): Boolean {
    val itemProgress = progressByAssignmentId[item.assignmentId] ?: return false
    when (questionType) {
        QuestionType.MEANING -> itemProgress.meaningDone = false
        QuestionType.READING -> itemProgress.readingDone = false
    }
    // A correct answer removes the question from the queue outright (no requeue) — push it back to
    // the front so it stays "current", the same invariant moveMatchingToFront restores above.
    queue.pushFront(PendingQuestion(item, questionType))

    // Undo removes the correct attempt just recorded by gradeAnswer.
    answeredQuestions.removeLastOrNull()

    persist()
    return true
}
