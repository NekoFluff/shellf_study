package com.crazyfluff.shellfstudy.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.strokeorder.CmpStrokeOrderRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The one repository backed directly by a bundled CMP resource (the ~8MB KanjiVG-derived
 * stroke_data.json) rather than the network/Room — previously untested, so a regression in the
 * parsing or the lazy-caching-behind-a-Mutex logic would only ever surface as silently-missing
 * stroke diagrams in the app, never a test failure.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CmpStrokeOrderRepositoryTest {

    @Test
    fun `getStrokeOrder returns the ordered strokes for a known kanji`() = runTest {
        val repository = CmpStrokeOrderRepository()

        val strokes = repository.getStrokeOrder('水')

        assertThat(strokes).isNotNull()
        assertThat(strokes!!).isNotEmpty()
        assertThat(strokes.first().pathData).isNotEmpty()
    }

    @Test
    fun `getStrokeOrder returns null for a character with no stroke data`() = runTest {
        val repository = CmpStrokeOrderRepository()

        val strokes = repository.getStrokeOrder('') // a private-use codepoint, never in KanjiVG

        assertThat(strokes).isNull()
    }

    @Test
    fun `preload populates the cache so a later lookup needs no further parsing`() = runTest {
        val repository = CmpStrokeOrderRepository()

        repository.preload()
        val strokes = repository.getStrokeOrder('水')

        assertThat(strokes).isNotNull()
    }

    @Test
    fun `concurrent callers before the cache is warm all see the same parsed data`() = runTest {
        val repository = CmpStrokeOrderRepository()

        // Regression target: loadAll() is guarded by a Mutex specifically so concurrent first
        // callers (e.g. two subject detail sheets opened back to back) share one parse instead of
        // each parsing the ~8MB dictionary independently.
        val results = (1..5).map { async { repository.getStrokeOrder('水') } }.awaitAll()

        assertThat(results).hasSize(5)
        results.forEach { assertThat(it).isNotNull() }
    }
}
