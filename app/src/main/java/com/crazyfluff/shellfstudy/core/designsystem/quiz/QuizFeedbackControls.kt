package com.crazyfluff.shellfstudy.core.designsystem.quiz

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.crazyfluff.shellfstudy.shared.quiz.AnswerFeedback

/** Time an incorrect answer's Continue button stays disabled for, so a reflexive fast tap can't
 *  blow past feedback before it's been registered. Correct answers never lock. */
const val ContinueLockMs = 1200

/** How long the button takes to settle back to its resting size after [ContinuePopOvershoot] —
 *  keeps the pop feeling snappy rather than jiggly. */
private val ContinuePopSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

/** How far below resting size the button dips right as it unlocks, before [ContinuePopSpring]
 *  springs it back past 1x and settles — small enough to read as a snap into place, not a bounce. */
private const val ContinuePopOvershoot = 0.94f

@Composable
fun GatedContinueButton(
    feedback: AnswerFeedback,
    onContinue: () -> Unit,
    continueButtonTestTag: String,
    modifier: Modifier = Modifier
) {
    // Drives the ring directly (rather than deriving it from a separately-toggled "unlocked"
    // boolean) so it actually animates 0->1 while it's visible, and unlocking happens in sync with
    // it visually finishing — not the instant before it, which just hid a ring stuck at 0.
    val lockProgress = remember(feedback) { Animatable(if (feedback.isCorrect) 1f else 0f) }
    val continueUnlocked = feedback.isCorrect || lockProgress.value >= 1f
    LaunchedEffect(feedback) {
        if (!feedback.isCorrect) {
            lockProgress.snapTo(0f)
            lockProgress.animateTo(1f, animationSpec = tween(ContinueLockMs))
        }
    }

    // The button "pops" into its fully-enabled look right as the ring finishes tracing its
    // outline, rather than silently flipping enabled underneath a static button — a quick
    // dip-then-overshoot-then-settle scale reads as the button visibly arriving, echoing the
    // ring's own sense of building up to completion. Correct answers unlock instantly (no ring to
    // finish), so they skip the pop rather than playing it for no visible reason.
    val popScale = remember(feedback) { Animatable(1f) }
    LaunchedEffect(feedback, continueUnlocked) {
        if (continueUnlocked && !feedback.isCorrect) {
            popScale.snapTo(ContinuePopOvershoot)
            popScale.animateTo(1f, animationSpec = ContinuePopSpring)
        }
    }

    val ringColor = MaterialTheme.colorScheme.primary
    val buttonShape = ButtonDefaults.shape

    Box(modifier = modifier) {
        Button(
            onClick = onContinue,
            enabled = continueUnlocked,
            modifier = Modifier
                .fillMaxWidth()
                .scale(popScale.value)
                .drawWithContent {
                    drawContent()
                    if (!continueUnlocked) {
                        // Traces the button's own outline (not a small separate ring) — empty at
                        // 0%, all the way around at 100% — in the color the button will become the
                        // instant it unlocks, so the ring reads as that color "arriving" rather
                        // than an unrelated countdown decoration.
                        val outlinePath = Path().apply { addOutline(buttonShape.createOutline(size, layoutDirection, this@drawWithContent)) }
                        val measure = PathMeasure().apply { setPath(outlinePath, forceClosed = false) }
                        val segment = Path()
                        measure.getSegment(0f, measure.length * lockProgress.value, segment)
                        drawPath(
                            path = segment,
                            color = ringColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
                .testTag(continueButtonTestTag)
        ) { Text("Continue") }
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
