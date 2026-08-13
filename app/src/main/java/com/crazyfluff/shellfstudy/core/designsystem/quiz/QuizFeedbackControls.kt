package com.crazyfluff.shellfstudy.core.designsystem.quiz

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.core.quiz.AnswerFeedback

/** Time an incorrect answer's Continue button stays disabled for, so a reflexive fast tap can't
 *  blow past feedback before it's been registered. Correct answers never lock. */
const val ContinueLockMs = 1200

@Composable
fun GatedContinueButton(
    feedback: AnswerFeedback,
    onContinue: () -> Unit,
    continueButtonTestTag: String,
    modifier: Modifier = Modifier
) {
    // Drives the fill directly (rather than deriving it from a separately-toggled "unlocked"
    // boolean) so the bar actually animates 0->1 while it's visible, and unlocking happens in
    // sync with it visually finishing — not the instant before it, which just hid a bar stuck at 0.
    val lockProgress = remember(feedback) { Animatable(if (feedback.isCorrect) 1f else 0f) }
    val continueUnlocked = feedback.isCorrect || lockProgress.value >= 1f
    LaunchedEffect(feedback) {
        if (!feedback.isCorrect) {
            lockProgress.snapTo(0f)
            lockProgress.animateTo(1f, animationSpec = tween(ContinueLockMs))
        }
    }

    Box(modifier = modifier) {
        Button(
            onClick = onContinue,
            enabled = continueUnlocked,
            modifier = Modifier.fillMaxWidth().testTag(continueButtonTestTag)
        ) { Text("Continue") }
        if (!continueUnlocked) {
            // A small ring on the button's trailing edge — empty at 0%, a full ring at 100% —
            // rather than a bar along an edge, so the lock timer reads as a countdown rather than
            // a loading bar (which it isn't; nothing is actually loading).
            CircularProgressIndicator(
                progress = { lockProgress.value },
                color = MaterialTheme.colorScheme.onSurface,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                strokeCap = StrokeCap.Round,
                strokeWidth = 2.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 20.dp)
                    .size(16.dp)
            )
        }
    }
}

/** Label for the small secondary line under the Correct!/Incorrect headline — kept short (capped
 *  candidate list, single line with ellipsis, tap-to-expand for the rest — see [formatAnswerList])
 *  so an item with lots of synonyms doesn't push the Continue button (or, on the review screen,
 *  the swipe-up handle below it) down. Null means nothing to show below the headline. */
fun feedbackDetailPrefix(feedback: AnswerFeedback): String? = when {
    !feedback.isCorrect -> "Answer:"
    feedback.wasCloseMatch -> "Close to:"
    feedback.answerCount > 1 -> "Also accepted:"
    else -> null
}
