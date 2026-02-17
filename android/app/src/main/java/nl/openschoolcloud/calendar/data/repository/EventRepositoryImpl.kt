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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.openschoolcloud.calendar.data.local.dao.CalendarDao
import nl.openschoolcloud.calendar.data.local.dao.EventDao
import nl.openschoolcloud.calendar.data.local.entity.EventEntity
import nl.openschoolcloud.calendar.data.remote.ical.AttendeeJson
import nl.openschoolcloud.calendar.data.remote.ical.JsonSerializer
import nl.openschoolcloud.calendar.data.remote.ical.ReminderJson
import nl.openschoolcloud.calendar.domain.model.Attendee
import nl.openschoolcloud.calendar.domain.model.AttendeeRole
import nl.openschoolcloud.calendar.domain.model.AttendeeStatus
import nl.openschoolcloud.calendar.domain.model.Event
import nl.openschoolcloud.calendar.domain.model.EventStatus
import nl.openschoolcloud.calendar.domain.model.Reminder
import nl.openschoolcloud.calendar.domain.model.ReminderAction
import nl.openschoolcloud.calendar.domain.model.ReminderTrigger
import nl.openschoolcloud.calendar.domain.model.SyncStatus
import nl.openschoolcloud.calendar.domain.repository.EventRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of EventRepository
 *
 * Handles event CRUD operations and sync with CalDAV server
 */
@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao,
    private val calendarDao: CalendarDao
) : EventRepository {

    override fun getEvents(
        start: Instant,
        end: Instant,
        calendarIds: List<String>?
    ): Flow<List<Event>> {
        return eventDao.getInRange(
            startMillis = start.toEpochMilli(),
            endMillis = end.toEpochMilli()
        ).map { entities ->
            entities
                .filter { calendarIds == null || it.calendarId in calendarIds }
                .map { it.toDomain() }
        }
    }

    override suspend fun getEvent(eventId: String): Event? {
        return eventDao.getByUid(eventId)?.toDomain()
    }

    override suspend fun createEvent(event: Event, sendInvites: Boolean): Result<Event> {
        // TODO: Implement in Sprint 2 - create event and sync to server
        return try {
            val entity = event.toEntity()
            eventDao.insert(entity)
            Result.success(event)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateEvent(event: Event, notifyAttendees: Boolean): Result<Event> {
        // TODO: Implement in Sprint 2 - update event and sync to server
        return try {
            val entity = event.toEntity()
            eventDao.update(entity)
            Result.success(event)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteEvent(eventId: String, notifyAttendees: Boolean): Result<Unit> {
        // TODO: Implement in Sprint 2 - delete event and sync to server
        return try {
            eventDao.deleteByUid(eventId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun searchEvents(query: String): Flow<List<Event>> {
        return eventDao.search(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPendingEvents(): List<Event> {
        return eventDao.getPending().map { it.toDomain() }
    }

    /**
     * Get events for a specific day
     */
    fun getEventsForDay(date: LocalDate): Flow<List<Event>> {
        val startOfDay = date.atStartOfDay(ZoneId.systemDefault()).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        return getEvents(startOfDay, endOfDay, null)
    }

    override fun getTasks(start: Instant, end: Instant): Flow<List<Event>> {
        return eventDao.getTasksInRange(
            startMillis = start.toEpochMilli(),
            endMillis = end.toEpochMilli()
        ).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun toggleTaskCompleted(eventId: String): Result<Event> {
        return try {
            val entity = eventDao.getByUid(eventId)
                ?: return Result.failure(IllegalArgumentException("Event not found: $eventId"))
            val updated = entity.copy(
                taskCompleted = !entity.taskCompleted,
                syncStatus = "PENDING_UPDATE",
                lastModified = Instant.now().toEpochMilli()
            )
            eventDao.update(updated)
            Result.success(updated.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get events for a specific calendar
     */
    fun getEventsForCalendar(calendarId: String): Flow<List<Event>> {
        return eventDao.getByCalendar(calendarId).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}

/**
 * Extension function to convert EventEntity to domain Event
 */
private fun EventEntity.toDomain(): Event {
    // Parse organizer from stored email/name
    val organizer = organizerEmail?.let { email ->
        Attendee(
            email = email,
            name = organizerName,
            role = AttendeeRole.CHAIR,
            status = AttendeeStatus.ACCEPTED
        )
    }

    // Parse attendees from JSON
    val attendees = JsonSerializer.deserializeAttendees(attendeesJson).mapNotNull { json ->
        json.email?.let { email ->
            Attendee(
                email = email,
                name = json.name,
                role = parseAttendeeRole(json.role),
                status = parseAttendeeStatus(json.status)
            )
        }
    }

    // Parse reminders from JSON
    val reminders = JsonSerializer.deserializeReminders(remindersJson).map { json ->
        val minutes = JsonSerializer.parseTriggerToMinutes(json.trigger)
        Reminder(
            trigger = ReminderTrigger.BeforeStart(minutes),
            action = parseReminderAction(json.action)
        )
    }

    return Event(
        uid = uid,
        calendarId = calendarId,
        summary = summary,
        description = description,
        location = location,
        dtStart = Instant.ofEpochMilli(dtStart),
        dtEnd = dtEnd?.let { Instant.ofEpochMilli(it) },
        allDay = allDay,
        timeZone = try { ZoneId.of(timeZone) } catch (e: Exception) { ZoneId.systemDefault() },
        rrule = rrule,
        color = colorOverride,
        organizer = organizer,
        attendees = attendees,
        reminders = reminders,
        status = try { EventStatus.valueOf(status) } catch (e: Exception) { EventStatus.CONFIRMED },
        created = created?.let { Instant.ofEpochMilli(it) },
        lastModified = lastModified?.let { Instant.ofEpochMilli(it) },
        etag = etag,
        syncStatus = try { SyncStatus.valueOf(syncStatus) } catch (e: Exception) { SyncStatus.SYNCED },
        isLearningAgenda = isLearningAgenda,
        learningGoal = learningGoal,
        learningNeeds = learningNeeds,
        eventType = eventType,
        taskCompleted = taskCompleted
    )
}

/**
 * Extension function to convert domain Event to EventEntity
 */
private fun Event.toEntity(): EventEntity {
    // Serialize attendees to JSON
    val attendeesJson = if (attendees.isNotEmpty()) {
        JsonSerializer.serializeAttendees(attendees.map { attendee ->
            AttendeeJson(
                email = attendee.email,
                name = attendee.name,
                status = attendee.status.name,
                role = attendee.role.name
            )
        })
    } else null

    // Serialize reminders to JSON
    val remindersJson = if (reminders.isNotEmpty()) {
        JsonSerializer.serializeReminders(reminders.map { reminder ->
            val trigger = when (val t = reminder.trigger) {
                is ReminderTrigger.BeforeStart -> JsonSerializer.minutesToTrigger(t.minutes)
                is ReminderTrigger.AtTime -> "-PT0M" // Absolute times stored as 0 offset
            }
            ReminderJson(
                trigger = trigger,
                action = reminder.action.name
            )
        })
    } else null

    return EventEntity(
        uid = uid,
        calendarId = calendarId,
        summary = summary,
        description = description,
        location = location,
        dtStart = dtStart.toEpochMilli(),
        dtEnd = dtEnd?.toEpochMilli(),
        allDay = allDay,
        timeZone = timeZone.id,
        rrule = rrule,
        colorOverride = color,
        organizerEmail = organizer?.email,
        organizerName = organizer?.name,
        attendeesJson = attendeesJson,
        remindersJson = remindersJson,
        status = status.name,
        created = created?.toEpochMilli(),
        lastModified = lastModified?.toEpochMilli(),
        etag = etag,
        syncStatus = syncStatus.name,
        rawIcal = null,
        isLearningAgenda = isLearningAgenda,
        learningGoal = learningGoal,
        learningNeeds = learningNeeds,
        eventType = eventType,
        taskCompleted = taskCompleted
    )
}

/**
 * Parse attendee role from iCal string
 */
private fun parseAttendeeRole(role: String?): AttendeeRole {
    return when (role?.uppercase()) {
        "CHAIR" -> AttendeeRole.CHAIR
        "OPT-PARTICIPANT" -> AttendeeRole.OPT_PARTICIPANT
        "NON-PARTICIPANT" -> AttendeeRole.NON_PARTICIPANT
        else -> AttendeeRole.REQ_PARTICIPANT
    }
}

/**
 * Parse attendee status from iCal string
 */
private fun parseAttendeeStatus(status: String?): AttendeeStatus {
    return when (status?.uppercase()) {
        "ACCEPTED" -> AttendeeStatus.ACCEPTED
        "DECLINED" -> AttendeeStatus.DECLINED
        "TENTATIVE" -> AttendeeStatus.TENTATIVE
        else -> AttendeeStatus.NEEDS_ACTION
    }
}

/**
 * Parse reminder action from iCal string
 */
private fun parseReminderAction(action: String?): ReminderAction {
    return when (action?.uppercase()) {
        "EMAIL" -> ReminderAction.EMAIL
        "AUDIO" -> ReminderAction.AUDIO
        else -> ReminderAction.DISPLAY
    }
}
