package com.crazyfluff.shellfstudy.core.designsystem.text

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProcessTextLookupTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `buildProcessTextIntent sets action, mime type, and extras`() {
        val intent = buildProcessTextIntent("水")

        assertThat(intent.action).isEqualTo(Intent.ACTION_PROCESS_TEXT)
        assertThat(intent.type).isEqualTo("text/plain")
        assertThat(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)).isEqualTo("水")
        assertThat(intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)).isTrue()
    }

    @Test
    fun `canHandleProcessText is false when no app can resolve it`() {
        val intent = buildProcessTextIntent("水")

        assertThat(canHandleProcessText(context, intent)).isFalse()
    }

    @Test
    fun `canHandleProcessText is true when an app can resolve it`() {
        val intent = buildProcessTextIntent("水")
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.craxic.akebifree"
                name = "com.craxic.akebifree.ProcessTextActivity"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(intent, resolveInfo)

        assertThat(canHandleProcessText(context, intent)).isTrue()
    }
}
