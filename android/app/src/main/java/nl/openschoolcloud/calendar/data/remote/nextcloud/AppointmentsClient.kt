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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Nextcloud Calendar Appointments OCS API.
 *
 * Nextcloud Calendar v3+ supports "Appointments" - a Calendly-style booking system.
 * Teachers create appointment configs, parents book via public link.
 *
 * API: GET /ocs/v2.php/apps/calendar/api/v1/appointment_configs
 */
@Singleton
class AppointmentsClient @Inject constructor(
    private val httpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "AppointmentsClient"
        private const val OCS_APPOINTMENTS_PATH =
            "/ocs/v2.php/apps/calendar/api/v1/appointment_configs"
    }

    data class AppointmentConfig(
        val id: Long,
        val name: String,
        val description: String?,
        val duration: Int,
        val token: String,
        val targetCalendarUri: String?,
        val visibility: String // "PUBLIC" or "PRIVATE"
    )

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
        val url = "${serverUrl.trimEnd('/')}$OCS_APPOINTMENTS_PATH"
        Log.d(TAG, "Fetching appointment configs from: $url (user: $username)")

        val request = Request.Builder()
            .url(url)
            .header("Authorization", Credentials.basic(username, password))
            .header("OCS-APIREQUEST", "true")
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
        return try {
            val root = JSONObject(json)
            val ocs = root.getJSONObject("ocs")

            // Log OCS meta status for debugging
            val meta = ocs.optJSONObject("meta")
            Log.d(TAG, "OCS meta: status=${meta?.optString("status")}, statuscode=${meta?.optInt("statuscode")}")

            val data = ocs.optJSONArray("data")
            if (data == null) {
                Log.w(TAG, "No 'data' array in OCS response. Keys in ocs: ${ocs.keys().asSequence().toList()}")
                return emptyList()
            }

            Log.d(TAG, "Found ${data.length()} items in data array")

            (0 until data.length()).map { i ->
                val obj = data.getJSONObject(i)
                // Nextcloud returns length in seconds; convert to minutes
                val lengthRaw = obj.optInt("length", 1800)
                val durationMinutes = if (lengthRaw > 60) lengthRaw / 60 else lengthRaw

                AppointmentConfig(
                    id = obj.getLong("id"),
                    name = obj.getString("name"),
                    description = obj.optString("description", null),
                    duration = durationMinutes,
                    token = obj.getString("token"),
                    targetCalendarUri = obj.optString("targetCalendarUri", null),
                    visibility = obj.optString("visibility", "PUBLIC")
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse appointment configs JSON", e)
            Log.e(TAG, "Raw JSON was: $json")
            emptyList()
        }
    }
}
