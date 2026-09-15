package com.crazyfluff.shellfstudy.shared.feature.settings

import com.crazyfluff.shellfstudy.shared.data.ThemeMode
import kotlinx.coroutines.Job

/**
 * Everything the settings screen can ask its state holder to do, as one type.
 *
 * This replaces `SettingsCallbacks`, a 26-field data class of lambdas that `SettingsRoute` built by
 * hand from `viewModel::onX` references. That bundle was a second encoding of the ViewModel's own
 * method names — every rename had to be made twice, and `SettingsScreen`'s signature could not say
 * which of the two dozen actions it actually used, because it declared all of them.
 *
 * Passing the state holder instead is the same treatment `LessonActions`, `ReviewActions` and
 * `LeaderboardActions` get; the interface exists rather than the composables naming the concrete
 * ViewModel so a screen test can substitute a recording stand-in.
 *
 * Route-level concerns stay parameters on `SettingsScreen` rather than members here: `onBack` and
 * `onOpenLeaderboard` are navigation, and switching notifications on needs a platform permission
 * prompt, which the ViewModel cannot raise — so `SettingsRoute` keeps intercepting that one.
 */
interface SettingsActions {
    fun onDailyLessonGoalChange(goal: Int)

    fun onLessonBatchSizeChange(size: Int)

    fun onThemeModeChange(mode: ThemeMode)

    fun onShowPitchAccentChange(enabled: Boolean)

    fun onAutoplayPronunciationAudioChange(enabled: Boolean)

    fun onRestrictAudioToMp3Change(enabled: Boolean)

    fun onShowSubjectTypeLabelChange(enabled: Boolean)

    fun onShowTotalTimerChange(enabled: Boolean)

    fun onShowQuestionTimerChange(enabled: Boolean)

    fun onShowStrokeOrderChange(enabled: Boolean)

    fun onUseJapaneseKeyboardChange(enabled: Boolean)

    fun onCloseEnoughAnswersEnabledChange(enabled: Boolean)

    fun onShowAnswerReadingPitchAccentChange(enabled: Boolean)

    fun onHideContextSentenceTranslationsChange(enabled: Boolean)

    fun onRequireTapToRevealMeaningAnswerChange(enabled: Boolean)

    fun onRequireTapToRevealReadingAnswerChange(enabled: Boolean)

    fun onReviewsAvailableEnabledChange(enabled: Boolean)

    fun onReviewsBacklogEnabledChange(enabled: Boolean)

    fun onBacklogThresholdChange(threshold: Int)

    fun onDailyReminderEnabledChange(enabled: Boolean)

    /**
     * The one action here that returns its [Job]. The write it performs is real-IO-backed, so a test
     * that asserts both the persisted hour *and* the follow-up reschedule cannot observe them
     * deterministically without awaiting this coroutine — see the note above those tests in
     * `SettingsViewModelTest`. Production callers ignore the result.
     */
    fun onDailyReminderHourChange(hour: Int): Job

    fun onQuietHoursEnabledChange(enabled: Boolean)

    fun onQuietHoursStartHourChange(hour: Int)

    fun onQuietHoursEndHourChange(hour: Int)

    fun onFullRefreshRequested()
}
