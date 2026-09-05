package com.crazyfluff.shellfstudy.shared.designsystem.quiz

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.unit.IntOffset
import com.crazyfluff.shellfstudy.shared.designsystem.text.JapaneseText
import com.crazyfluff.shellfstudy.shared.designsystem.text.RomajiOutputTransformation
import com.crazyfluff.shellfstudy.shared.designsystem.theme.LocalJapaneseFontFamily
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType
import com.crazyfluff.shellfstudy.shared.quiz.label
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop

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
    useJapaneseKeyboard: Boolean = false,
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

    // Owned locally rather than driven by `value`/`onValueChange` directly so the field goes
    // through Compose's modern text-input pipeline instead of the legacy CoreTextField path, whose
    // IME cursor-anchor bookkeeping has a framework crash (see LegacyCursorAnchorInfoBuilder).
    // Re-created whenever focusResetKey changes, which is exactly when the ViewModel itself resets
    // `value` back to "" (new question or undo) — so there's no need for continuous two-way sync.
    val fieldState = remember(focusResetKey) { TextFieldState(value) }
    LaunchedEffect(fieldState) {
        // Drop the initial emission (the seed value the state was just created with) — only
        // react to actual edits, so this can't race the reset that happens on the same
        // recomposition that creates a fresh fieldState.
        snapshotFlow { fieldState.text.toString() }.drop(1).collect {
            showTypeMismatchWarning = false
            onValueChange(it)
        }
    }

    // Compose keys BasicTextField's internal transformed-state (and the IME session tied to it)
    // on this instance's identity, recreating both whenever it changes — a plain class with no
    // equals() override is a fresh, "changed" instance every recomposition, so a freshly allocated
    // RomajiOutputTransformation on every keystroke was tearing down and re-establishing the IME
    // session on every letter typed (the software keyboard hiding and reshowing). remember() here
    // keeps its identity stable across keystrokes, only creating a new one when what it actually
    // depends on changes.
    val outputTransformation = remember(useJapaneseKeyboard, questionType, isAnswered) {
        if (!useJapaneseKeyboard && questionType == QuestionType.READING) {
            RomajiOutputTransformation(isComplete = isAnswered)
        } else {
            null
        }
    }

    OutlinedTextField(
        state = fieldState,
        label = { JapaneseText("答え") },
        // Only reading questions ever contain kana (typed via a Japanese IME, or live-converted
        // from romaji by outputTransformation above) — meaning answers are plain English, so they
        // keep the ambient Latin font instead of picking up Noto Sans JP unconditionally.
        textStyle = if (questionType == QuestionType.READING) {
            LocalTextStyle.current.copy(fontFamily = LocalJapaneseFontFamily.current)
        } else {
            LocalTextStyle.current
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        enabled = !isAnswered,
        outputTransformation = outputTransformation,
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
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done,
            hintLocales = if (useJapaneseKeyboard) {
                LocaleList(Locale(if (questionType == QuestionType.READING) "ja" else "en"))
            } else null
        ),
        onKeyboardAction = KeyboardActionHandler { if (!isAnswered) onSubmit() },
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
            .focusRequester(answerFocusRequester)
            .testTag(answerFieldTestTag)
    )
}
