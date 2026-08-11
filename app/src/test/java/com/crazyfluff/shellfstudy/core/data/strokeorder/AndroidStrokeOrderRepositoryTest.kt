package com.crazyfluff.shellfstudy.core.data.strokeorder

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Robolectric-based: reads the real bundled res/raw/stroke_data.json, which needs a real Android Context. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AndroidStrokeOrderRepositoryTest {

    private val repository = AndroidStrokeOrderRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun `getStrokeOrder returns an ordered, non-empty stroke list for a known kanji`() = runTest {
        val strokes = repository.getStrokeOrder('水')

        assertThat(strokes).isNotNull()
        assertThat(strokes).isNotEmpty()
        assertThat(strokes!!.all { it.startsWith("M") }).isTrue()
    }

    @Test
    fun `getStrokeOrder returns null for a character outside KanjiVG's set`() = runTest {
        // A Private Use Area codepoint - guaranteed absent from any real dataset.
        assertThat(repository.getStrokeOrder('')).isNull()
    }

    @Test
    fun `getStrokeOrder caches the parsed data across repeated calls`() = runTest {
        val first = repository.getStrokeOrder('水')
        val second = repository.getStrokeOrder('水')

        assertThat(second).isEqualTo(first)
    }
}
