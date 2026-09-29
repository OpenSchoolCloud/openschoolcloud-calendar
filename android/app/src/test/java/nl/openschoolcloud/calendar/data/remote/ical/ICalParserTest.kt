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
package nl.openschoolcloud.calendar.data.remote.ical

import nl.openschoolcloud.calendar.domain.model.EventStatus
import nl.openschoolcloud.calendar.domain.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for ICalParser
 */
class ICalParserTest {

    private lateinit var parser: ICalParser

    @Before
    fun setup() {
        parser = ICalParser()
    }

    // ==================== parseEvent tests ====================

    @Test
    fun `parseEvent parses basic event with required fields`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:test-event-123@example.com
            SUMMARY:Team Meeting
            DTSTART:20240115T100000Z
            DTEND:20240115T110000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", "etag-123")

        assertNotNull(result)
        assertEquals("test-event-123@example.com", result!!.uid)
        assertEquals("Team Meeting", result.summary)
        assertEquals("calendar-1", result.calendarId)
        assertEquals("etag-123", result.etag)
        assertEquals(SyncStatus.SYNCED.name, result.syncStatus)
    }

    @Test
    fun `parseEvent parses event with all optional fields`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:full-event-456@example.com
            SUMMARY:Project Review
            DESCRIPTION:Quarterly project status review meeting
            LOCATION:Conference Room A
            DTSTART:20240120T140000Z
            DTEND:20240120T150000Z
            STATUS:CONFIRMED
            ORGANIZER;CN=John Doe:mailto:john@example.com
            ATTENDEE;CN=Jane Smith;PARTSTAT=ACCEPTED:mailto:jane@example.com
            ATTENDEE;CN=Bob Wilson;PARTSTAT=TENTATIVE:mailto:bob@example.com
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            DESCRIPTION:Event reminder
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", "etag-456")

        assertNotNull(result)
        assertEquals("full-event-456@example.com", result!!.uid)
        assertEquals("Project Review", result.summary)
        assertEquals("Quarterly project status review meeting", result.description)
        assertEquals("Conference Room A", result.location)
        assertEquals("john@example.com", result.organizerEmail)
        assertEquals("John Doe", result.organizerName)
        assertNotNull(result.attendeesJson)
        assertNotNull(result.remindersJson)
    }

    @Test
    fun `parseEvent detects all-day event`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:allday-event@example.com
            SUMMARY:Holiday
            DTSTART;VALUE=DATE:20240101
            DTEND;VALUE=DATE:20240102
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", null)

        assertNotNull(result)
        assertTrue(result!!.allDay)
    }

    @Test
    fun `parseEvent parses event with recurrence rule`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:recurring-event@example.com
            SUMMARY:Weekly Team Standup
            DTSTART:20240108T090000Z
            DTEND:20240108T091500Z
            RRULE:FREQ=WEEKLY;BYDAY=MO,WE,FR
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", null)

        assertNotNull(result)
        assertEquals("FREQ=WEEKLY;BYDAY=MO,WE,FR", result!!.rrule)
    }

    @Test
    fun `parseEvent handles different statuses`() {
        val tentativeIcal = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:tentative@example.com
            SUMMARY:Maybe Meeting
            DTSTART:20240115T100000Z
            STATUS:TENTATIVE
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val cancelledIcal = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:cancelled@example.com
            SUMMARY:Cancelled Meeting
            DTSTART:20240115T100000Z
            STATUS:CANCELLED
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val tentativeResult = parser.parseEvent(tentativeIcal, "cal", null)
        val cancelledResult = parser.parseEvent(cancelledIcal, "cal", null)

        assertEquals(EventStatus.TENTATIVE.name, tentativeResult!!.status)
        assertEquals(EventStatus.CANCELLED.name, cancelledResult!!.status)
    }

    @Test
    fun `parseEvent returns null for invalid ical`() {
        val invalidIcal = "This is not valid iCal data"

        val result = parser.parseEvent(invalidIcal, "calendar-1", null)

        assertNull(result)
    }

    @Test
    fun `parseEvent returns null for missing UID`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            SUMMARY:No UID Event
            DTSTART:20240115T100000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", null)

        assertNull(result)
    }

    @Test
    fun `parseEvent returns null for missing start date`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:no-start@example.com
            SUMMARY:No Start Date
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", null)

        assertNull(result)
    }

    @Test
    fun `parseEvent uses default summary for missing summary`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:no-summary@example.com
            DTSTART:20240115T100000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", null)

        assertNotNull(result)
        assertEquals("Untitled Event", result!!.summary)
    }

    // ==================== parseEvents tests ====================

    @Test
    fun `parseEvents parses multiple events from single ical`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:event-1@example.com
            SUMMARY:First Event
            DTSTART:20240115T100000Z
            END:VEVENT
            BEGIN:VEVENT
            UID:event-2@example.com
            SUMMARY:Second Event
            DTSTART:20240115T140000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvents(icalData, "calendar-1", "etag")

        assertEquals(2, result.size)
        assertEquals("event-1@example.com", result[0].uid)
        assertEquals("event-2@example.com", result[1].uid)
    }

    @Test
    fun `parseEvents returns empty list for invalid ical`() {
        val result = parser.parseEvents("invalid data", "calendar-1", null)

        assertTrue(result.isEmpty())
    }

    // ==================== Attendee parsing tests ====================

    @Test
    fun `parseEvent correctly parses multiple attendees`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:attendees-test@example.com
            SUMMARY:Meeting with Attendees
            DTSTART:20240115T100000Z
            ATTENDEE;CN=Alice;PARTSTAT=ACCEPTED;ROLE=REQ-PARTICIPANT:mailto:alice@example.com
            ATTENDEE;CN=Bob;PARTSTAT=DECLINED;ROLE=OPT-PARTICIPANT:mailto:bob@example.com
            ATTENDEE;CN=Charlie;PARTSTAT=TENTATIVE:mailto:charlie@example.com
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", null)

        assertNotNull(result)
        assertNotNull(result!!.attendeesJson)

        val attendees = JsonSerializer.deserializeAttendees(result.attendeesJson)
        assertEquals(3, attendees.size)
        assertEquals("alice@example.com", attendees[0].email)
        assertEquals("Alice", attendees[0].name)
    }

    // ==================== Alarm/Reminder parsing tests ====================

    @Test
    fun `parseEvent correctly parses multiple alarms`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:alarms-test@example.com
            SUMMARY:Event with Alarms
            DTSTART:20240115T100000Z
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            END:VALARM
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT1H
            END:VALARM
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", null)

        assertNotNull(result)
        assertNotNull(result!!.remindersJson)

        val reminders = JsonSerializer.deserializeReminders(result.remindersJson)
        assertEquals(2, reminders.size)
    }

    // ==================== Edge cases ====================

    @Test
    fun `parseEvent handles event without end time`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:no-end@example.com
            SUMMARY:Open-ended Event
            DTSTART:20240115T100000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", null)

        assertNotNull(result)
        // RFC 5545: a date-time DTSTART with no DTEND and no DURATION has
        // zero duration, i.e. it ends at the same instant it starts.
        assertEquals(result!!.dtStart, result.dtEnd)
    }

    @Test
    fun `parseEvent preserves raw ical data`() {
        val icalData = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//Test//Test//EN
            BEGIN:VEVENT
            UID:raw-test@example.com
            SUMMARY:Test
            DTSTART:20240115T100000Z
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = parser.parseEvent(icalData, "calendar-1", null)

        assertNotNull(result)
        assertEquals(icalData, result!!.rawIcal)
    }
}
