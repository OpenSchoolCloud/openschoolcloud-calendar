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
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val isLearningAgenda: Boolean = false,
    val learningGoal: String? = null,    // "Wat ga ik doen?"
    val learningNeeds: String? = null    // "Wat heb ik nodig?"
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
 * Represents a Nextcloud Appointments booking configuration.
 * Teachers can create these for parent-teacher conferences (10-minutengesprekken).
 */
data class BookingConfig(
    val id: Long,
    val name: String,
    val description: String?,
    val duration: Int, // minutes
    val token: String,
    val bookingUrl: String,
    val calendarId: String?,
    val visibility: BookingVisibility = BookingVisibility.PUBLIC
)

enum class BookingVisibility {
    PUBLIC,
    PRIVATE
}

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
