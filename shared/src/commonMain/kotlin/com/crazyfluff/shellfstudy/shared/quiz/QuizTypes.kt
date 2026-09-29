package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.PersistedQuestion

enum class QuestionType {
    MEANING, READING;

    companion object {
        /**
         * The [QuestionType] a persisted session snapshot recorded, or null if this build has no such
         * name.
         *
         * `valueOf` is the obvious call and the wrong one here: the string comes from storage written
         * by an older build (or edited by hand), and an unrecognised name would throw out of the
         * resume path — turning a renamed constant into a crash on launch instead of a session that
         * rebuilds itself. Both quiz ViewModels already handle a corrupt snapshot by falling back to a
         * fresh fetch; callers treat null from here as that same case.
         */
        fun fromPersisted(name: String): QuestionType? = entries.firstOrNull { it.name == name }
    }
}

val QuestionType.label: String get() = when (this) {
    QuestionType.MEANING -> "meaning"
    QuestionType.READING -> "reading"
}

/**
 * Rebuilds a persisted queue entry into a live question, or null when it cannot be recovered — either
 * because [itemsById] no longer holds the assignment, or because [PersistedQuestion.questionType]
 * names a question type this build doesn't have.
 *
 * Shared by the lesson and review resume paths so the two cannot disagree about what counts as a
 * recoverable snapshot; both fall back to a fresh fetch when an entry comes back null.
 */
fun <T> PersistedQuestion.toPendingQuestionOrNull(itemsById: Map<Long, T>): PendingQuestion<T>? {
    val item = itemsById[assignmentId] ?: return null
    val type = QuestionType.fromPersisted(questionType) ?: return null
    return PendingQuestion(item, type)
}

data class AnswerFeedback(
    val isCorrect: Boolean,
    val correctAnswer: String,
    val wasCloseMatch: Boolean = false,
    val answerCount: Int = 1
)
