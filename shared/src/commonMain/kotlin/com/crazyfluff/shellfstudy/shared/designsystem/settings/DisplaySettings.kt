package com.crazyfluff.shellfstudy.shared.designsystem.settings

import androidx.compose.runtime.staticCompositionLocalOf
import com.crazyfluff.shellfstudy.shared.data.AppSettings

/**
 * The user's preferences as the *UI* needs them: every field changes how something is drawn, never
 * what a feature does. Nothing here decides a queue's contents, a batch size, or whether an answer is
 * accepted.
 *
 * These nine flags used to be re-declared in five feature UI states — `SubjectDetailUiState`'s four
 * flat fields, `LessonUiState.DisplaySettings`, `ReviewUiState.DisplaySettings`,
 * `QuizQuestionUiState`'s five, and `SubjectDetailDisplaySettings` — with each ViewModel copying them
 * field-by-field out of [AppSettings] and each screen threading them into whichever composable needed
 * them. A tenth display setting meant editing all of that. They are cross-cutting the way the theme
 * is: no single feature owns them, and each is read by a leaf several layers below whoever knows about
 * it, so they travel by [LocalDisplaySettings] instead.
 *
 * Two of these — [restrictAudioToMp3] and [showAnswerReadingPitchAccent] — are *also* read by the
 * lesson and review ViewModels, which need them while assembling a question (choosing which clip
 * autoplays, and whether to build a reading hint at all). Those ViewModels keep reading them from
 * [AppSettings]: this type is the rendering view of the same stored values, not a second source, so
 * the two can never disagree. The genuinely behaviour-only settings stay off this type entirely —
 * `dailyLessonGoal`, `lessonBatchSize`, `closeEnoughAnswersEnabled`, `autoplayPronunciationAudio`.
 */
data class DisplaySettings(
    val showPitchAccent: Boolean = true,
    val restrictAudioToMp3: Boolean = false,
    val showSubjectTypeLabel: Boolean = false,
    val showTotalTimer: Boolean = false,
    val showQuestionTimer: Boolean = false,
    val showStrokeOrder: Boolean = true,
    val useJapaneseKeyboard: Boolean = false,
    val showAnswerReadingPitchAccent: Boolean = false,
    val hideContextSentenceTranslations: Boolean = true
)

/** The rendering view of the stored preferences — see [DisplaySettings]. */
fun AppSettings.toDisplaySettings(): DisplaySettings = DisplaySettings(
    showPitchAccent = showPitchAccent,
    restrictAudioToMp3 = restrictAudioToMp3,
    showSubjectTypeLabel = showSubjectTypeLabel,
    showTotalTimer = showTotalTimer,
    showQuestionTimer = showQuestionTimer,
    showStrokeOrder = showStrokeOrder,
    useJapaneseKeyboard = useJapaneseKeyboard,
    showAnswerReadingPitchAccent = showAnswerReadingPitchAccent,
    hideContextSentenceTranslations = hideContextSentenceTranslations
)

/**
 * The [DisplaySettings] for the whole app, provided once by `ShellfStudyApp` from the settings
 * repository's own flow.
 *
 * Defaults to the same values [DisplaySettings] carries, so a caller that composes one leaf without a
 * provider in scope — a focused test, most often — gets production defaults rather than everything
 * switched off. `staticCompositionLocalOf` rather than `compositionLocalOf` because settings change
 * rarely (a user toggling a switch on the settings screen) and a change may legitimately affect
 * anything below, so tracking reads individually would buy nothing.
 */
val LocalDisplaySettings = staticCompositionLocalOf { DisplaySettings() }
