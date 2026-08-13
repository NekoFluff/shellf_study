package com.crazyfluff.shellfstudy.core.data.model

import com.crazyfluff.shellfstudy.core.network.SubjectType
import java.time.Instant

data class ReviewForecastBucket(
    val hoursFromNow: Int,
    val availableAt: Instant,
    val newlyAvailableCount: Int,
    val countsByType: Map<SubjectType, Int> = emptyMap()
)

data class ReviewForecast(
    val reviewsAvailableNow: Int,
    val buckets: List<ReviewForecastBucket>,
    val availableNowCountsByType: Map<SubjectType, Int> = emptyMap()
)

enum class ItemSpreadBucket { LOCKED, APPRENTICE, GURU, MASTER, ENLIGHTENED, BURNED }

/** [SubjectType.KANA_VOCABULARY] shares [SubjectType.VOCABULARY]'s color/segment wherever subject
 *  types are broken out in a chart, so its count folds into vocabulary's at those call sites. */
fun SubjectType.foldKana(): SubjectType = if (this == SubjectType.KANA_VOCABULARY) SubjectType.VOCABULARY else this

data class ItemSpread(
    val lockedCount: Int,
    val apprenticeCount: Int,
    val guruCount: Int,
    val masterCount: Int,
    val enlightenedCount: Int,
    val burnedCount: Int,
    val countsByType: Map<ItemSpreadBucket, Map<SubjectType, Int>> = emptyMap()
) {
    val totalCount: Int get() = lockedCount + apprenticeCount + guruCount + masterCount + enlightenedCount + burnedCount
}

data class LevelItem(
    val subjectId: Long,
    val subjectType: SubjectType,
    /** Characters when the subject has a glyph, otherwise its slug (WaniKani's own fallback for image-only radicals). */
    val display: String,
    val passed: Boolean,
    /** PNG image URL for radicals that have no unicode glyph — preferred over [display]'s slug fallback when present. */
    val characterImageUrl: String? = null
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
