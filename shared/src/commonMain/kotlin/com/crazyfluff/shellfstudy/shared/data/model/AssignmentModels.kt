package com.crazyfluff.shellfstudy.shared.data.model

import com.crazyfluff.shellfstudy.shared.network.SubjectType
import kotlin.time.Instant

data class ReviewForecastBucket(
    val hoursFromNow: Int,
    val availableAt: Instant,
    val newlyAvailableCount: Int,
    val countsByType: Map<SubjectType, Int> = emptyMap(),
    /** Grouped by the SRS stage each assignment advances TO if this review is passed — not its
     *  current stage. See [com.crazyfluff.shellfstudy.shared.data.AssignmentRepository]'s
     *  `nextStageBucketFor` for how this is approximated. */
    val countsByNextStage: Map<ItemSpreadBucket, Int> = emptyMap()
)

data class ReviewForecast(
    val reviewsAvailableNow: Int,
    val buckets: List<ReviewForecastBucket>,
    val availableNowCountsByType: Map<SubjectType, Int> = emptyMap(),
    val availableNowCountsByNextStage: Map<ItemSpreadBucket, Int> = emptyMap()
)

/** Which dimension [ReviewForecastCard][com.crazyfluff.shellfstudy.shared.feature.dashboard.ReviewForecastCard]'s
 *  bar segments break down by — user-selectable via its color-mode toggle. Pure display state (both
 *  breakdowns are always present on [ReviewForecast]), unlike [ReviewForecastWindow] which changes
 *  what's actually fetched. */
enum class ReviewForecastColorMode(val label: String) {
    SUBJECT_TYPE("Type"),
    SRS_STAGE("Stage")
}

/** How far ahead [ReviewForecastCard][com.crazyfluff.shellfstudy.shared.feature.dashboard.ReviewForecastCard]
 *  projects upcoming reviews, and how finely — user-selectable via its window dropdown, mirroring
 *  [LeaderboardWindow]. A short window buckets hourly; a longer one buckets by day (or wider) so
 *  [AssignmentRepository][com.crazyfluff.shellfstudy.shared.data.AssignmentRepository] groups
 *  straight into [bucketCount] buckets instead of computing e.g. 2880 near-empty hourly ones only
 *  to discard almost all of them. [bucketHours] must divide [totalHours] evenly.
 *
 *  4 months (not a full year) caps [FOUR_MONTHS]: WaniKani's SRS never schedules a review further
 *  out than the Enlightened→Burned interval, so a year-long forecast would spend its back three
 *  quarters showing nothing. */
enum class ReviewForecastWindow(val totalHours: Int, val bucketHours: Int, val label: String) {
    DAY(totalHours = 24, bucketHours = 1, label = "24h"),
    THREE_DAYS(totalHours = 24 * 3, bucketHours = 3, label = "3d"),
    WEEK(totalHours = 24 * 7, bucketHours = 24, label = "7d"),
    MONTH(totalHours = 24 * 30, bucketHours = 24, label = "30d"),
    FOUR_MONTHS(totalHours = 24 * 120, bucketHours = 240, label = "4mo");

    val bucketCount: Int get() = totalHours / bucketHours
}

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
    /** The subject's raw unicode glyph, if it has one. WaniKani can supply [characterImageUrl]
     *  *alongside* a real glyph (not only for glyph-less radicals), so this must be checked before
     *  [characterImageUrl] wherever both are rendered — matching [display]'s own priority. */
    val characters: String?,
    /** Characters when the subject has a glyph, otherwise its slug — the ultimate text fallback
     *  when there's also no [characterImageUrl] to show. */
    val display: String,
    val passed: Boolean,
    val srsStage: SrsStage,
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
