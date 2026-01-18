package nl.openschoolcloud.calendar.domain.repository

import kotlinx.coroutines.flow.Flow
import nl.openschoolcloud.calendar.domain.model.*
import java.time.Instant

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
     * Update an existing event
     * @param notifyAttendees Whether to notify attendees of changes
     */
    suspend fun updateEvent(event: Event, notifyAttendees: Boolean = true): Result<Event>
    
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
}

/**
 * Repository for calendars
 */
interface CalendarRepository {
    
    /**
     * Get all calendars for all accounts
     */
    fun getCalendars(): Flow<List<Calendar>>
    
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
 * Result of a sync operation
 */
data class SyncResult(
    val calendarId: String,
    val success: Boolean,
    val eventsAdded: Int = 0,
    val eventsUpdated: Int = 0,
    val eventsDeleted: Int = 0,
    val conflicts: Int = 0,
    val error: String? = null
)
