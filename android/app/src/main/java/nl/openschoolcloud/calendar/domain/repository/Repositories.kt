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
package nl.openschoolcloud.calendar.domain.repository

import kotlinx.coroutines.flow.Flow
import nl.openschoolcloud.calendar.domain.model.*
import java.time.Instant

/**
 * Thrown when the server rejects a write because the local ETag is stale
 * (HTTP 412 Precondition Failed) — another user changed the same event
 * between our load and our save. Callers should prompt the user to either
 * overwrite (retry with forceOverwrite=true) or discard local changes.
 */
class EventConflictException(message: String = "Event was modified by another user") :
    Exception(message)

/**
 * Repository for calendar events
 */
interface EventRepository {

    /**
     * Get events for a date range, optionally filtered by calendars
     */
    fun getEvents(
        start: Instant,
        end: Instant,
        calendarIds: List<String>? = null
    ): Flow<List<Event>>

    /**
     * Get a single event by ID
     */
    suspend fun getEvent(eventId: String): Event?

    /**
     * Create a new event
     * @param sendInvites Whether to send invites to attendees
     */
    suspend fun createEvent(event: Event, sendInvites: Boolean = true): Result<Event>

    /**
     * Update an existing event.
     *
     * @param notifyAttendees Whether to notify attendees of changes
     * @param forceOverwrite When true, skip the If-Match precondition check
     *   and overwrite whatever is on the server. Use only after the user
     *   has explicitly resolved an [EventConflictException].
     */
    suspend fun updateEvent(
        event: Event,
        notifyAttendees: Boolean = true,
        forceOverwrite: Boolean = false
    ): Result<Event>

    /**
     * Re-fetch a single event from the CalDAV server and overwrite the
     * local copy. Used to recover from an [EventConflictException]
     * after the user chooses to discard local changes.
     */
    suspend fun refreshEventFromServer(eventId: String): Result<Event>
    
    /**
     * Delete an event
     * @param notifyAttendees Whether to send cancellation to attendees
     */
    suspend fun deleteEvent(eventId: String, notifyAttendees: Boolean = true): Result<Unit>
    
    /**
     * Search events by query
     */
    fun searchEvents(query: String): Flow<List<Event>>
    
    /**
     * Get events with pending sync status
     */
    suspend fun getPendingEvents(): List<Event>

    /**
     * Get tasks for a date range
     */
    fun getTasks(start: Instant, end: Instant): Flow<List<Event>>

    /**
     * Toggle task completed status
     */
    suspend fun toggleTaskCompleted(eventId: String): Result<Event>
}

/**
 * Repository for calendars
 */
interface CalendarRepository {
    
    /**
     * Get visible calendars for all accounts
     */
    fun getCalendars(): Flow<List<Calendar>>

    /**
     * Get all calendars regardless of visibility (for Settings screen toggles)
     */
    fun getAllCalendars(): Flow<List<Calendar>>
    
    /**
     * Get calendars for a specific account
     */
    fun getCalendarsForAccount(accountId: String): Flow<List<Calendar>>
    
    /**
     * Get a single calendar by ID
     */
    suspend fun getCalendar(calendarId: String): Calendar?
    
    /**
     * Update calendar properties (visibility, color, order)
     */
    suspend fun updateCalendar(calendar: Calendar): Result<Calendar>
    
    /**
     * Discover calendars from server (refresh)
     */
    suspend fun discoverCalendars(accountId: String): Result<List<Calendar>>
    
    /**
     * Sync a calendar with the server
     */
    suspend fun syncCalendar(calendarId: String): Result<SyncResult>
    
    /**
     * Sync all calendars
     */
    suspend fun syncAll(): Result<List<SyncResult>>

    /**
     * Ensure a local-only calendar exists for standalone mode
     */
    suspend fun ensureLocalCalendarExists()

    /**
     * Generate diagnostic information for debug log export
     */
    suspend fun getDiagnosticInfo(): String
}

/**
 * Repository for accounts
 */
interface AccountRepository {
    
    /**
     * Get all accounts
     */
    fun getAccounts(): Flow<List<Account>>
    
    /**
     * Get the default account
     */
    suspend fun getDefaultAccount(): Account?
    
    /**
     * Get a single account by ID
     */
    suspend fun getAccount(accountId: String): Account?
    
    /**
     * Add a new account
     */
    suspend fun addAccount(
        serverUrl: String,
        username: String,
        password: String
    ): Result<Account>
    
    /**
     * Remove an account and all its data
     */
    suspend fun removeAccount(accountId: String): Result<Unit>
    
    /**
     * Set the default account
     */
    suspend fun setDefaultAccount(accountId: String): Result<Unit>
    
    /**
     * Verify account credentials
     */
    suspend fun verifyCredentials(
        serverUrl: String,
        username: String,
        password: String
    ): Result<AccountVerificationResult>
}

/**
 * Result of an account verification
 */
data class AccountVerificationResult(
    val success: Boolean,
    val displayName: String? = null,
    val email: String? = null,
    val principalUrl: String? = null,
    val calendarsFound: Int = 0,
    val error: String? = null
)

/**
 * Repository for booking/appointment configurations
 */
interface BookingRepository {

    /**
     * Get all appointment configurations for the default account.
     * Returns empty list if the server doesn't support appointments.
     */
    suspend fun getBookingConfigs(): Result<List<nl.openschoolcloud.calendar.domain.model.BookingConfig>>
}

/**
 * Repository for contacts from CardDAV addressbooks
 */
interface ContactRepository {

    fun getContacts(): Flow<List<Contact>>

    fun searchContacts(query: String): Flow<List<Contact>>

    suspend fun searchContactsSync(query: String): List<Contact>

    suspend fun syncContacts(accountId: String): Result<Int>

    suspend fun syncAllContacts(): Result<Int>
}

/**
 * Result of a sync operation
 */
data class SyncResult(
    val calendarId: String,
    val success: Boolean,
    val eventsAdded: Int = 0,
    val eventsUpdated: Int = 0,
    val eventsDeleted: Int = 0,
    val conflicts: Int = 0,
    val error: String? = null,
    val syncMode: String? = null // "full", "incremental", "ctag-unchanged", or null on error
)
