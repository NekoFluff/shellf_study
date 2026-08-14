package com.crazyfluff.shellfstudy.shared.data.model

import kotlinx.datetime.LocalDate

data class StudyStreak(
    val currentStreakDays: Int,
    val isActiveToday: Boolean
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
