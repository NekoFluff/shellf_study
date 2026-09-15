package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.crazyfluff.shellfstudy.shared.quiz.QuestionType

fun questionTypeColor(type: QuestionType): Color = when (type) {
    QuestionType.READING -> QuestionTypeReadingColor
    QuestionType.MEANING -> QuestionTypeMeaningColor
}
