package com.crazyfluff.shellfstudy.shared.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.crazyfluff.shellfstudy.shared.network.SubjectType

// Eink values are derived from the color scheme (primary/secondary/tertiary = EinkSubjectColors.*
// in EinkColorScheme) so the scheme is the single source of truth for grayscale overrides.
@Composable
fun subjectColor(type: SubjectType): Color = when (type) {
    SubjectType.RADICAL -> themeAwareColor(SubjectTypeColors.Radical, MaterialTheme.colorScheme.tertiary)
    SubjectType.KANJI -> themeAwareColor(SubjectTypeColors.Kanji, MaterialTheme.colorScheme.secondary)
    SubjectType.VOCABULARY -> themeAwareColor(SubjectTypeColors.Vocabulary, MaterialTheme.colorScheme.primary)
    SubjectType.KANA_VOCABULARY -> themeAwareColor(SubjectTypeColors.Vocabulary, MaterialTheme.colorScheme.primary)
}

/** Named shorthands — use these instead of [themeAwareColor] or bare [SubjectTypeColors] references. */
@Composable fun radicalColor(): Color = subjectColor(SubjectType.RADICAL)
@Composable fun kanjiColor(): Color = subjectColor(SubjectType.KANJI)
@Composable fun vocabularyColor(): Color = subjectColor(SubjectType.VOCABULARY)

fun subjectTypeLabel(type: SubjectType): String = when (type) {
    SubjectType.RADICAL -> "Radical"
    SubjectType.KANJI -> "Kanji"
    SubjectType.VOCABULARY -> "Vocabulary"
    SubjectType.KANA_VOCABULARY -> "Kana Vocabulary"
}
