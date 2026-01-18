package nl.openschoolcloud.calendar.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * Represents a calendar event
 */
data class Event(
    val uid: String,
    val calendarId: String,
    val summary: String,
    val description: String? = null,
    val location: String? = null,
    val dtStart: Instant,
    val dtEnd: Instant? = null,
    val allDay: Boolean = false,
    val timeZone: ZoneId = ZoneId.systemDefault(),
    val rrule: String? = null, // Recurrence rule (iCal format)
    val color: Int? = null, // Override color (null = use calendar color)
    val organizer: Attendee? = null,
    val attendees: List<Attendee> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val status: EventStatus = EventStatus.CONFIRMED,
    val created: Instant? = null,
    val lastModified: Instant? = null,
    val etag: String? = null, // Server version tag
    val syncStatus: SyncStatus = SyncStatus.SYNCED
)

/**
 * Represents an event attendee
 */
data class Attendee(
    val email: String,
    val name: String? = null,
    val role: AttendeeRole = AttendeeRole.REQ_PARTICIPANT,
    val status: AttendeeStatus = AttendeeStatus.NEEDS_ACTION
)

enum class AttendeeRole {
    CHAIR,
    REQ_PARTICIPANT,
    OPT_PARTICIPANT,
    NON_PARTICIPANT
}

enum class AttendeeStatus {
    NEEDS_ACTION,
    ACCEPTED,
    DECLINED,
    TENTATIVE
}

enum class EventStatus {
    TENTATIVE,
    CONFIRMED,
    CANCELLED
}

/**
 * Represents a reminder for an event
 */
data class Reminder(
    val trigger: ReminderTrigger,
    val action: ReminderAction = ReminderAction.DISPLAY
)

sealed class ReminderTrigger {
    data class BeforeStart(val minutes: Int) : ReminderTrigger()
    data class AtTime(val time: Instant) : ReminderTrigger()
}

enum class ReminderAction {
    DISPLAY,
    EMAIL,
    AUDIO
}

/**
 * Represents a calendar
 */
data class Calendar(
    val id: String,
    val accountId: String,
    val displayName: String,
    val color: Int,
    val url: String, // CalDAV URL
    val ctag: String? = null, // Change tag for sync
    val syncToken: String? = null,
    val readOnly: Boolean = false,
    val visible: Boolean = true,
    val order: Int = 0
)

/**
 * Represents a Nextcloud account
 */
data class Account(
    val id: String,
    val serverUrl: String,
    val username: String,
    val displayName: String? = null,
    val email: String? = null,
    val principalUrl: String? = null,
    val calendarHomeSet: String? = null,
    val isDefault: Boolean = false
)

/**
 * Sync status for offline support
 */
enum class SyncStatus {
    SYNCED,        // In sync with server
    PENDING_CREATE, // Created offline, needs upload
    PENDING_UPDATE, // Modified offline, needs upload
    PENDING_DELETE, // Deleted offline, needs server delete
    CONFLICT        // Server version differs
}
