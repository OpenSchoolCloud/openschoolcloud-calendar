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

import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nl.openschoolcloud.calendar.BuildConfig
import nl.openschoolcloud.calendar.data.remote.auth.AuthManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for the OSC-Cloud sync backend (Plan C, tasks 1-8). Every call is
 * bearer-authenticated with the Authentik OIDC access token — never logged,
 * only ever placed in the Authorization header.
 */
@Singleton
open class OscCloudApi @Inject constructor(
    private val httpClient: OkHttpClient,
    private val authManager: AuthManager
) {
    /** Overridable only so tests can point requests at a local MockWebServer. */
    open fun baseUrl(): String = BuildConfig.OSC_CLOUD_API_BASE

    private val moshi = Moshi.Builder().build()
    private val statusAdapter = moshi.adapter(OscCloudStatusJson::class.java)
    private val trialRequestAdapter = moshi.adapter(TrialRequestJson::class.java)
    private val membersRequestAdapter = moshi.adapter(MembersRequestJson::class.java)

    suspend fun startTrial(familyId: String, memberCount: Int): Result<OscCloudStatus> {
        val body = trialRequestAdapter.toJson(TrialRequestJson(familyId, memberCount))
        return execute("POST", "/v1/sync/trial", body)
    }

    suspend fun status(): Result<OscCloudStatus> = execute("GET", "/v1/sync/status", null)

    suspend fun updateMembers(memberCount: Int): Result<OscCloudStatus> {
        val body = membersRequestAdapter.toJson(MembersRequestJson(memberCount))
        return execute("POST", "/v1/sync/members", body)
    }

    private suspend fun execute(
        method: String,
        path: String,
        jsonBody: String?
    ): Result<OscCloudStatus> = withContext(Dispatchers.IO) {
        try {
            val token = authManager.currentAccessToken()
                ?: return@withContext Result.failure(IllegalStateException("Not logged in"))

            val requestBuilder = Request.Builder()
                .url(baseUrl() + path)
                .header("Authorization", "Bearer $token")

            when (method) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post(
                    (jsonBody ?: "{}").toRequestBody(JSON_MEDIA_TYPE)
                )
                else -> return@withContext Result.failure(IllegalArgumentException("Unsupported method $method"))
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        OscCloudException("HTTP ${response.code} for $method $path", response.code)
                    )
                }
                val responseBody = response.body?.string()
                    ?: return@withContext Result.failure(OscCloudException("Empty response for $method $path"))
                val parsed = statusAdapter.fromJson(responseBody)
                    ?: return@withContext Result.failure(OscCloudException("Malformed response for $method $path"))
                Result.success(parsed.toDomain())
            }
        } catch (e: IOException) {
            Result.failure(OscCloudException("Network error: ${e.message}", cause = e))
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

class OscCloudException(
    message: String,
    val httpCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause)
