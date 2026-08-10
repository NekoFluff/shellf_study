package com.crazyfluff.shellfstudy.fakes

import com.crazyfluff.shellfstudy.core.data.TokenCipher

/** No-op stand-in for [TokenCipher] — real AES/Keystore crypto isn't available on the JVM test runner. */
class FakeTokenCipher : TokenCipher {
    override fun encrypt(plainText: String): String = "enc:$plainText"
    override fun decrypt(encoded: String): String = encoded.removePrefix("enc:")
}
