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

    /** Returns a terminal [Result] to stop the whole pass (auth failure / transient error), or null
     *  to keep going — mirrors [drainReviewSubmissions]'s branching exactly. */
    private suspend fun drainLessonStarts(): Result? {
        for (row in outboxDao.getPendingLessonStarts()) {
            when (val result = assignmentRepository.startAssignment(row.assignmentId)) {
                is ApiResult.Success -> outboxDao.deleteLessonStart(row)
                is ApiResult.Error -> {
                    if (result.isAuthError) {
                        outboxRepository.setBlockedOnAuth(true)
                        return Result.failure()
                    }
                    if (result.isTerminalRejection) {
                        outboxDao.markLessonStartTerminal(row.id, result.message)
                        assignmentRepository.refetchAssignment(row.assignmentId)
                        continue
                    }
                    return Result.retry()
                }
            }
        }
        return null
    }

    private suspend fun drainReviewSubmissions(): Result? {
        for (row in outboxDao.getPendingReviewSubmissions()) {
            when (val result = waniKaniRepository.submitReview(row.assignmentId, row.toGrade())) {
                is ApiResult.Success -> {
                    assignmentRepository.reconcileAfterReviewResult(result.data)
                    outboxDao.deleteReviewSubmission(row)
                }
                is ApiResult.Error -> {
                    if (result.isAuthError) {
                        outboxRepository.setBlockedOnAuth(true)
                        return Result.failure()
                    }
                    if (result.isTerminalRejection) {
                        outboxDao.markReviewSubmissionTerminal(row.id, result.message)
                        assignmentRepository.refetchAssignment(row.assignmentId)
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
