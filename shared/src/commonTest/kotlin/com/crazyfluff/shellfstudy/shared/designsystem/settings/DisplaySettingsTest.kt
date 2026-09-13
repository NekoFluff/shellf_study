package com.crazyfluff.shellfstudy.shared.designsystem.settings

import com.crazyfluff.shellfstudy.shared.data.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the one hand-written step between "what the user stored" and "what the UI renders".
 *
 * This mapping used to be spelled out field-by-field inside each feature ViewModel, which meant a
 * dropped line broke a single screen's setting and nothing else. It is now the only place that can
 * drop one, so every field is asserted with a value *opposite* to [DisplaySettings]'s default — a
 * field that stops being copied falls back to that default and fails here.
 */
class DisplaySettingsTest {

    @Test
    fun `every stored display preference reaches the rendering flags`() {
        val stored = AppSettings(
            showPitchAccent = false,
            restrictAudioToMp3 = true,
            showSubjectTypeLabel = true,
            showTotalTimer = true,
            showQuestionTimer = true,
            showStrokeOrder = false,
            useJapaneseKeyboard = true,
            showAnswerReadingPitchAccent = true,
            hideContextSentenceTranslations = false
        )

        val rendered = stored.toDisplaySettings()

        assertFalse(rendered.showPitchAccent)
        assertTrue(rendered.restrictAudioToMp3)
        assertTrue(rendered.showSubjectTypeLabel)
        assertTrue(rendered.showTotalTimer)
        assertTrue(rendered.showQuestionTimer)
        assertFalse(rendered.showStrokeOrder)
        assertTrue(rendered.useJapaneseKeyboard)
        assertTrue(rendered.showAnswerReadingPitchAccent)
        assertFalse(rendered.hideContextSentenceTranslations)
    }

    /**
     * Settings a ViewModel reads to decide what a session *does* must not leak into the rendering
     * flags — they are not display preferences, and a composable reading them would be deciding
     * behaviour from a rendering value.
     */
    @Test
    fun `behaviour-only settings are not part of the rendering flags`() {
        val stored = AppSettings(
            dailyLessonGoal = 42,
            lessonBatchSize = 7,
            closeEnoughAnswersEnabled = false,
            autoplayPronunciationAudio = false
        )

        assertEquals(
            DisplaySettings(),
            stored.toDisplaySettings()
        )
    }
}
