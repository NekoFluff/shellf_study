package com.crazyfluff.shellfstudy.core.designsystem.text

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.crazyfluff.shellfstudy.shared.util.RomajiConverter

/**
 * Renders the user's raw romaji keystrokes as their live hiragana conversion, without touching
 * the underlying answer input state — that stays exactly what the user typed, so there's no
 * feedback loop where already-converted kana gets fed back through the converter as more text is
 * typed (which would mis-convert a mid-word "n" before its following character is known). Shared
 * by Review and Lesson's reading fields; the meaning field uses [VisualTransformation.None].
 *
 * The [OffsetMapping] is derived from [RomajiConverter.convert]'s per-step boundaries rather than
 * a fixed formula, so tapping/dragging/arrow-keying through the displayed hiragana correctly
 * repositions the cursor in the underlying romaji text (and vice versa) instead of always
 * snapping to the end. An offset that lands strictly inside a multi-character romaji-to-kana step
 * (e.g. partway through "sha") has no exact counterpart on the other side, so it snaps to the far
 * end of that step's span — the same way a real IME can't place a cursor mid-syllable.
 *
 * @param isComplete Forwarded to [RomajiConverter.convert] as-is. Pass `false` while the field is
 * still editable (a trailing "n" is ambiguous — more input might still arrive), and `true` once an
 * answer has been submitted and graded, so the disabled field reflects the same fully-resolved
 * conversion that grading actually checked against, instead of leaving a stray "n" that grading
 * already treated as ん.
 */
class RomajiVisualTransformation(private val isComplete: Boolean = false) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val conversion = RomajiConverter.convert(text.text, isComplete = isComplete)
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                mapOffset(offset, conversion.rawBoundaries, conversion.hiraganaBoundaries)

            override fun transformedToOriginal(offset: Int): Int =
                mapOffset(offset, conversion.hiraganaBoundaries, conversion.rawBoundaries)
        }
        return TransformedText(AnnotatedString(conversion.output), offsetMapping)
    }
}

/**
 * Maps [offset] from one boundary space to the other. [fromBoundaries] and [toBoundaries] are
 * parallel, non-decreasing, equal-length arrays (see [RomajiConverter.Conversion]) describing the
 * same sequence of conversion steps in each space. Always returns a value coerced into
 * `[0, toBoundaries.last()]`, so a malformed or unexpected offset can't throw — it degrades to the
 * nearest sane position instead of crashing the text field.
 */
private fun mapOffset(offset: Int, fromBoundaries: IntArray, toBoundaries: IntArray): Int {
    if (fromBoundaries.isEmpty() || toBoundaries.isEmpty()) return 0
    val clamped = offset.coerceIn(0, fromBoundaries.last())
    for (index in 0 until fromBoundaries.size - 1) {
        val segmentStart = fromBoundaries[index]
        val segmentEnd = fromBoundaries[index + 1]
        if (clamped in segmentStart..segmentEnd) {
            return if (clamped == segmentStart) toBoundaries[index] else toBoundaries[index + 1]
        }
    }
    return toBoundaries.last()
}
