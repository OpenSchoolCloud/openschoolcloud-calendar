/*
 * OpenSchoolCloud Calendar
 * Copyright (C) 2025 OpenSchoolCloud / Aldewereld Consultancy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package nl.openschoolcloud.calendar.data.remote.osccloud

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import nl.openschoolcloud.calendar.data.remote.auth.AuthManager
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OscCloudApiTest {

    private lateinit var server: MockWebServer
    private val authManager = mockk<AuthManager>()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun apiAgainst(baseUrl: String) = TestableOscCloudApi(OkHttpClient(), authManager, baseUrl)

    @Test fun `startTrial sends a bearer header and parses the status`() = runTest {
        coEvery { authManager.currentAccessToken() } returns "token-123"
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"status":"TRIAL","trial_days_left":30,"member_count":2,"caldav_url":"https://x/dav/","app_password":"pw"}"""
            )
        )

        val result = apiAgainst(server.url("/").toString().trimEnd('/')).startTrial("f1", 2)

        assertTrue(result.isSuccess)
        val status = result.getOrThrow()
        assertEquals("TRIAL", status.status)
        assertEquals(30, status.trialDaysLeft)
        assertEquals("pw", status.appPassword)

        val recorded = server.takeRequest()
        assertEquals("Bearer token-123", recorded.getHeader("Authorization"))
        assertEquals("POST", recorded.method)
    }

    @Test fun `status fails without throwing when there is no access token`() = runTest {
        coEvery { authManager.currentAccessToken() } returns null

        val result = apiAgainst(server.url("/").toString().trimEnd('/')).status()

        assertTrue(result.isFailure)
        assertNull(server.takeRequest(1, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test fun `status surfaces a failure Result on a non-2xx response`() = runTest {
        coEvery { authManager.currentAccessToken() } returns "token-123"
        server.enqueue(MockResponse().setResponseCode(402))

        val result = apiAgainst(server.url("/").toString().trimEnd('/')).status()

        assertTrue(result.isFailure)
    }
}

/**
 * OscCloudApi reads its base URL from BuildConfig, which unit tests can't
 * repoint. This subclass overrides only the base URL resolution so the
 * request-building/parsing logic under test is the real implementation.
 */
private class TestableOscCloudApi(
    httpClient: OkHttpClient,
    authManager: AuthManager,
    private val baseUrl: String
) : OscCloudApi(httpClient, authManager) {
    override fun baseUrl(): String = baseUrl
}
