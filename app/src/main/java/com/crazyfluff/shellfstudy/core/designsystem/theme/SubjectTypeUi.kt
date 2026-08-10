package com.crazyfluff.shellfstudy.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.crazyfluff.shellfstudy.core.network.SubjectType

fun subjectColor(type: SubjectType): Color = when (type) {
    SubjectType.RADICAL -> SubjectTypeColors.Radical
    SubjectType.KANJI -> SubjectTypeColors.Kanji
    SubjectType.VOCABULARY -> SubjectTypeColors.Vocabulary
    SubjectType.KANA_VOCABULARY -> SubjectTypeColors.Vocabulary
}

fun subjectTypeLabel(type: SubjectType): String = when (type) {
    SubjectType.RADICAL -> "Radical"
    SubjectType.KANJI -> "Kanji"
    SubjectType.VOCABULARY -> "Vocabulary"
    SubjectType.KANA_VOCABULARY -> "Kana Vocabulary"
}
