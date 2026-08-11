package com.crazyfluff.shellfstudy.core.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.crazyfluff.shellfstudy.core.data.TokenRepository
import com.crazyfluff.shellfstudy.fakes.FakeTokenCipher
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AuthInterceptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var tokenRepository: TokenRepository
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { tempFolder.newFile("test.preferences_pb") }
        )
        tokenRepository = TokenRepository(dataStore, FakeTokenCipher())
        client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(tokenRepository)).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun executeRequest() {
        server.enqueue(MockResponse.Builder().code(200).build())
        client.newCall(Request.Builder().url(server.url("/user")).build()).execute().close()
    }

    @Test
    fun `attaches Bearer token when one is stored`() = runTest {
        tokenRepository.saveToken("abc123")

        executeRequest()

        assertThat(server.takeRequest().headers["Authorization"]).isEqualTo("Bearer abc123")
    }

    @Test
    fun `omits Authorization header when no token is stored`() = runTest {
        executeRequest()

        assertThat(server.takeRequest().headers["Authorization"]).isNull()
    }

    @Test
    fun `always attaches the WaniKani revision header`() = runTest {
        executeRequest()

        assertThat(server.takeRequest().headers["Wanikani-Revision"]).isEqualTo("20170710")
    }
}
