package com.crazyfluff.shellfstudy.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crazyfluff.shellfstudy.core.network.SubjectType

@Composable
fun subjectColor(type: SubjectType): Color = when (type) {
    SubjectType.RADICAL -> themeAwareColor(SubjectTypeColors.Radical, EinkSubjectColors.Radical)
    SubjectType.KANJI -> themeAwareColor(SubjectTypeColors.Kanji, EinkSubjectColors.Kanji)
    SubjectType.VOCABULARY -> themeAwareColor(SubjectTypeColors.Vocabulary, EinkSubjectColors.Vocabulary)
    SubjectType.KANA_VOCABULARY -> themeAwareColor(SubjectTypeColors.Vocabulary, EinkSubjectColors.Vocabulary)
}

fun subjectTypeLabel(type: SubjectType): String = when (type) {
    SubjectType.RADICAL -> "Radical"
    SubjectType.KANJI -> "Kanji"
    SubjectType.VOCABULARY -> "Vocabulary"
    SubjectType.KANA_VOCABULARY -> "Kana Vocabulary"
}
