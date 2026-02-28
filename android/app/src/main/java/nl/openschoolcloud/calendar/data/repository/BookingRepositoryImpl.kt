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
package nl.openschoolcloud.calendar.data.repository

import android.util.Log
import kotlinx.coroutines.flow.firstOrNull
import nl.openschoolcloud.calendar.data.local.dao.AccountDao
import nl.openschoolcloud.calendar.data.remote.auth.CredentialStorage
import nl.openschoolcloud.calendar.data.remote.nextcloud.AppointmentsClient
import nl.openschoolcloud.calendar.domain.model.BookingConfig
import nl.openschoolcloud.calendar.domain.model.BookingVisibility
import nl.openschoolcloud.calendar.domain.repository.BookingRepository
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookingRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val credentialStorage: CredentialStorage,
    private val appointmentsClient: AppointmentsClient
) : BookingRepository {

    override suspend fun getBookingConfigs(): Result<List<BookingConfig>> {
        // Try default account first, fall back to any non-local Nextcloud account
        var account = accountDao.getDefault()
        Log.d(TAG, "Default account: ${account?.id} (serverUrl=${account?.serverUrl})")

        if (account == null || account.serverUrl.isBlank()) {
            Log.d(TAG, "No default account or local account, trying all accounts")
            val allAccounts = accountDao.getAll().firstOrNull() ?: emptyList()
            account = allAccounts.firstOrNull { it.serverUrl.isNotBlank() }
            Log.d(TAG, "Fallback account: ${account?.id} (serverUrl=${account?.serverUrl})")
        }

        if (account == null || account.serverUrl.isBlank()) {
            Log.w(TAG, "No Nextcloud account found for booking configs")
            return Result.failure(IllegalStateException("Geen Nextcloud account geconfigureerd"))
        }

        // Diagnostic: log stored credential IDs for comparison
        val storedAccountIds = credentialStorage.getAllAccountIds()
        Log.d(TAG, "Looking for credentials with account.id='${account.id}'")
        Log.d(TAG, "All stored credential account IDs: $storedAccountIds")
        Log.d(TAG, "Account entity - id=${account.id}, username=${account.username}, serverUrl=${account.serverUrl}, isDefault=${account.isDefault}")

        var password = credentialStorage.getPassword(account.id)
        if (password == null) {
            Log.w(TAG, "No credentials for account.id='${account.id}', trying fallback...")

            // Fallback: try all stored credential IDs (account may have been recreated with different UUID)
            for (storedId in storedAccountIds) {
                val candidate = credentialStorage.getPassword(storedId)
                if (candidate != null) {
                    Log.d(TAG, "Found credentials under alternative ID: '$storedId'")
                    password = candidate
                    break
                }
            }
        }

        if (password == null) {
            Log.e(TAG, "No credentials found anywhere for account ${account.id}")
            return Result.failure(IllegalStateException("Inloggegevens niet gevonden voor account"))
        }
        Log.d(TAG, "Credentials found, proceeding with API call")

        val result = appointmentsClient.getAppointmentConfigs(
            serverUrl = account.serverUrl,
            username = account.username,
            password = password
        )

        return result.map { configs ->
            Log.d(TAG, "Mapping ${configs.size} configs to BookingConfig")
            val baseUrl = getBaseUrl(account.serverUrl)
            configs.map { config ->
                val bookingUrl = "$baseUrl/index.php/apps/calendar/appointment/${config.token}"
                BookingConfig(
                    id = config.id,
                    name = config.name,
                    description = config.description,
                    duration = config.duration,
                    token = config.token,
                    bookingUrl = bookingUrl,
                    calendarId = null,
                    visibility = when (config.visibility) {
                        "PRIVATE" -> BookingVisibility.PRIVATE
                        else -> BookingVisibility.PUBLIC
                    }
                )
            }
        }.also { result ->
            result.fold(
                onSuccess = { Log.d(TAG, "Successfully loaded ${it.size} booking configs") },
                onFailure = { Log.e(TAG, "Failed to load booking configs", it) }
            )
        }
    }

    private fun getBaseUrl(storedUrl: String): String {
        return try {
            val uri = URI(storedUrl)
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "${uri.scheme}://${uri.host}$port"
        } catch (e: Exception) {
            storedUrl.trimEnd('/')
        }
    }

    companion object {
        private const val TAG = "BookingRepository"
    }
}
