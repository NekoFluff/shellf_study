package com.crazyfluff.shellfstudy.shared.data

import com.crazyfluff.shellfstudy.shared.database.outbox.OutboxDao
import com.crazyfluff.shellfstudy.shared.data.model.ReviewGrade
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class DrainOutcome { SUCCESS, RETRY, AUTH_FAILURE }

/**
 * Drains the outbox queues in-process: lesson starts first (reviews reference started assignments),
 * then review submissions. Returns [DrainOutcome] so the caller can distinguish a transient failure
 * (worth retrying) from an auth failure (stop until re-auth) from a clean pass.
 *
 * Passes are serialised by [drainLock], and each row's submit-then-record runs non-cancellably.
 * `POST /reviews` is not idempotent, so both matter: two overlapping passes would read the same
 * PENDING row and submit it twice, and a pass cancelled between the server accepting a row and the
 * row being deleted would leave it PENDING for the next pass to submit again. Both happen in
 * practice — iOS requests a drain on every grade, and WorkManager's REPLACE cancels a running
 * worker when a new request comes in.
 */
class OutboxDrainer(
    private val outboxDao: OutboxDao,
    private val waniKaniRepository: WaniKaniRepository,
    private val assignmentRepository: AssignmentRepository,
    private val outboxRepository: OutboxRepository
) {
    private val drainLock = Mutex()

    suspend fun drain(): DrainOutcome = drainLock.withLock {
        val lessonResult = drainLessonStarts()
        if (lessonResult != DrainOutcome.SUCCESS) return lessonResult
        val reviewResult = drainReviewSubmissions()
        if (reviewResult != DrainOutcome.SUCCESS) return reviewResult
        outboxRepository.setBlockedOnAuth(false)
        DrainOutcome.SUCCESS
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
                // Counts preserved end-to-end, not collapsed back to booleans: the row is the durable
                // record of how many wrong answers WaniKani must be told about.
                ReviewGrade(
                    meaningCorrect = row.incorrectMeaningAnswers == 0,
                    readingCorrect = row.incorrectReadingAnswers == 0,
                    incorrectMeaning = row.incorrectMeaningAnswers,
                    incorrectReading = row.incorrectReadingAnswers
                )
            )
        },
        // Deleted before reconciling, deliberately, and the opposite of what this used to do. The
        // two writes cannot be one transaction: the outbox is its own Room database, separate from
        // the assignments table it reconciles into. So one of the two orders has to be chosen, and
        // the choice decides what a crash between them costs:
        //
        //   reconcile -> delete: the row is still PENDING, so the next drain submits the review
        //     again. WaniKani's POST /reviews is not idempotent — the assignment has already moved
        //     on from the first submission, so the second one advances it again from the new stage,
        //     which is silent SRS corruption and a duplicate review in the user's history.
        //   delete -> reconcile: the submission is recorded once and the local assignment keeps the
        //     optimistic stage the grading path already gave it until the next assignments sync
        //     (which the dashboard's resume path and the periodic sync both perform) fetches the
        //     authoritative row.
        //
        // A stale local row that heals on the next sync is the cheaper failure. The fuller fix is a
        // two-phase row — mark it submitted, reconcile, then delete — which would need the server's
        // answer persisted on the row or a refetch to recover; not worth the extra state for a
        // window this narrow.
        onSuccess = { row, data ->
            outboxDao.deleteReviewSubmission(row)
            assignmentRepository.reconcileAfterReviewResult(data)
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
        var retryNeeded = false
        for (row in rows) {
            val result = withContext(NonCancellable) {
                submit(row).also { if (it is ApiResult.Success) onSuccess(row, it.data) }
            }
            when (result) {
                is ApiResult.Success -> Unit
                is ApiResult.Error -> {
                    if (result.isAuthError) {
                        outboxRepository.setBlockedOnAuth(true)
                        return DrainOutcome.AUTH_FAILURE
                    }
                    if (result.isTerminalRejection) {
                        markTerminal(row, result.message)
                        // Best-effort reconciliation, result deliberately ignored: if this refetch
                        // itself fails (e.g. offline at this exact moment), the assignment's local
                        // cache is left stale until the next periodic sync re-fetches it wholesale
                        // — there's no separate "needs reconciliation" bookkeeping to retry just
                        // this one row sooner, and the terminal rejection above is recorded either
                        // way, so it's not worth failing (or retrying) the whole drain over.
                        assignmentRepository.refetchAssignment(assignmentId(row))
                        continue
                    }
                    if (result.status == null) {
                        // The request never got an answer — offline, DNS, a timeout. Every remaining
                        // row would fail the same way, so stop the pass instead of retrying the whole
                        // queue against a network that is down.
                        return DrainOutcome.RETRY
                    }
                    // The server answered and refused *this* row: a 5xx on one payload, or a 429.
                    // Rows are independent submissions, so the rest of the queue drains rather than
                    // waiting behind it — the failing row stays PENDING and is retried next pass.
                    retryNeeded = true
                }
            }
        }
        return if (retryNeeded) DrainOutcome.RETRY else DrainOutcome.SUCCESS
    }
}
