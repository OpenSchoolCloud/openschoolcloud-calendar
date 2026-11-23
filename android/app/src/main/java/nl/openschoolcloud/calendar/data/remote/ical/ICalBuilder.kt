/*
 * OSC Calendar - Privacy-first calendar for Dutch education
 * Copyright (C) 2025 Aldewereld Consultancy (OpenSchoolCloud)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 */
package nl.openschoolcloud.calendar.data.remote.ical

import nl.openschoolcloud.calendar.domain.model.AttendeeRole
import nl.openschoolcloud.calendar.domain.model.AttendeeStatus
import nl.openschoolcloud.calendar.domain.model.Event
import nl.openschoolcloud.calendar.domain.model.EventStatus
import nl.openschoolcloud.calendar.domain.model.ReminderAction
import nl.openschoolcloud.calendar.domain.model.ReminderTrigger
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds iCalendar (RFC 5545) VCALENDAR strings from Event domain objects.
 * Uses string templates for reliability (no dependency on ical4j builder API).
 */
@Singleton
class ICalBuilder @Inject constructor() {

    private val utcFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
        .withZone(ZoneOffset.UTC)

    /**
     * Build a complete VCALENDAR string for an event.
     */
    fun buildVCalendar(
        event: Event,
        organizerEmail: String? = null,
        organizerName: String? = null
    ): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("PRODID:-//OSC Calendar//OSC Calendar 1.0//EN")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("CALSCALE:GREGORIAN")

        if (event.attendees.isNotEmpty()) {
            sb.appendLine("METHOD:REQUEST")
        }

        appendVEvent(sb, event, organizerEmail, organizerName)

        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun appendVEvent(
        sb: StringBuilder,
        event: Event,
        organizerEmail: String?,
        organizerName: String?
    ) {
        sb.appendLine("BEGIN:VEVENT")

        // UID (required)
        sb.appendLine("UID:${event.uid}")

        // DTSTAMP (required)
        sb.appendLine("DTSTAMP:${formatUtc(Instant.now())}")

        // DTSTART
        if (event.allDay) {
            val dateStr = event.dtStart.atZone(event.timeZone).toLocalDate()
                .format(DateTimeFormatter.BASIC_ISO_DATE)
            sb.appendLine("DTSTART;VALUE=DATE:$dateStr")
        } else {
            sb.appendLine("DTSTART:${formatUtc(event.dtStart)}")
        }

        // DTEND
        event.dtEnd?.let { endInstant ->
            if (event.allDay) {
                val dateStr = endInstant.atZone(event.timeZone).toLocalDate()
                    .format(DateTimeFormatter.BASIC_ISO_DATE)
                sb.appendLine("DTEND;VALUE=DATE:$dateStr")
            } else {
                sb.appendLine("DTEND:${formatUtc(endInstant)}")
            }
        }

        // SUMMARY
        sb.appendLine("SUMMARY:${escapeIcal(event.summary)}")

        // DESCRIPTION
        event.description?.let {
            sb.appendLine("DESCRIPTION:${escapeIcal(it)}")
        }

        // LOCATION
        event.location?.let {
            sb.appendLine("LOCATION:${escapeIcal(it)}")
        }

        // STATUS
        when (event.status) {
            EventStatus.TENTATIVE -> sb.appendLine("STATUS:TENTATIVE")
            EventStatus.CANCELLED -> sb.appendLine("STATUS:CANCELLED")
            else -> sb.appendLine("STATUS:CONFIRMED")
        }

        // LAST-MODIFIED
        event.lastModified?.let {
            sb.appendLine("LAST-MODIFIED:${formatUtc(it)}")
        }

        // RRULE
        event.rrule?.let {
            sb.appendLine("RRULE:$it")
        }

        // ORGANIZER (when attendees present)
        if (event.attendees.isNotEmpty() && organizerEmail != null) {
            val cnParam = organizerName?.let { ";CN=${escapeParam(it)}" } ?: ""
            sb.appendLine("ORGANIZER${cnParam}:mailto:$organizerEmail")
        }

        // ATTENDEES
        for (attendee in event.attendees) {
            val params = buildList {
                attendee.name?.let { add("CN=${escapeParam(it)}") }
                add("ROLE=${mapRole(attendee.role)}")
                add("PARTSTAT=${mapPartStat(attendee.status)}")
                add("RSVP=TRUE")
            }
            val paramStr = if (params.isNotEmpty()) ";" + params.joinToString(";") else ""
            sb.appendLine("ATTENDEE${paramStr}:mailto:${attendee.email}")
        }

        // VALARM (reminders)
        for (reminder in event.reminders) {
            sb.appendLine("BEGIN:VALARM")
            when (val trigger = reminder.trigger) {
                is ReminderTrigger.BeforeStart -> {
                    sb.appendLine("TRIGGER:${minutesToDuration(trigger.minutes)}")
                }
                is ReminderTrigger.AtTime -> {
                    sb.appendLine("TRIGGER:PT0S")
                }
            }
            sb.appendLine("ACTION:${reminder.action.name}")
            if (reminder.action == ReminderAction.DISPLAY || reminder.action == ReminderAction.EMAIL) {
                sb.appendLine("DESCRIPTION:Reminder: ${escapeIcal(event.summary)}")
            }
            sb.appendLine("END:VALARM")
        }

        // X-OSC custom properties
        if (event.eventType != "STANDARD") {
            sb.appendLine("X-OSC-EVENT-TYPE:${event.eventType}")
        }
        if (event.isLearningAgenda) {
            sb.appendLine("X-OSC-LEARNING-AGENDA:TRUE")
            event.learningGoal?.let {
                sb.appendLine("X-OSC-LEARNING-GOAL:${escapeIcal(it)}")
            }
            event.learningNeeds?.let {
                sb.appendLine("X-OSC-LEARNING-NEEDS:${escapeIcal(it)}")
            }
        }

        sb.appendLine("END:VEVENT")
    }

    private fun formatUtc(instant: Instant): String = utcFormatter.format(instant)

    private fun escapeIcal(text: String): String {
        return text
            .replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace("\n", "\\n")
    }

    private fun escapeParam(text: String): String {
        return if (text.contains(";") || text.contains(":") || text.contains(",")) {
            "\"$text\""
        } else {
            text
        }
    }

    private fun mapRole(role: AttendeeRole): String = when (role) {
        AttendeeRole.CHAIR -> "CHAIR"
        AttendeeRole.REQ_PARTICIPANT -> "REQ-PARTICIPANT"
        AttendeeRole.OPT_PARTICIPANT -> "OPT-PARTICIPANT"
        AttendeeRole.NON_PARTICIPANT -> "NON-PARTICIPANT"
    }

    private fun mapPartStat(status: AttendeeStatus): String = when (status) {
        AttendeeStatus.NEEDS_ACTION -> "NEEDS-ACTION"
        AttendeeStatus.ACCEPTED -> "ACCEPTED"
        AttendeeStatus.DECLINED -> "DECLINED"
        AttendeeStatus.TENTATIVE -> "TENTATIVE"
    }

    private fun minutesToDuration(minutes: Int): String = when {
        minutes >= 1440 && minutes % 1440 == 0 -> "-P${minutes / 1440}D"
        minutes >= 60 && minutes % 60 == 0 -> "-PT${minutes / 60}H"
        else -> "-PT${minutes}M"
    }
}
