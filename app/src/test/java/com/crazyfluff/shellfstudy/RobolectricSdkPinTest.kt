package com.crazyfluff.shellfstudy

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the Robolectric SDK level for the rest of the suite.
 *
 * The level is set once in `src/test/resources/robolectric.properties` rather than by a `@Config` on
 * every class, which is only an improvement if that file is actually read: a properties file that is
 * misplaced or misspelled is ignored silently, and the suite then runs against this project's targetSdk
 * (37), where Robolectric 4.15.1's shadows are incomplete — surfacing as unrelated-looking failures in
 * whichever test happens to touch a missing shadow, or as none at all.
 *
 * `RobolectricTestRunner` reads the property before this test body runs, so asserting the level here
 * asserts the file was honoured.
 */
@RunWith(AndroidJUnit4::class)
class RobolectricSdkPinTest {

    @Test
    fun `the suite runs against the SDK pinned in robolectric properties`() {
        assertThat(Build.VERSION.SDK_INT).isEqualTo(35)
    }
}
