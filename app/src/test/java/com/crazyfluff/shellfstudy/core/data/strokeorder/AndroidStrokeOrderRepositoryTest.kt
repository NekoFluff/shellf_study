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
        assertThat(strokes!!.all { it.pathData.startsWith("M") }).isTrue()
    }

    @Test
    fun `getStrokeOrder reuses KanjiVG's own curated stroke-number label positions`() = runTest {
        val strokes = repository.getStrokeOrder('水')!!

        // From KanjiVG's own StrokeNumbers_6c34 group ("1" at matrix(1 0 0 1 43.75 15.38)") —
        // a curated position, not one computed from the stroke's own start point.
        assertThat(strokes.first().labelX).isEqualTo(43.75f)
        assertThat(strokes.first().labelY).isEqualTo(15.38f)
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
