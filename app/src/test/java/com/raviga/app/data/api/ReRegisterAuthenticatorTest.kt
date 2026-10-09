package com.raviga.app.data.api

import com.raviga.app.data.local.SessionTokens
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReRegisterAuthenticatorTest {

    private class FakeSession(
        override var clientId: String?,
        override var accessToken: String?,
        override val installId: String = "install-1",
    ) : SessionTokens {
        var recoveryKey: String? = null
        override fun set(clientId: String, accessToken: String, recoveryKey: String?) {
            this.clientId = clientId
            this.accessToken = accessToken
            if (recoveryKey != null) this.recoveryKey = recoveryKey
        }
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private lateinit var server: MockWebServer
    private var signedOut = false

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    private fun client(session: FakeSession): OkHttpClient {
        val base = server.url("/v1/").toString()
        return OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor { session.accessToken })
            .authenticator(ReRegisterAuthenticator(base, OkHttpClient(), json, session, onSignedOut = { signedOut = true }))
            .build()
    }

    private fun get(client: OkHttpClient) =
        client.newCall(Request.Builder().url(server.url("/v1/me")).build()).execute().use { it.code }

    private fun registered(clientId: String, token: String, isNew: Boolean, recoveryKey: String? = null) =
        MockResponse().setResponseCode(201).setBody(
            """{"clientId":"$clientId","deviceId":"dv_2","accessToken":"$token",""" +
                """"recoveryKey":${recoveryKey?.let { "\"$it\"" } ?: "null"},"isNewClient":$isNew}""",
        )

    @Test fun replacedTokenHealsWithTheSameClient() {
        val session = FakeSession("cl_1", "old")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(registered("cl_1", "fresh", isNew = false))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        assertEquals(200, get(client(session)))

        server.takeRequest()
        val register = server.takeRequest()
        assertEquals("/v1/devices/register", register.path)
        assertTrue(register.body.readUtf8().contains("\"installId\":\"install-1\""))
        assertEquals("Bearer fresh", server.takeRequest().getHeader("Authorization"))
        assertEquals("fresh", session.accessToken)
        assertFalse(signedOut)
    }

    @Test fun revokedDeviceSignsOutAndDoesNotReplayAgainstTheNewClient() {
        val session = FakeSession("cl_1", "old")
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(registered("cl_2", "fresh", isNew = true, recoveryKey = "rark_new"))

        assertEquals(401, get(client(session)))

        assertEquals(2, server.requestCount)
        assertTrue(signedOut)
        assertEquals("cl_2", session.clientId)
        assertEquals("rark_new", session.recoveryKey)
    }

    @Test fun noSessionMeansNothingToHeal() {
        val session = FakeSession(null, null)
        server.enqueue(MockResponse().setResponseCode(401))

        assertEquals(401, get(client(session)))

        assertEquals(1, server.requestCount)
        assertNull(session.clientId)
        assertFalse(signedOut)
    }

    @Test fun aTokenRefreshedByAnotherCallIsReusedWithoutRegistering() {
        val session = FakeSession("cl_1", "old")
        val client = client(session)
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        // Another request re-registered while this one was in flight.
        val call = client.newCall(Request.Builder().url(server.url("/v1/me")).header("Authorization", "Bearer stale").build())

        assertEquals(200, call.execute().use { it.code })

        server.takeRequest()
        assertEquals("Bearer old", server.takeRequest().getHeader("Authorization"))
        assertEquals(2, server.requestCount)
    }
}
