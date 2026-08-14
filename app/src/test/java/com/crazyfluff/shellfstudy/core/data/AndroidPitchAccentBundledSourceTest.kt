package com.crazyfluff.shellfstudy.core.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.model.PitchAccent
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric-based: reads the real bundled res/raw/pitch_info.json, which needs a real Android Context. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AndroidPitchAccentBundledSourceTest {

    private val source = AndroidPitchAccentBundledSource(ApplicationProvider.getApplicationContext())

    @Test
    fun `get returns a word's readings including a null-reading wildcard entry`() {
        val entries = source.get("お土産")

        assertThat(entries).containsExactly(
            PitchAccent(reading = "オミヤゲ", partOfSpeech = null, pitchNumber = 0),
            PitchAccent(reading = null, partOfSpeech = null, pitchNumber = 0)
        )
    }

    @Test
    fun `get returns multiple readings for a word that has more than one`() {
        val entries = source.get("水")

        assertThat(entries).containsExactly(
            PitchAccent(reading = "スイ", partOfSpeech = null, pitchNumber = 1),
            PitchAccent(reading = "ミズ", partOfSpeech = null, pitchNumber = 0),
            PitchAccent(reading = null, partOfSpeech = null, pitchNumber = 0)
        )
    }

    @Test
    fun `get returns an empty list for a word not in the dictionary`() {
        assertThat(source.get("絶対にない単語xyz")).isEmpty()
    }
}
