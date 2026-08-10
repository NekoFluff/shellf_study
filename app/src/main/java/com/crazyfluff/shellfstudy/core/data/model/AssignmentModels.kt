package com.crazyfluff.shellfstudy.core.data.model

import com.crazyfluff.shellfstudy.core.network.SubjectType
import java.time.Instant

data class ReviewForecastBucket(
    val hoursFromNow: Int,
    val availableAt: Instant,
    val newlyAvailableCount: Int
)

data class ReviewForecast(
    val reviewsAvailableNow: Int,
    val buckets: List<ReviewForecastBucket>
)

data class ItemSpread(
    val lockedCount: Int,
    val apprenticeCount: Int,
    val guruCount: Int,
    val masterCount: Int,
    val enlightenedCount: Int,
    val burnedCount: Int
) {
    val totalCount: Int get() = lockedCount + apprenticeCount + guruCount + masterCount + enlightenedCount + burnedCount
}

data class LevelItem(
    val subjectId: Long,
    val subjectType: SubjectType,
    /** Characters when the subject has a glyph, otherwise its slug (WaniKani's own fallback for image-only radicals). */
    val display: String,
    val passed: Boolean
)

data class SubjectTypeProgress(
    val subjectType: SubjectType,
    val items: List<LevelItem>
) {
    val passedCount: Int get() = items.count { it.passed }
    val totalCount: Int get() = items.size
}

data class LevelProgress(
    val level: Int,
    val breakdown: List<SubjectTypeProgress>
)
