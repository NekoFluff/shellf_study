package com.crazyfluff.shellfstudy.shared.designsystem.text

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import com.crazyfluff.shellfstudy.shared.util.RomajiConverter

/**
 * Renders the user's raw romaji keystrokes as their live hiragana conversion, without touching
 * the underlying answer input state — that stays exactly what the user typed, so there's no
 * feedback loop where already-converted kana gets fed back through the converter as more text is
 * typed (which would mis-convert a mid-word "n" before its following character is known). Shared
 * by Review and Lesson's reading fields; the meaning field uses no output transformation.
 *
 * Unlike the old [androidx.compose.ui.text.input.VisualTransformation]-based version, this doesn't
 * hand-roll an offset mapping between raw and displayed text — [TextFieldBuffer]'s [replace] tracks
 * the edit itself, and the cursor/IME anchor math is derived from that automatically.
 *
 * @param isComplete Forwarded to [RomajiConverter.convert] as-is. Pass `false` while the field is
 * still editable (a trailing "n" is ambiguous — more input might still arrive), and `true` once an
 * answer has been submitted and graded, so the disabled field reflects the same fully-resolved
 * conversion that grading actually checked against, instead of leaving a stray "n" that grading
 * already treated as ん.
 */
class RomajiOutputTransformation(private val isComplete: Boolean = false) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        val conversion = RomajiConverter.convert(asCharSequence().toString(), isComplete = isComplete)
        replace(0, length, conversion.output)
    }
}
