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
package nl.openschoolcloud.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing calendar events
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("calendarId"),
        Index("dtStart"),
        Index("dtEnd")
    ]
)
data class EventEntity(
    @PrimaryKey
    val uid: String,
    val calendarId: String,
    val summary: String,
    val description: String?,
    val location: String?,
    val dtStart: Long, // epoch millis
    val dtEnd: Long?,
    val allDay: Boolean,
    val timeZone: String,
    val rrule: String?,
    val colorOverride: Int?,
    val organizerEmail: String?,
    val organizerName: String?,
    val attendeesJson: String?, // JSON array of attendees
    val remindersJson: String?, // JSON array of reminders
    val status: String, // CONFIRMED, TENTATIVE, CANCELLED
    val created: Long?,
    val lastModified: Long?,
    val etag: String?,
    val syncStatus: String, // SYNCED, PENDING_CREATE, PENDING_UPDATE, PENDING_DELETE
    val rawIcal: String?, // Original iCal data for conflict resolution
    val isLearningAgenda: Boolean = false,
    val learningGoal: String? = null,
    val learningNeeds: String? = null,
    val eventType: String = "STANDARD",
    val taskCompleted: Boolean = false
)
