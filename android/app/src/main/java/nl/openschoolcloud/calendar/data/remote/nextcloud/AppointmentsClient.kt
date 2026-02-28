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
package nl.openschoolcloud.calendar.data.remote.nextcloud

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Nextcloud Calendar Appointments API.
 *
 * Nextcloud Calendar v3+ supports "Appointments" - a Calendly-style booking system.
 * Teachers create appointment configs, parents book via public link.
 *
 * API: GET /index.php/apps/calendar/v1/appointment_configs
 * Note: This is a direct Calendar app route, NOT an OCS endpoint.
 * Requires OCS-APIRequest: true header to bypass CSRF check.
 */
@Singleton
class AppointmentsClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AppointmentsClient"
        private const val APPOINTMENTS_PATH =
            "/index.php/apps/calendar/v1/appointment_configs"
    }

    data class AppointmentConfig(
        val id: Long,
        val name: String,
        val description: String?,
        val location: String?,
        val duration: Int, // in minutes (converted from seconds)
        val token: String,
        val visibility: String // "PUBLIC" or "PRIVATE"
    )

    /**
     * Extract base URL (scheme + host + port) from a stored URL that may
     * include path components (e.g. CalDAV paths).
     */
    private fun getBaseUrl(storedUrl: String): String {
        return try {
            val uri = URI(storedUrl)
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "${uri.scheme}://${uri.host}$port"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse URI, using trimmed URL: $storedUrl", e)
            storedUrl.trimEnd('/')
        }
    }

    /**
     * Fetch all appointment configurations for the authenticated user.
     *
     * @param serverUrl Base server URL (e.g., https://cloud.school.nl)
     * @param username Username
     * @param password App password
     * @return List of appointment configs, or empty if not supported
     */
    suspend fun getAppointmentConfigs(
        serverUrl: String,
        username: String,
        password: String
    ): Result<List<AppointmentConfig>> = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl(serverUrl)
        val url = "$baseUrl$APPOINTMENTS_PATH"
        Log.d(TAG, "Fetching appointment configs from: $url (user: $username)")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .header("OCS-APIRequest", "true")
            .header("Accept", "application/json")
            .get()
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Response: ${response.code} ${response.message}, body length: ${body.length}")

                when {
                    response.isSuccessful -> {
                        Log.d(TAG, "Response body: $body")
                        val configs = parseAppointmentConfigs(body)
                        Log.d(TAG, "Parsed ${configs.size} appointment configs")
                        Result.success(configs)
                    }
                    response.code == 404 -> {
                        Log.w(TAG, "Appointments API returned 404 - app not installed or not supported")
                        Result.success(emptyList())
                    }
                    response.code == 401 || response.code == 403 -> {
                        Log.e(TAG, "Authentication failed: ${response.code}, body: $body")
                        Result.failure(IOException("Authenticatie mislukt (${response.code})"))
                    }
                    else -> {
                        Log.e(TAG, "Server error: ${response.code} ${response.message}, body: $body")
                        Result.failure(
                            IOException("Server fout: ${response.code} ${response.message}")
                        )
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Network error fetching appointments", e)
            Result.failure(IOException("Netwerkfout: ${e.message}", e))
        }
    }

    private fun parseAppointmentConfigs(json: String): List<AppointmentConfig> {
        // Check for HTML response (auth failure or CSRF rejection)
        val trimmed = json.trimStart()
        if (trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html")) {
            Log.e(TAG, "Got HTML response instead of JSON - likely auth or CSRF failure")
            return emptyList()
        }

        return try {
            val root = JSONObject(json)

            // Response format: { "status": "success", "data": [...] }
            val status = root.optString("status", "")
            Log.d(TAG, "Response status: '$status'")

            if (status != "success") {
                Log.e(TAG, "API returned non-success status: $status, message: ${root.optString("message")}")
                return emptyList()
            }

            val data = root.optJSONArray("data")
            if (data == null) {
                Log.w(TAG, "No 'data' array in response. Keys: ${root.keys().asSequence().toList()}")
                return emptyList()
            }

            Log.d(TAG, "Found ${data.length()} items in data array")

            (0 until data.length()).map { i ->
                val obj = data.getJSONObject(i)
                // Nextcloud returns length in seconds (e.g. 1800 = 30 min)
                val lengthSeconds = obj.optInt("length", 1800)
                val durationMinutes = lengthSeconds / 60
                val token = obj.getString("token")

                AppointmentConfig(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    description = obj.optString("description", null),
                    location = obj.optString("location", null),
                    duration = durationMinutes,
                    token = token,
                    visibility = obj.optString("visibility", "PUBLIC")
                ).also {
                    Log.d(TAG, "Config: ${it.name} (${durationMinutes}min) → ...appointment/$token")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse appointment configs JSON", e)
            Log.e(TAG, "Raw JSON was: $json")
            emptyList()
        }
    }
}
