package com.crazyfluff.shellfstudy.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crazyfluff.shellfstudy.core.data.ApiResult
import com.crazyfluff.shellfstudy.core.data.AssignmentRepository
import com.crazyfluff.shellfstudy.core.data.OutboxRepository
import com.crazyfluff.shellfstudy.core.data.WaniKaniRepository
import com.crazyfluff.shellfstudy.core.data.isAuthError
import com.crazyfluff.shellfstudy.core.data.isTerminalRejection
import com.crazyfluff.shellfstudy.core.data.model.ReviewGrade
import com.crazyfluff.shellfstudy.core.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.core.database.outbox.PendingReviewSubmissionEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Drains the durable outbox (queued review submissions / lesson starts) once connectivity allows
 * — constrained on `NetworkType.CONNECTED` by [OutboxSyncScheduler], so this only ever runs when
 * actually online. Lesson starts are drained before review submissions (a review's assignment
 * must already be started, so this is the natural precedence), each in creation order.
 */
@HiltWorker
class OutboxSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val outboxDao: OutboxDao,
    private val waniKaniRepository: WaniKaniRepository,
    private val assignmentRepository: AssignmentRepository,
    private val outboxRepository: OutboxRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        drainLessonStarts()?.let { return it }
        drainReviewSubmissions()?.let { return it }
        // A clean full pass proves the token is fine again, in case a prior pass had set this.
        outboxRepository.setBlockedOnAuth(false)
        return Result.success()
    }

    private suspend fun drainLessonStarts(): Result? = drain(
        rows = outboxDao.getPendingLessonStarts(),
        assignmentId = { it.assignmentId },
        submit = { row -> assignmentRepository.startAssignment(row.assignmentId) },
        onSuccess = { row, _ -> outboxDao.deleteLessonStart(row) },
        markTerminal = { row, message -> outboxDao.markLessonStartTerminal(row.id, message) }
    )

    private suspend fun drainReviewSubmissions(): Result? = drain(
        rows = outboxDao.getPendingReviewSubmissions(),
        assignmentId = { it.assignmentId },
        submit = { row -> waniKaniRepository.submitReview(row.assignmentId, row.toGrade()) },
        onSuccess = { row, data ->
            assignmentRepository.reconcileAfterReviewResult(data)
            outboxDao.deleteReviewSubmission(row)
        },
        markTerminal = { row, message -> outboxDao.markReviewSubmissionTerminal(row.id, message) }
    )

    /**
     * Drains one outbox queue in creation order, applying the shared auth/terminal/retry branching
     * — [onSuccess] and [markTerminal] carry the one thing that's genuinely queue-specific: what to
     * do with the row (and, for a success, the response payload) once its fate is known. Returns a
     * terminal [Result] to stop the whole pass (auth failure / transient error), or null to keep
     * going onto the next queue.
     */
    private suspend fun <Row, T> drain(
        rows: List<Row>,
        assignmentId: (Row) -> Long,
        submit: suspend (Row) -> ApiResult<T>,
        onSuccess: suspend (Row, T) -> Unit,
        markTerminal: suspend (Row, String) -> Unit
    ): Result? {
        for (row in rows) {
            when (val result = submit(row)) {
                is ApiResult.Success -> onSuccess(row, result.data)
                is ApiResult.Error -> {
                    if (result.isAuthError) {
                        outboxRepository.setBlockedOnAuth(true)
                        return Result.failure()
                    }
                    if (result.isTerminalRejection) {
                        markTerminal(row, result.message)
                        assignmentRepository.refetchAssignment(assignmentId(row))
                        continue
                    }
                    return Result.retry()
                }
            }
        }
        return null
    }
}

private fun PendingReviewSubmissionEntity.toGrade(): ReviewGrade =
    ReviewGrade(meaningCorrect = incorrectMeaningAnswers == 0, readingCorrect = incorrectReadingAnswers == 0)
