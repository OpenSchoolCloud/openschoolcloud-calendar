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
package nl.openschoolcloud.calendar.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "holiday_events",
    foreignKeys = [
        ForeignKey(
            entity = HolidayCalendarEntity::class,
            parentColumns = ["id"],
            childColumns = ["calendarId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("calendarId"),
        Index("date"),
        Index("endDate")
    ]
)
data class HolidayEventEntity(
    @PrimaryKey
    val id: String,
    val calendarId: String,
    val category: String,
    val title: String,
    val description: String,
    val classroomTip: String?,
    val date: Long,       // epoch millis (start of day UTC)
    val endDate: Long?,   // epoch millis (start of day UTC)
    val dateCalculationType: String,
    val easterOffset: Int?,
    val fixedMonth: Int?,
    val fixedDay: Int?
)
