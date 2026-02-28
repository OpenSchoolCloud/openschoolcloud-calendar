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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import nl.openschoolcloud.calendar.data.local.dao.AccountDao
import nl.openschoolcloud.calendar.data.local.dao.CalendarDao
import nl.openschoolcloud.calendar.data.local.dao.EventDao
import nl.openschoolcloud.calendar.data.local.entity.AccountEntity
import nl.openschoolcloud.calendar.data.local.entity.CalendarEntity
import nl.openschoolcloud.calendar.data.remote.auth.CredentialStorage
import nl.openschoolcloud.calendar.data.remote.caldav.CalDavClient
import nl.openschoolcloud.calendar.data.remote.ical.ICalParser
import nl.openschoolcloud.calendar.domain.model.Calendar
import nl.openschoolcloud.calendar.domain.model.SyncStatus
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import nl.openschoolcloud.calendar.domain.repository.SyncResult
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CalendarRepository
 *
 * Handles calendar management and sync operations with Nextcloud CalDAV server
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val calendarDao: CalendarDao,
    private val eventDao: EventDao,
    private val accountDao: AccountDao,
    private val calDavClient: CalDavClient,
    private val icalParser: ICalParser,
    private val credentialStorage: CredentialStorage
) : CalendarRepository {

    override fun getCalendars(): Flow<List<Calendar>> {
        return calendarDao.getVisible().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getAllCalendars(): Flow<List<Calendar>> {
        return calendarDao.getAll().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getCalendarsForAccount(accountId: String): Flow<List<Calendar>> {
        return calendarDao.getByAccount(accountId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCalendar(calendarId: String): Calendar? {
        return calendarDao.getById(calendarId)?.toDomain()
    }

    override suspend fun updateCalendar(calendar: Calendar): Result<Calendar> {
        return try {
            val entity = CalendarEntity(
                id = calendar.id,
                accountId = calendar.accountId,
                displayName = calendar.displayName,
                colorInt = calendar.color,
                url = calendar.url,
                ctag = calendar.ctag,
                syncToken = calendar.syncToken,
                readOnly = calendar.readOnly,
                visible = calendar.visible,
                sortOrder = calendar.order
            )
            calendarDao.update(entity)
            Result.success(calendar)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun discoverCalendars(accountId: String): Result<List<Calendar>> {
        return withContext(Dispatchers.IO) {
            try {
                val account = accountDao.getById(accountId)
                    ?: return@withContext Result.failure(Exception("Account not found"))

                val password = getPasswordWithFallback(accountId)
                    ?: return@withContext Result.failure(Exception("Credentials not found"))

                val calendarHome = account.calendarHomeSet
                    ?: return@withContext Result.failure(Exception("Calendar home not found"))

                val calendarsResult = calDavClient.listCalendars(
                    calendarHomeUrl = calendarHome,
                    username = account.username,
                    password = password
                )

                calendarsResult.mapCatching { calendarInfoList ->
                    val entities = calendarInfoList.mapIndexed { index, info ->
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
                    calendarDao.insertAll(entities)
                    entities.map { it.toDomain() }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun syncCalendar(calendarId: String): Result<SyncResult> {
        return withContext(Dispatchers.IO) {
            var lastException: Exception? = null
            val maxRetries = 3
            val baseDelayMs = 1000L

            repeat(maxRetries) { attempt ->
                try {
                    val result = doSyncCalendar(calendarId)
                    if (result.isSuccess) {
                        return@withContext result
                    }
                    lastException = result.exceptionOrNull() as? Exception
                } catch (e: Exception) {
                    lastException = e

                    // Don't retry on non-recoverable errors
                    if (e is IllegalArgumentException || e is SecurityException) {
                        return@withContext Result.failure(e)
                    }
                }

                // Exponential backoff: 1s, 2s, 4s
                if (attempt < maxRetries - 1) {
                    val delayTime = baseDelayMs * (1 shl attempt)
                    delay(delayTime)
                }
            }

            Result.failure(lastException ?: IOException("Sync failed after $maxRetries attempts"))
        }
    }

    /**
     * Perform actual sync operation for a single calendar
     */
    private suspend fun doSyncCalendar(calendarId: String): Result<SyncResult> {
        val calendar = calendarDao.getById(calendarId)
            ?: return Result.failure(Exception("Calendar not found"))

        val account = accountDao.getById(calendar.accountId)
            ?: return Result.failure(Exception("Account not found"))

        val password = getPasswordWithFallback(account.id)
            ?: return Result.failure(Exception("Credentials not found"))

        // Check if calendar has changed (CTag)
        val currentCtag = calDavClient.getCtag(
            calendar.url,
            account.username,
            password
        ).getOrNull()

        if (currentCtag != null && currentCtag == calendar.ctag) {
            // No changes on server
            return Result.success(
                SyncResult(
                    calendarId = calendarId,
                    success = true,
                    eventsAdded = 0,
                    eventsUpdated = 0,
                    eventsDeleted = 0
                )
            )
        }

        // Fetch events (last 3 months + next 12 months)
        val startDate = LocalDate.now().minusMonths(3)
        val endDate = LocalDate.now().plusMonths(12)

        val eventsResult = calDavClient.fetchEvents(
            calendarUrl = calendar.url,
            username = account.username,
            password = password,
            startDate = startDate,
            endDate = endDate
        )

        val eventDataList = eventsResult.getOrElse {
            return Result.failure(it)
        }

        // Parse and store events
        var added = 0
        var updated = 0

        val newEventUids = mutableSetOf<String>()

        for (eventData in eventDataList) {
            val entity = icalParser.parseEvent(
                icalData = eventData.icalData,
                calendarId = calendarId,
                etag = eventData.etag
            ) ?: continue

            newEventUids.add(entity.uid)

            val existing = eventDao.getByUid(entity.uid)
            if (existing == null) {
                eventDao.insert(entity)
                added++
            } else if (existing.etag != entity.etag) {
                eventDao.update(entity)
                updated++
            }
        }

        // Delete events that no longer exist on server
        val localEvents = eventDao.getByCalendarSync(calendarId)
        var deleted = 0
        for (local in localEvents) {
            if (local.uid !in newEventUids && local.syncStatus == SyncStatus.SYNCED.name) {
                eventDao.deleteByUid(local.uid)
                deleted++
            }
        }

        // Update calendar ctag
        calendarDao.updateSyncInfo(calendarId, currentCtag, null)

        return Result.success(
            SyncResult(
                calendarId = calendarId,
                success = true,
                eventsAdded = added,
                eventsUpdated = updated,
                eventsDeleted = deleted
            )
        )
    }

    override suspend fun syncAll(): Result<List<SyncResult>> {
        return withContext(Dispatchers.IO) {
            try {
                // Clean up orphaned accounts (no credentials, no events)
                cleanupOrphanedAccounts()

                // Discover new calendars from server before syncing
                discoverNewCalendars()

                val calendars = calendarDao.getVisibleSync()
                val results = mutableListOf<SyncResult>()

                for (calendar in calendars) {
                    val result = syncCalendar(calendar.id)
                    result.onSuccess { syncResult ->
                        results.add(syncResult)
                    }.onFailure { error ->
                        results.add(
                            SyncResult(
                                calendarId = calendar.id,
                                success = false,
                                error = error.message
                            )
                        )
                    }
                }

                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /**
     * Discover new calendars from all non-local accounts.
     * Only inserts calendars that don't already exist in Room (preserves user visibility settings).
     * Best-effort: failures are silently ignored so sync can proceed.
     */
    private suspend fun discoverNewCalendars() {
        try {
            val accounts = accountDao.getAllSync()
            for (account in accounts) {
                if (account.id == LOCAL_ACCOUNT_ID) continue
                val calendarHome = account.calendarHomeSet ?: continue
                val password = getPasswordWithFallback(account.id) ?: continue

                val result = calDavClient.listCalendars(
                    calendarHomeUrl = calendarHome,
                    username = account.username,
                    password = password
                )

                result.onSuccess { calendarInfoList ->
                    val existingIds = calendarDao.getAllSync()
                        .filter { it.accountId == account.id }
                        .map { it.id }
                        .toSet()

                    val newCalendars = calendarInfoList.mapIndexedNotNull { index, info ->
                        val calId = "${account.id}_${info.url.hashCode()}"
                        if (calId in existingIds) return@mapIndexedNotNull null
                        CalendarEntity(
                            id = calId,
                            accountId = account.id,
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
                }
            }
        } catch (_: Exception) {
            // Discovery is best-effort; don't fail the sync
        }
    }

    /**
     * Remove accounts that have no stored credentials and no events in any of their calendars.
     * These are ghost accounts left over from duplicate logins or failed setups.
     * Best-effort: failures are silently ignored.
     */
    private suspend fun cleanupOrphanedAccounts() {
        try {
            val accounts = accountDao.getAllSync()
            for (account in accounts) {
                if (account.id == LOCAL_ACCOUNT_ID) continue

                // Check if this account has credentials (including fallback)
                val hasPassword = credentialStorage.getPassword(account.id) != null
                if (hasPassword) continue

                // No direct credentials — check if any stored credential exists at all
                val anyCredentials = credentialStorage.getAllAccountIds().any { storedId ->
                    credentialStorage.getPassword(storedId) != null
                }
                if (anyCredentials) continue

                // No credentials at all — check if this account has any events
                val calendars = calendarDao.getAllSync().filter { it.accountId == account.id }
                val hasEvents = calendars.any { eventDao.getCountByCalendar(it.id) > 0 }
                if (hasEvents) continue

                // Orphaned account: no credentials and no events — clean up
                calendarDao.deleteByAccount(account.id)
                accountDao.deleteById(account.id)
            }
        } catch (_: Exception) {
            // Cleanup is best-effort
        }
    }

    override suspend fun ensureLocalCalendarExists() {
        withContext(Dispatchers.IO) {
            // Ensure local account exists (needed for foreign key constraint)
            val existingAccount = accountDao.getById(LOCAL_ACCOUNT_ID)
            if (existingAccount == null) {
                accountDao.insert(
                    AccountEntity(
                        id = LOCAL_ACCOUNT_ID,
                        serverUrl = "",
                        username = "local",
                        displayName = "Lokaal",
                        email = null,
                        principalUrl = null,
                        calendarHomeSet = null,
                        isDefault = false
                    )
                )
            }
            // Ensure local calendar exists
            val existingCalendar = calendarDao.getById(LOCAL_CALENDAR_ID)
            if (existingCalendar == null) {
                calendarDao.insert(
                    CalendarEntity(
                        id = LOCAL_CALENDAR_ID,
                        accountId = LOCAL_ACCOUNT_ID,
                        displayName = "Mijn Agenda",
                        colorInt = DEFAULT_CALENDAR_COLOR,
                        url = "",
                        ctag = null,
                        syncToken = null,
                        readOnly = false,
                        visible = true,
                        sortOrder = 0
                    )
                )
            }
        }
    }

    override suspend fun getDiagnosticInfo(): String {
        return withContext(Dispatchers.IO) {
            val sb = StringBuilder()
            sb.appendLine("=== OSC Calendar Debug Log ===")
            sb.appendLine("Timestamp: ${Instant.now()}")
            sb.appendLine()

            // Accounts
            sb.appendLine("--- Accounts ---")
            val accounts = accountDao.getAllSync()
            if (accounts.isEmpty()) {
                sb.appendLine("(none)")
            }
            for (account in accounts) {
                sb.appendLine("ID: ${account.id}")
                sb.appendLine("  Server: ${account.serverUrl}")
                sb.appendLine("  Username: ${account.username}")
                sb.appendLine("  CalendarHome: ${account.calendarHomeSet ?: "(null)"}")
                sb.appendLine("  Default: ${account.isDefault}")
            }
            sb.appendLine()

            // Calendars
            sb.appendLine("--- Calendars ---")
            val calendars = calendarDao.getAllSync()
            if (calendars.isEmpty()) {
                sb.appendLine("(none)")
            }
            for (cal in calendars) {
                val eventCount = eventDao.getCountByCalendar(cal.id)
                sb.appendLine("ID: ${cal.id}")
                sb.appendLine("  Account: ${cal.accountId}")
                sb.appendLine("  Name: ${cal.displayName}")
                sb.appendLine("  URL: ${cal.url}")
                sb.appendLine("  Visible: ${cal.visible}")
                sb.appendLine("  ReadOnly: ${cal.readOnly}")
                sb.appendLine("  CTag: ${cal.ctag ?: "(null)"}")
                sb.appendLine("  Events: $eventCount")
            }
            sb.appendLine()

            // CalDAV discovery test
            sb.appendLine("--- CalDAV Discovery ---")
            for (account in accounts) {
                if (account.id == LOCAL_ACCOUNT_ID) continue
                val calendarHome = account.calendarHomeSet
                if (calendarHome == null) {
                    sb.appendLine("Account ${account.id}: No calendarHomeSet")
                    continue
                }
                val password = getPasswordWithFallback(account.id)
                if (password == null) {
                    sb.appendLine("Account ${account.id}: No password stored (tried fallback)")
                    continue
                }
                try {
                    val result = calDavClient.listCalendars(
                        calendarHomeUrl = calendarHome,
                        username = account.username,
                        password = password
                    )
                    result.fold(
                        onSuccess = { infos ->
                            sb.appendLine("Account ${account.id}: Found ${infos.size} calendars on server")
                            for (info in infos) {
                                sb.appendLine("  - ${info.displayName} (${info.url})")
                            }
                        },
                        onFailure = { error ->
                            sb.appendLine("Account ${account.id}: Discovery failed: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    sb.appendLine("Account ${account.id}: Exception: ${e.message}")
                }
            }

            sb.toString()
        }
    }

    /**
     * Get password for an account, with fallback to any stored credential.
     * Handles the case where account ID changed (e.g., EncryptedSharedPreferences issue
     * on some devices, or account recreated with different UUID).
     * If a fallback password is found, re-saves it under the correct account ID.
     */
    private fun getPasswordWithFallback(accountId: String): String? {
        credentialStorage.getPassword(accountId)?.let { return it }

        // Fallback: try all stored credential IDs
        for (storedId in credentialStorage.getAllAccountIds()) {
            val candidate = credentialStorage.getPassword(storedId)
            if (candidate != null) {
                // Re-save under current account ID for future lookups
                credentialStorage.saveCredentials(accountId, candidate)
                return candidate
            }
        }
        return null
    }

    /**
     * Parse color string to Int, with fallback to default OSC blue
     */
    private fun parseColor(colorString: String?): Int {
        if (colorString == null) return DEFAULT_CALENDAR_COLOR
        return try {
            android.graphics.Color.parseColor(colorString)
        } catch (e: Exception) {
            DEFAULT_CALENDAR_COLOR
        }
    }

    companion object {
        private const val DEFAULT_CALENDAR_COLOR = 0xFF3B9FD9.toInt()
        const val LOCAL_ACCOUNT_ID = "local"
        const val LOCAL_CALENDAR_ID = "local_default"
    }
}

/**
 * Extension function to convert CalendarEntity to domain Calendar
 */
private fun CalendarEntity.toDomain() = Calendar(
    id = id,
    accountId = accountId,
    displayName = displayName,
    color = colorInt,
    url = url,
    ctag = ctag,
    syncToken = syncToken,
    readOnly = readOnly,
    visible = visible,
    order = sortOrder
)
