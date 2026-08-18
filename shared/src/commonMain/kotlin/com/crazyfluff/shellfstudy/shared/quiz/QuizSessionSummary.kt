package com.crazyfluff.shellfstudy.shared.quiz

import com.crazyfluff.shellfstudy.shared.data.model.QuizDisplayItem
import com.crazyfluff.shellfstudy.shared.data.model.SessionAnswerRow
import com.crazyfluff.shellfstudy.shared.data.model.SessionMissedItemRow

/** Aggregate stats for a completed lesson or review session — shared shape, computed the same way
 *  for both features via [summarizeQuizSession]. */
data class QuizSessionSummary<T>(
    val itemsCount: Int,
    val correctFirstTry: Int,
    val missedItems: List<T>,
    val totalElapsedMs: Long,
    val averageTimePerItemMs: Long,
    val slowestAnswers: List<SlowAnswer<T>>
)

/** Items completed, how many were correct without ever missing, which were missed at least once,
 *  and timing — total session time, average time per item, and the slowest answers.
 *
 *  [progress] must already be filtered down to the items that actually count as "completed" for
 *  the calling feature (e.g. Review only counts entries with [QuizItemProgress.hasAnyProgress],
 *  since its progress map is pre-seeded for the whole queue up front). */
fun <T> summarizeQuizSession(
    progress: Collection<QuizItemProgress<T>>,
    answeredQuestions: List<AnsweredQuestionRecord<T>>,
    totalElapsedMs: Long
): QuizSessionSummary<T> {
    val itemsCount = progress.size
    val correctFirstTry = progress.count { !it.hadIncorrectMeaning && !it.hadIncorrectReading }
    val missedItems = progress
        .filter { it.hadIncorrectMeaning || it.hadIncorrectReading }
        .map { it.item }
    val averageTimePerItemMs = if (itemsCount == 0) 0L else totalElapsedMs / itemsCount
    val slowestAnswers = answeredQuestions.sortedByDescending { it.elapsedMs }.take(5)
        .map { SlowAnswer(it.item, it.type, it.elapsedMs, it.isCorrect) }
    return QuizSessionSummary(
        itemsCount = itemsCount,
        correctFirstTry = correctFirstTry,
        missedItems = missedItems,
        totalElapsedMs = totalElapsedMs,
        averageTimePerItemMs = averageTimePerItemMs,
        slowestAnswers = slowestAnswers
    )
}

/** Reduces a graded answer down to its session-summary display row — shared by Lesson and Review,
 *  which previously each carried an identical private copy of this mapping. */
fun <T : QuizDisplayItem> SlowAnswer<T>.toSessionAnswerRow(): SessionAnswerRow = SessionAnswerRow(
    label = item.characters ?: item.meanings.firstOrNull() ?: "?",
    typeLabel = type.label,
    elapsedMs = elapsedMs,
    isCorrect = isCorrect,
    subjectId = item.subjectId,
    subjectType = item.subjectType
)

/** Reduces a missed item down to its session-summary display row — shared by Lesson and Review. */
fun <T : QuizDisplayItem> T.toSessionMissedItemRow(): SessionMissedItemRow = SessionMissedItemRow(
    label = characters ?: meanings.firstOrNull() ?: "?",
    subjectId = subjectId,
    subjectType = subjectType
)
