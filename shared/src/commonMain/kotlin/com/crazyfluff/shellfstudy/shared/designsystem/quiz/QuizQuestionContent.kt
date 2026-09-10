package com.crazyfluff.shellfstudy.shared.designsystem.quiz

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.crazyfluff.shellfstudy.shared.data.model.QuizDisplayItem
import com.crazyfluff.shellfstudy.shared.data.PlaybackState
import com.crazyfluff.shellfstudy.shared.data.model.RankChange
import com.crazyfluff.shellfstudy.shared.data.model.SrsStage
import com.crazyfluff.shellfstudy.shared.designsystem.components.ExpandableAnswerListText
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectDetailHandleHeight
import com.crazyfluff.shellfstudy.shared.designsystem.subjectdetail.SubjectGlyph
import com.crazyfluff.shellfstudy.shared.designsystem.theme.CorrectAnswerColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.CorrectAnswerColorDark
import com.crazyfluff.shellfstudy.shared.designsystem.theme.RankChangeChip
import com.crazyfluff.shellfstudy.shared.designsystem.theme.RankChangeChipEnterDurationMs
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectColor
import com.crazyfluff.shellfstudy.shared.designsystem.theme.subjectTypeLabel
import com.crazyfluff.shellfstudy.shared.designsystem.theme.themeAwareColor
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.label

private val RankChangeChipWarmupValue = RankChange(from = SrsStage.APPRENTICE_1, to = SrsStage.APPRENTICE_2)

/** Test tags for [QuizQuestionContent] — one bundle per feature (Lesson/Review). */
data class QuizQuestionTestTags(
    val progressCount: String,
    val questionTimerText: String,
    val totalTimerText: String,
    val characters: String,
    val subjectTypeLabel: String,
    val rankChangeText: String,
    val questionLabel: String,
    val answerField: String,
    val typeMismatchText: String,
    val dontKnowButton: String,
    val submitButton: String,
    val undoButton: String,
    val feedbackText: String,
    val answerDetailText: String,
    val continueButton: String,
    val answerReadingPitchAccentHint: String,
    /** The optional "Batch 2 of 4"-style label in the progress row. */
    val sessionContextLabel: String
)

/** Everything [QuizQuestionContent] needs to render one quiz question — a read-only projection
 *  each feature's own (differently-shaped) UI state builds right before rendering. */
data class QuizQuestionUiState<T : QuizDisplayItem>(
    val item: T,
    val questionType: QuestionType,
    val totalCount: Int,
    val remainingCount: Int,
    val answerInput: String,
    val feedback: AnswerFeedback?,
    val rankChange: RankChange?,
    val undoCounter: Int,
    val answerTypeMismatchCount: Int,
    val showSubjectTypeLabel: Boolean,
    val showQuestionTimer: Boolean,
    val showTotalTimer: Boolean,
    val questionElapsedMs: Long?,
    val questionActiveElapsedMs: Long,
    val questionActiveSegmentStartMs: Long?,
    val sessionActiveElapsedMs: Long,
    val sessionActiveSegmentStartMs: Long?,
    val useJapaneseKeyboard: Boolean,
    // Review defers submitting a correct answer to WaniKani until Continue is pressed, so it can
    // still be undone up to that point — Lesson has no such pending-submission window, so it leaves
    // this at the default and undo stays incorrect-only there.
    val allowUndoAfterCorrect: Boolean = false,
    // Live setting gate, plus the reading/pitch-accent data for the just-graded reading question —
    // null/empty unless that setting is on, the question type is READING, and feedback exists.
    val showAnswerReadingPitchAccent: Boolean = false,
    val answerReading: String? = null,
    val answerPitchAccents: List<PitchAccent> = emptyList(),
    val answerReadingHasAudio: Boolean = false,
    // Which pass of the session this question belongs to ("Batch 2 of 4", "Extra practice") — null
    // when there's nothing worth saying, which is the case for every review question and for a lesson
    // session that fits in a single batch.
    val sessionContextLabel: String? = null
)

/**
 * A single lesson/review quiz question — progress bar, timers, subject glyph, rank-change chip,
 * answer field (with undo), and feedback/continue controls. Shared between Lesson and Review,
 * which previously carried two copies of this composable that had drifted apart (undo, rank
 * change) before both features converged on the same behavior.
 */
@Composable
fun <T : QuizDisplayItem> ColumnScope.QuizQuestionContent(
    uiState: QuizQuestionUiState<T>,
    onAnswerInputChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDontKnow: () -> Unit,
    onContinue: () -> Unit,
    onUndo: () -> Unit,
    onPlayAnswerReading: () -> Unit,
    testTags: QuizQuestionTestTags,
    audioPlaybackState: PlaybackState = PlaybackState.IDLE
) {
    val item = uiState.item
    val questionType = uiState.questionType

    val progress = if (uiState.totalCount == 0) 0f else
        (uiState.totalCount - uiState.remainingCount).toFloat() / uiState.totalCount
    val accentColor = subjectColor(item.subjectType)

    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth(),
        color = accentColor,
        drawStopIndicator = {}
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${uiState.totalCount - uiState.remainingCount} / ${uiState.totalCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(testTags.progressCount)
            )
            // The count above is this pass's, not the session's — say which pass, so a learner midway
            // through a multi-batch session doesn't read "2 / 10" as the whole thing.
            uiState.sessionContextLabel?.let { label ->
                Text(
                    text = " · ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(testTags.sessionContextLabel)
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiState.showQuestionTimer) {
                val questionElapsedMs = uiState.questionElapsedMs
                if (questionElapsedMs != null) {
                    // Frozen at the instant the question was answered, matching the elapsedMs recorded
                    // for the slowest-answers summary, rather than continuing to tick through feedback.
                    Text(
                        text = formatElapsedClock(questionElapsedMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(testTags.questionTimerText)
                    )
                } else {
                    // Pause-aware like the total-session timer below, so backgrounding mid-question
                    // freezes this instead of counting straight through the time spent away.
                    PausableElapsedTimeText(
                        baseElapsedMs = uiState.questionActiveElapsedMs,
                        segmentStartMs = uiState.questionActiveSegmentStartMs,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(testTags.questionTimerText)
                    )
                }
            }
            if (uiState.showQuestionTimer && uiState.showTotalTimer) {
                Text(
                    text = " / ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (uiState.showTotalTimer) {
                PausableElapsedTimeText(
                    baseElapsedMs = uiState.sessionActiveElapsedMs,
                    segmentStartMs = uiState.sessionActiveSegmentStartMs,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(testTags.totalTimerText)
                )
            }
        }
    }

    Column(
        modifier = Modifier.weight(1f, fill = false).fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val answerReading = uiState.answerReading
        val showAnswerHint = uiState.showAnswerReadingPitchAccent &&
            questionType == QuestionType.READING &&
            answerReading != null
        AnimatedVisibility(
            visible = showAnswerHint,
            // Slides down from above (unlike RankChangeChip's slide-up-from-below below the glyph)
            // — a deliberate visual distinction between "new info revealed here" and "your queue
            // status down there".
            enter = fadeIn(animationSpec = tween(durationMillis = RankChangeChipEnterDurationMs)) +
                slideInVertically(
                    initialOffsetY = { -it / 2 },
                    animationSpec = tween(durationMillis = RankChangeChipEnterDurationMs, easing = FastOutSlowInEasing)
                ),
            exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                slideOutVertically(
                    targetOffsetY = { -it / 2 },
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                )
        ) {
            if (answerReading != null) {
                Column {
                    AnswerReadingPitchAccentHint(
                        reading = answerReading,
                        pitchAccents = uiState.answerPitchAccents,
                        modifier = Modifier.testTag(testTags.answerReadingPitchAccentHint),
                        hasAudio = uiState.answerReadingHasAudio,
                        playbackState = audioPlaybackState,
                        onPlayReading = onPlayAnswerReading
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }
        }
        SubjectGlyph(
            characters = item.characters,
            characterImageUrl = item.characterImageUrl,
            subjectType = item.subjectType,
            size = 104.dp,
            modifier = Modifier.testTag(testTags.characters)
        )
        if (uiState.showSubjectTypeLabel) {
            Text(
                text = subjectTypeLabel(item.subjectType),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(testTags.subjectTypeLabel)
            )
        }
        // Unconditionally mounted warm-up for RankChangeChip's icon/text (measured at ~30ms across
        // two frames; see ReviewSubmitJankProfilingTest). Both features reset `rankChange` to null
        // every question, so AnimatedVisibility below tears down and rebuilds its content on every
        // question that has a rank change, not just the session's first — without a warm-up, that
        // cost recurs on every such Submit. Measures its children at a realistic width (unlike a
        // zero-size warm-up, which measures Text/Icon against degenerate 0px constraints and
        // doesn't exercise the same layout path) but reports zero size to this Column and clips its
        // own draw to nothing, so it neither pushes later content down nor is ever actually visible
        // or hit-testable.
        Layout(
            modifier = Modifier.clipToBounds(),
            content = {
                RankChangeChip(RankChangeChipWarmupValue)
                RankChangeChip(RankChangeChipWarmupValue.copy(from = SrsStage.APPRENTICE_2, to = SrsStage.APPRENTICE_1))
            }
        ) { measurables, _ ->
            val childConstraints = Constraints(maxWidth = 200.dp.roundToPx())
            val placeables = measurables.map { it.measure(childConstraints) }
            layout(0, 0) { placeables.forEach { it.place(0, 0) } }
        }
        AnimatedVisibility(
            visible = uiState.rankChange != null,
            // Fade + slide up from below — no scale (a prior scale+overshoot combination visibly
            // clipped the chip's edges against AnimatedVisibility's clip-to-bounds behavior; a
            // pure fade/slide never exceeds its own laid-out bounds, so there's nothing to clip).
            // Duration shared with RankChangeChip's own internal color-morph animation via
            // RankChangeChipEnterDurationMs so the two can't drift out of sync; at a full-height
            // slide distance so the motion actually reads as an animation — a much shorter/smaller
            // version of this previously was imperceptible.
            enter = fadeIn(animationSpec = tween(durationMillis = RankChangeChipEnterDurationMs)) +
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = RankChangeChipEnterDurationMs, easing = FastOutSlowInEasing)
                ),
            // Snappier than the entrance — the chip should feel dismissed, not lingered on.
            exit = fadeOut(animationSpec = tween(durationMillis = 150)) +
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
                )
        ) {
            val rankChange = uiState.rankChange
            if (rankChange != null) {
                Spacer(modifier = Modifier.height(8.dp))
                RankChangeChip(rankChange, modifier = Modifier.testTag(testTags.rankChangeText))
            }
        }
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "What is the ${questionType.label}?",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.testTag(testTags.questionLabel)
        )
        Spacer(modifier = Modifier.height(8.dp))
        val feedbackForField = uiState.feedback
        val canUndo = feedbackForField != null && (!feedbackForField.isCorrect || uiState.allowUndoAfterCorrect)
        QuizAnswerField(
            value = uiState.answerInput,
            onValueChange = onAnswerInputChange,
            questionType = questionType,
            isAnswered = feedbackForField != null,
            answerTypeMismatchCount = uiState.answerTypeMismatchCount,
            onSubmit = onSubmit,
            answerFieldTestTag = testTags.answerField,
            typeMismatchTextTestTag = testTags.typeMismatchText,
            // Also includes undoCounter: undo clears the field and re-enables it without changing
            // item/questionType, so the field's focus-restoring effect wouldn't otherwise refire and
            // the user would be left tapped-out of the field they just asked to retry.
            focusResetKey = Triple(item.assignmentId, questionType, uiState.undoCounter),
            useJapaneseKeyboard = uiState.useJapaneseKeyboard,
            trailingIcon = if (feedbackForField != null) {
                {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier.testTag(testTags.undoButton)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last answer")
                    }
                }
            } else null
        )

        Spacer(modifier = Modifier.height(16.dp))

        val feedback = uiState.feedback
        if (feedback == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDontKnow,
                    modifier = Modifier.weight(1f).testTag(testTags.dontKnowButton)
                ) { Text("I don't know") }
                Button(
                    onClick = onSubmit,
                    enabled = uiState.answerInput.isNotBlank(),
                    modifier = Modifier.weight(1f).testTag(testTags.submitButton)
                ) { Text("Submit") }
            }
        } else {
            Text(
                text = if (feedback.isCorrect) "Correct!" else "Incorrect",
                color = if (feedback.isCorrect) {
                    themeAwareColor(CorrectAnswerColor, CorrectAnswerColorDark)
                } else {
                    MaterialTheme.colorScheme.error
                },
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag(testTags.feedbackText)
            )
            feedbackDetailPrefix(feedback)?.let { prefix ->
                // Capped at a fixed height + internally scrollable rather than left unbounded:
                // an item with many accepted synonyms could otherwise grow past this
                // non-scrolling Column's bounds and push the Continue button down underneath
                // the swipe-up handle's reserved space below, silently stealing its taps.
                ExpandableAnswerListText(
                    joined = feedback.correctAnswer,
                    resetKey = feedback,
                    prefix = prefix,
                    expandedMaxHeight = 96.dp,
                    modifier = Modifier.testTag(testTags.answerDetailText)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            GatedContinueButton(
                feedback = feedback,
                onContinue = onContinue,
                continueButtonTestTag = testTags.continueButton,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Reserves room so the swipe-up handle — pinned to the true bottom of the screen as its own
        // overlay in the screen's outer Box, not laid out inline here — doesn't cover this content.
        // See SubjectDetailSheet for why it lives outside this Column. The extra 24dp covers the
        // handle's own navigationBarsPadding, which grows its footprint on devices with a gesture
        // pill or 3-button nav bar.
        Spacer(modifier = Modifier.height(16.dp + SubjectDetailHandleHeight + 24.dp))
    }
}
