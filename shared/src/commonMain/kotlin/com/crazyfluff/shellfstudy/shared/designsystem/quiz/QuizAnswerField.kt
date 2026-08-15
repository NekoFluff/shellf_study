package com.crazyfluff.shellfstudy.shared.designsystem.quiz

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.IntOffset
import com.crazyfluff.shellfstudy.shared.designsystem.text.RomajiVisualTransformation
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.label
import kotlin.math.roundToInt

/** The answer input shared by review sessions and lesson quizzes — a WaniKani-romaji-aware text
 *  field that shakes and warns when the typed answer looks like the *other* question type (see
 *  [com.crazyfluff.shellfstudy.shared.quiz.evaluateAnswer]), rather than silently grading it as a miss. */
@Composable
fun QuizAnswerField(
    value: String,
    onValueChange: (String) -> Unit,
    questionType: QuestionType,
    isAnswered: Boolean,
    answerTypeMismatchCount: Int,
    onSubmit: () -> Unit,
    answerFieldTestTag: String,
    typeMismatchTextTestTag: String,
    focusResetKey: Any?,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val answerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(focusResetKey) {
        answerFocusRequester.requestFocus()
    }

    val shakeOffset = remember { Animatable(0f) }
    var showTypeMismatchWarning by remember(focusResetKey) { mutableStateOf(false) }
    // Keyed on the count (not a boolean) so back-to-back identical mistakes still retrigger the
    // shake — a plain boolean wouldn't change value between two consecutive "wrong type" submits.
    LaunchedEffect(answerTypeMismatchCount) {
        if (answerTypeMismatchCount == 0) return@LaunchedEffect
        showTypeMismatchWarning = true
        shakeOffset.snapTo(0f)
        listOf(-12f, 12f, -8f, 8f, -4f, 0f).forEach { target ->
            shakeOffset.animateTo(target, animationSpec = tween(durationMillis = 40))
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = {
            showTypeMismatchWarning = false
            onValueChange(it)
        },
        label = { Text("答え") },
        singleLine = true,
        enabled = !isAnswered,
        visualTransformation = if (questionType == QuestionType.READING) {
            RomajiVisualTransformation(isComplete = isAnswered)
        } else {
            VisualTransformation.None
        },
        trailingIcon = trailingIcon,
        supportingText = if (showTypeMismatchWarning) {
            {
                Text(
                    text = "Expecting the ${questionType.label}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag(typeMismatchTextTestTag)
                )
            }
        } else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (!isAnswered) onSubmit() }),
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            .focusRequester(answerFocusRequester)
            .testTag(answerFieldTestTag)
    )
}
