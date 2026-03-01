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
import nl.openschoolcloud.calendar.data.remote.caldav.CalDavException
import nl.openschoolcloud.calendar.data.remote.caldav.EventData
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
     * Perform actual sync operation for a single calendar.
     *
     * Sync strategy:
     * 1. PROPFIND to get current CTag + SyncToken from server
     * 2. CTag comparison: if CTag unchanged, skip (no changes on server)
     * 3. If we have a stored syncToken → try incremental sync via sync-collection
     *    - If token expired (HTTP 400/403) → fall back to full sync
     * 4. If no syncToken → full sync via calendar-query REPORT
     * 5. Save new CTag + SyncToken for next sync
     */
    private suspend fun doSyncCalendar(calendarId: String): Result<SyncResult> {
        val calendar = calendarDao.getById(calendarId)
            ?: return Result.failure(Exception("Calendar not found: $calendarId"))

        val account = accountDao.getById(calendar.accountId)
            ?: return Result.failure(Exception("Account not found: ${calendar.accountId}"))

        val password = getPasswordWithFallback(account.id)
            ?: return Result.failure(Exception("Credentials not found for account: ${account.id}"))

        // Step 1: Get current CTag + SyncToken from server
        val ctagInfo = calDavClient.getCtagInfo(
            calendar.url,
            account.username,
            password
        ).getOrElse { e ->
            return Result.failure(
                Exception("PROPFIND failed for ${calendar.displayName}: ${e.message}", e)
            )
        }

        // Step 2: CTag comparison — skip if no changes
        if (ctagInfo.ctag != null && ctagInfo.ctag == calendar.ctag) {
            return Result.success(
                SyncResult(
                    calendarId = calendarId,
                    success = true,
                    syncMode = "ctag-unchanged"
                )
            )
        }

        // Step 3: Try incremental sync if we have a stored syncToken
        val storedSyncToken = calendar.syncToken
        if (storedSyncToken != null && storedSyncToken.isNotBlank()) {
            val incrementalResult = doIncrementalSync(
                calendarId = calendarId,
                calendarUrl = calendar.url,
                calendarName = calendar.displayName,
                username = account.username,
                password = password,
                storedSyncToken = storedSyncToken,
                newCtag = ctagInfo.ctag,
                serverSyncToken = ctagInfo.syncToken
            )

            // If incremental sync succeeded, return it
            if (incrementalResult.isSuccess) {
                return incrementalResult
            }

            // If token expired (400/403), fall through to full sync
            val error = incrementalResult.exceptionOrNull()
            val isTokenExpired = error is CalDavException &&
                    (error.httpCode == 400 || error.httpCode == 403)

            if (!isTokenExpired) {
                // Non-token error (network, auth) — don't retry with full sync
                return incrementalResult
            }
            // Fall through to full sync
        }

        // Step 4: Full sync via calendar-query REPORT
        return doFullSync(
            calendarId = calendarId,
            calendarUrl = calendar.url,
            calendarName = calendar.displayName,
            username = account.username,
            password = password,
            newCtag = ctagInfo.ctag,
            serverSyncToken = ctagInfo.syncToken
        )
    }

    /**
     * Incremental sync using sync-collection REPORT.
     * Only fetches changed/deleted events since the last syncToken.
     */
    private suspend fun doIncrementalSync(
        calendarId: String,
        calendarUrl: String,
        calendarName: String,
        username: String,
        password: String,
        storedSyncToken: String,
        newCtag: String?,
        serverSyncToken: String?
    ): Result<SyncResult> {
        val syncResponse = calDavClient.syncCollection(
            calendarUrl = calendarUrl,
            username = username,
            password = password,
            syncToken = storedSyncToken
        ).getOrElse { e ->
            return Result.failure(
                if (e is CalDavException) e
                else Exception("sync-collection failed for $calendarName: ${e.message}", e)
            )
        }

        // Process changed events
        var added = 0
        var updated = 0
        for (eventData in syncResponse.changed) {
            val entity = icalParser.parseEvent(
                icalData = eventData.icalData,
                calendarId = calendarId,
                etag = eventData.etag
            ) ?: continue

            val existing = eventDao.getByUid(entity.uid)
            if (existing == null) {
                eventDao.insert(entity)
                added++
            } else {
                eventDao.update(entity)
                updated++
            }
        }

        // Process deleted events
        var deleted = 0
        for (href in syncResponse.deleted) {
            val uid = extractUidFromHref(href) ?: continue
            val existing = eventDao.getByUid(uid)
            if (existing != null && existing.calendarId == calendarId) {
                eventDao.deleteByUid(uid)
                deleted++
            }
        }

        // Save new sync state
        val newSyncToken = syncResponse.syncToken.ifBlank { serverSyncToken }
        calendarDao.updateSyncInfo(calendarId, newCtag, newSyncToken)

        return Result.success(
            SyncResult(
                calendarId = calendarId,
                success = true,
                eventsAdded = added,
                eventsUpdated = updated,
                eventsDeleted = deleted,
                syncMode = "incremental"
            )
        )
    }

    /**
     * Full sync using calendar-query REPORT.
     * Fetches ALL events and reconciles with local database.
     */
    private suspend fun doFullSync(
        calendarId: String,
        calendarUrl: String,
        calendarName: String,
        username: String,
        password: String,
        newCtag: String?,
        serverSyncToken: String?
    ): Result<SyncResult> {
        // Fetch all events (no time-range for complete sync)
        val eventsResult = calDavClient.fetchEvents(
            calendarUrl = calendarUrl,
            username = username,
            password = password,
            startDate = null,
            endDate = null
        )

        val eventDataList = eventsResult.getOrElse { e ->
            return Result.failure(
                Exception("calendar-query failed for $calendarName: ${e.message}", e)
            )
        }

        // Parse and upsert events
        var added = 0
        var updated = 0
        val serverEventUids = mutableSetOf<String>()

        for (eventData in eventDataList) {
            val entity = icalParser.parseEvent(
                icalData = eventData.icalData,
                calendarId = calendarId,
                etag = eventData.etag
            ) ?: continue

            serverEventUids.add(entity.uid)

            val existing = eventDao.getByUid(entity.uid)
            if (existing == null) {
                eventDao.insert(entity)
                added++
            } else if (existing.etag != entity.etag) {
                eventDao.update(entity)
                updated++
            }
        }

        // Delete local events that no longer exist on server
        var deleted = 0
        val localEvents = eventDao.getByCalendarSync(calendarId)
        for (local in localEvents) {
            if (local.uid !in serverEventUids && local.syncStatus == SyncStatus.SYNCED.name) {
                eventDao.deleteByUid(local.uid)
                deleted++
            }
        }

        // Save new sync state (CTag + SyncToken for next incremental sync)
        calendarDao.updateSyncInfo(calendarId, newCtag, serverSyncToken)

        return Result.success(
            SyncResult(
                calendarId = calendarId,
                success = true,
                eventsAdded = added,
                eventsUpdated = updated,
                eventsDeleted = deleted,
                syncMode = "full"
            )
        )
    }

    /**
     * Extract UID from a CalDAV event href.
     * Nextcloud uses the UID as the filename: /remote.php/dav/.../uid.ics
     */
    private fun extractUidFromHref(href: String): String? {
        val filename = href.substringAfterLast('/')
        return if (filename.endsWith(".ics")) {
            filename.removeSuffix(".ics")
        } else null
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
                val hasPassword = getPasswordWithFallback(account.id) != null
                sb.appendLine("ID: ${account.id}")
                sb.appendLine("  Server: ${account.serverUrl}")
                sb.appendLine("  Username: ${account.username}")
                sb.appendLine("  CalendarHome: ${account.calendarHomeSet ?: "(null)"}")
                sb.appendLine("  Default: ${account.isDefault}")
                sb.appendLine("  Credentials: ${if (hasPassword) "OK" else "MISSING"}")
            }
            sb.appendLine()

            // Calendars with sync details
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
                sb.appendLine("  SyncToken: ${cal.syncToken ?: "(null)"}")
                sb.appendLine("  Events: $eventCount")
                sb.appendLine("  Next sync mode: ${if (cal.syncToken != null) "incremental" else "full"}")
            }
            sb.appendLine()

            // CalDAV discovery + sync test
            sb.appendLine("--- CalDAV Discovery & Sync Test ---")
            for (account in accounts) {
                if (account.id == LOCAL_ACCOUNT_ID) continue
                val calendarHome = account.calendarHomeSet
                if (calendarHome == null) {
                    sb.appendLine("Account ${account.username}: No calendarHomeSet")
                    continue
                }
                val password = getPasswordWithFallback(account.id)
                if (password == null) {
                    sb.appendLine("Account ${account.username}: No password stored (tried fallback)")
                    continue
                }

                // Discovery
                try {
                    val result = calDavClient.listCalendars(
                        calendarHomeUrl = calendarHome,
                        username = account.username,
                        password = password
                    )
                    result.fold(
                        onSuccess = { infos ->
                            sb.appendLine("Account ${account.username}: Found ${infos.size} calendars on server")
                            for (info in infos) {
                                sb.appendLine("  - ${info.displayName}")
                                sb.appendLine("    URL: ${info.url}")
                                sb.appendLine("    CTag: ${info.ctag ?: "(null)"}")
                                sb.appendLine("    SyncToken: ${info.syncToken ?: "(null)"}")
                                sb.appendLine("    SupportsEvents: ${info.supportsEvents}")
                            }
                        },
                        onFailure = { error ->
                            sb.appendLine("Account ${account.username}: Discovery failed: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    sb.appendLine("Account ${account.username}: Exception: ${e.message}")
                }

                // Per-calendar CTag/sync test
                val accountCalendars = calendars.filter { it.accountId == account.id }
                for (cal in accountCalendars) {
                    if (cal.url.isBlank()) continue
                    try {
                        val ctagResult = calDavClient.getCtagInfo(
                            cal.url, account.username, password
                        )
                        ctagResult.fold(
                            onSuccess = { info ->
                                val ctagChanged = info.ctag != cal.ctag
                                sb.appendLine("  Sync check [${cal.displayName}]:")
                                sb.appendLine("    Server CTag: ${info.ctag ?: "(null)"}")
                                sb.appendLine("    Stored CTag: ${cal.ctag ?: "(null)"}")
                                sb.appendLine("    Changed: $ctagChanged")
                                sb.appendLine("    Server SyncToken: ${info.syncToken ?: "(null)"}")
                            },
                            onFailure = { error ->
                                sb.appendLine("  Sync check [${cal.displayName}]: FAILED - ${error.message}")
                            }
                        )
                    } catch (e: Exception) {
                        sb.appendLine("  Sync check [${cal.displayName}]: Exception - ${e.message}")
                    }
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
