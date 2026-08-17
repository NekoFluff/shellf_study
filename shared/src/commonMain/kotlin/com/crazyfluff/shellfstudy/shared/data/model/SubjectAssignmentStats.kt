package com.crazyfluff.shellfstudy.shared.data.model

import kotlin.time.Instant

/** Current SRS stage plus the assignment's lifecycle dates — the subject detail view's
 *  stat-chip and milestone-list source. Null if the subject hasn't been lessoned yet (no
 *  assignment exists for it). */
data class SubjectAssignmentStats(
    val srsStage: SrsStage,
    val nextReviewAt: Instant?,
    val unlockedAt: Instant?,
    val startedAt: Instant?,
    val passedAt: Instant?,
    val burnedAt: Instant?
)
