package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardMetric
import com.crazyfluff.shellfstudy.shared.data.model.LeaderboardWindow
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastColorMode
import com.crazyfluff.shellfstudy.shared.data.model.ReviewForecastWindow
import com.crazyfluff.shellfstudy.shared.feature.dashboard.DashboardCallbacks

/**
 * [DashboardCallbacks] with every affordance stubbed except the ones a test names.
 *
 * `DashboardCallbacks` itself has no defaults on purpose, so that adding an action breaks
 * `DashboardRoute`'s compilation rather than silently shipping a tappable control wired to nothing. A
 * test is in the opposite position — it usually exercises one affordance and wants the rest inert —
 * so the defaults live here, where "does nothing" is the intent rather than an oversight.
 */
fun dashboardCallbacks(
    onRefresh: () -> Unit = {},
    onStartReview: () -> Unit = {},
    onLogOut: () -> Unit = {},
    onStartLesson: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenLeaderboard: () -> Unit = {},
    onOpenLastSessionSummary: () -> Unit = {},
    onAbandonReviewSession: () -> Unit = {},
    onAbandonLessonSession: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onLevelProgressLevelChange: (Int) -> Unit = {},
    onLeaderboardMetricChange: (LeaderboardMetric) -> Unit = {},
    onLeaderboardWindowChange: (LeaderboardWindow) -> Unit = {},
    onReviewForecastWindowChange: (ReviewForecastWindow) -> Unit = {},
    onReviewForecastColorModeChange: (ReviewForecastColorMode) -> Unit = {}
): DashboardCallbacks = DashboardCallbacks(
    onRefresh = onRefresh,
    onStartReview = onStartReview,
    onLogOut = onLogOut,
    onStartLesson = onStartLesson,
    onOpenSettings = onOpenSettings,
    onOpenLeaderboard = onOpenLeaderboard,
    onOpenLastSessionSummary = onOpenLastSessionSummary,
    onAbandonReviewSession = onAbandonReviewSession,
    onAbandonLessonSession = onAbandonLessonSession,
    onSearchQueryChange = onSearchQueryChange,
    onLevelProgressLevelChange = onLevelProgressLevelChange,
    onLeaderboardMetricChange = onLeaderboardMetricChange,
    onLeaderboardWindowChange = onLeaderboardWindowChange,
    onReviewForecastWindowChange = onReviewForecastWindowChange,
    onReviewForecastColorModeChange = onReviewForecastColorModeChange
)
