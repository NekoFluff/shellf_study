package com.crazyfluff.shellfstudy.core.data.model

import java.time.LocalDate

data class StudyStreak(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val isActiveToday: Boolean,
    /** Exactly 7 entries, oldest (6 days ago) first, today last. */
    val activeDaysLast7: List<Boolean>
)

data class ReviewsCompletedStats(
    val today: Int,
    val last7Days: Int,
    val allTime: Int
)

/** "How much longer until I've seen the whole library" — a DashboardViewModel-level derivation. */
data class CompletionProjection(
    val totalItems: Int,
    val itemsSeen: Int,
    val dailyPace: Int,
    val daysRemaining: Int,
    val projectedCompletionDate: LocalDate
) {
    val itemsRemaining: Int get() = (totalItems - itemsSeen).coerceAtLeast(0)
}
