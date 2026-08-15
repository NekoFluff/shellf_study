package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingLessonStartEntity
import com.crazyfluff.shellfstudy.shared.database.outbox.PendingReviewSubmissionEntity
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade

enum class DrainOutcome { SUCCESS, RETRY, AUTH_FAILURE }

/**
 * Drains the outbox queues in-process: lesson starts first (reviews reference started assignments),
 * then review submissions. Returns [DrainOutcome] so the caller can distinguish a transient failure
 * (worth retrying) from an auth failure (stop until re-auth) from a clean pass.
 */
class OutboxDrainer(
    private val outboxDao: OutboxDao,
    private val waniKaniRepository: WaniKaniRepository,
    private val assignmentRepository: AssignmentRepository,
    private val outboxRepository: OutboxRepository
) {
    suspend fun drain(): DrainOutcome {
        val lessonResult = drainLessonStarts()
        if (lessonResult != DrainOutcome.SUCCESS) return lessonResult
        val reviewResult = drainReviewSubmissions()
        if (reviewResult != DrainOutcome.SUCCESS) return reviewResult
        outboxRepository.setBlockedOnAuth(false)
        return DrainOutcome.SUCCESS
    }

    private suspend fun drainLessonStarts(): DrainOutcome = drain(
        rows = outboxDao.getPendingLessonStarts(),
        assignmentId = { it.assignmentId },
        submit = { row -> assignmentRepository.startAssignment(row.assignmentId) },
        onSuccess = { row, _ -> outboxDao.deleteLessonStart(row) },
        markTerminal = { row, message -> outboxDao.markLessonStartTerminal(row.id, message) }
    )

    private suspend fun drainReviewSubmissions(): DrainOutcome = drain(
        rows = outboxDao.getPendingReviewSubmissions(),
        assignmentId = { it.assignmentId },
        submit = { row ->
            waniKaniRepository.submitReview(
                row.assignmentId,
                ReviewGrade(meaningCorrect = row.incorrectMeaningAnswers == 0, readingCorrect = row.incorrectReadingAnswers == 0)
            )
        },
        onSuccess = { row, data ->
            assignmentRepository.reconcileAfterReviewResult(data)
            outboxDao.deleteReviewSubmission(row)
        },
        markTerminal = { row, message -> outboxDao.markReviewSubmissionTerminal(row.id, message) }
    )

    private suspend fun <Row, T> drain(
        rows: List<Row>,
        assignmentId: (Row) -> Long,
        submit: suspend (Row) -> ApiResult<T>,
        onSuccess: suspend (Row, T) -> Unit,
        markTerminal: suspend (Row, String?) -> Unit
    ): DrainOutcome {
        for (row in rows) {
            when (val result = submit(row)) {
                is ApiResult.Success -> onSuccess(row, result.data)
                is ApiResult.Error -> {
                    if (result.isAuthError) {
                        outboxRepository.setBlockedOnAuth(true)
                        return DrainOutcome.AUTH_FAILURE
                    }
                    if (result.isTerminalRejection) {
                        markTerminal(row, result.message)
                        assignmentRepository.refetchAssignment(assignmentId(row))
                        continue
                    }
                    return DrainOutcome.RETRY
                }
            }
        }
        return DrainOutcome.SUCCESS
    }
}
