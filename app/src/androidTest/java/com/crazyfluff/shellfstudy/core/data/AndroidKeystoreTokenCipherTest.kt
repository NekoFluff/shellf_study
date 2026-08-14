package com.crazyfluff.shellfstudy.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.crazyfluff.shellfstudy.shared.data.AndroidKeystoreTokenCipher
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on-device because the Android Keystore provider isn't available on the host JVM. */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreTokenCipherTest {

    @Test
    fun encryptingThenDecrypting_returnsTheOriginalPlainText() {
        val cipher = AndroidKeystoreTokenCipher()
        val original = "wk-1234567890abcdef"

        val encrypted = cipher.encrypt(original)
        assertThat(encrypted).isNotEqualTo(original)

        val decrypted = cipher.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(original)
    }

    @Test
    fun encryptingTheSameValueTwice_producesDifferentCiphertextDueToRandomIv() {
        val cipher = AndroidKeystoreTokenCipher()

        val first = cipher.encrypt("same-token")
        val second = cipher.encrypt("same-token")

        assertThat(first).isNotEqualTo(second)
        assertThat(cipher.decrypt(first)).isEqualTo("same-token")
        assertThat(cipher.decrypt(second)).isEqualTo("same-token")
    }
}
