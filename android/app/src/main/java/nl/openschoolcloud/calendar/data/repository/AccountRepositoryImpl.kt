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

import android.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import nl.openschoolcloud.calendar.data.local.dao.AccountDao
import nl.openschoolcloud.calendar.data.local.dao.CalendarDao
import nl.openschoolcloud.calendar.data.local.entity.AccountEntity
import nl.openschoolcloud.calendar.data.local.entity.CalendarEntity
import nl.openschoolcloud.calendar.data.remote.auth.CredentialStorage
import nl.openschoolcloud.calendar.data.remote.caldav.CalDavClient
import nl.openschoolcloud.calendar.domain.model.Account
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.AccountVerificationResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AccountRepository
 *
 * Handles account management including:
 * - Adding/removing accounts
 * - CalDAV discovery
 * - Credential management
 */
@Singleton
class AccountRepositoryImpl @Inject constructor(
    private val accountDao: AccountDao,
    private val calendarDao: CalendarDao,
    private val calDavClient: CalDavClient,
    private val credentialStorage: CredentialStorage
) : AccountRepository {

    override fun getAccounts(): Flow<List<Account>> {
        return accountDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getDefaultAccount(): Account? {
        return accountDao.getDefault()?.toDomain()
    }

    override suspend fun getAccount(accountId: String): Account? {
        return accountDao.getById(accountId)?.toDomain()
    }

    override suspend fun addAccount(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Account> = withContext(Dispatchers.IO) {
        try {
            // 1. Normalize URL
            val normalizedUrl = normalizeUrl(serverUrl)

            // 2. Discover principal
            val principalUrl = calDavClient.discoverPrincipal(normalizedUrl, username, password)
                .getOrThrow()

            // 3. Discover calendar home
            val calendarHome = calDavClient.discoverCalendarHome(principalUrl, username, password)
                .getOrThrow()

            // 4. List calendars to verify connection and get initial calendars
            val calendars = calDavClient.listCalendars(calendarHome, username, password)
                .getOrThrow()

            if (calendars.isEmpty()) {
                return@withContext Result.failure(Exception("No calendars found"))
            }

            // 5. Check if account already exists for this server+username
            val existing = accountDao.getByServerAndUsername(normalizedUrl, username)
            if (existing != null) {
                // Update credentials and metadata on existing account
                credentialStorage.saveCredentials(existing.id, password)
                val updated = existing.copy(
                    principalUrl = principalUrl,
                    calendarHomeSet = calendarHome
                )
                accountDao.update(updated)

                // Insert only NEW calendars (preserve user visibility on existing ones)
                val existingCalIds = calendarDao.getAllSync()
                    .filter { it.accountId == existing.id }
                    .map { it.id }
                    .toSet()

                val newCalendars = calendars.mapIndexedNotNull { index, info ->
                    val calId = "${existing.id}_${info.url.hashCode()}"
                    if (calId in existingCalIds) return@mapIndexedNotNull null
                    CalendarEntity(
                        id = calId,
                        accountId = existing.id,
                        displayName = info.displayName,
                        colorInt = parseColor(info.color),
                        url = info.url,
                        ctag = info.ctag,
                        syncToken = info.syncToken,
                        readOnly = info.readOnly,
                        visible = true,
                        sortOrder = index
                    )
                }
                if (newCalendars.isNotEmpty()) {
                    calendarDao.insertAll(newCalendars)
                }

                return@withContext Result.success(updated.toDomain())
            }

            // 6. Create new account
            val accountId = UUID.randomUUID().toString()
            val isFirst = accountDao.getDefault() == null

            val entity = AccountEntity(
                id = accountId,
                serverUrl = normalizedUrl,
                username = username,
                displayName = username, // Could fetch from server later
                email = null,
                principalUrl = principalUrl,
                calendarHomeSet = calendarHome,
                isDefault = isFirst
            )

            accountDao.insert(entity)
            credentialStorage.saveCredentials(accountId, password)

            // 7. Save discovered calendars
            val calendarEntities = calendars.mapIndexed { index, info ->
                CalendarEntity(
                    id = "${accountId}_${info.url.hashCode()}",
                    accountId = accountId,
                    displayName = info.displayName,
                    colorInt = parseColor(info.color),
                    url = info.url,
                    ctag = info.ctag,
                    syncToken = info.syncToken,
                    readOnly = info.readOnly,
                    visible = true,
                    sortOrder = index
                )
            }
            calendarDao.insertAll(calendarEntities)

            Result.success(entity.toDomain())

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun verifyCredentials(
        serverUrl: String,
        username: String,
        password: String
    ): Result<AccountVerificationResult> = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = normalizeUrl(serverUrl)

            val principalUrl = calDavClient.discoverPrincipal(normalizedUrl, username, password)
                .getOrElse {
                    return@withContext Result.success(
                        AccountVerificationResult(
                            success = false,
                            error = "Could not connect: ${it.message}"
                        )
                    )
                }

            val calendarHome = calDavClient.discoverCalendarHome(principalUrl, username, password)
                .getOrElse {
                    return@withContext Result.success(
                        AccountVerificationResult(
                            success = false,
                            error = "Could not find calendars: ${it.message}"
                        )
                    )
                }

            val calendars = calDavClient.listCalendars(calendarHome, username, password)
                .getOrElse { emptyList() }

            Result.success(
                AccountVerificationResult(
                    success = true,
                    principalUrl = principalUrl,
                    calendarsFound = calendars.size
                )
            )
        } catch (e: Exception) {
            Result.success(
                AccountVerificationResult(
                    success = false,
                    error = e.message ?: "Unknown error"
                )
            )
        }
    }

    override suspend fun removeAccount(accountId: String): Result<Unit> {
        return try {
            accountDao.deleteById(accountId)
            credentialStorage.deleteCredentials(accountId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setDefaultAccount(accountId: String): Result<Unit> {
        return try {
            accountDao.clearDefault()
            accountDao.setDefault(accountId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Normalize URL to ensure consistent format
     */
    private fun normalizeUrl(url: String): String {
        var normalized = url.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }
        return normalized.trimEnd('/')
    }

    /**
     * Parse color string to Int, with fallback to default OSC blue
     */
    private fun parseColor(colorString: String?): Int {
        if (colorString == null) return DEFAULT_CALENDAR_COLOR
        return try {
            Color.parseColor(colorString)
        } catch (e: Exception) {
            DEFAULT_CALENDAR_COLOR
        }
    }

    companion object {
        // Default OSC blue color
        private const val DEFAULT_CALENDAR_COLOR = 0xFF3B9FD9.toInt()
    }
}

/**
 * Extension function to convert AccountEntity to domain Account
 */
private fun AccountEntity.toDomain() = Account(
    id = id,
    serverUrl = serverUrl,
    username = username,
    displayName = displayName,
    email = email,
    principalUrl = principalUrl,
    calendarHomeSet = calendarHomeSet,
    isDefault = isDefault
)
