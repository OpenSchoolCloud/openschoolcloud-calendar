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
package nl.openschoolcloud.calendar.data.local

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class HolidayCalendarJson(
    val calendarId: String,
    val category: String,
    val displayName: String,
    val description: String,
    val events: List<HolidayEventJson>
)

@JsonClass(generateAdapter = true)
data class HolidayEventJson(
    val titleKey: String,
    val title: String,
    val description: String,
    val classroomTip: String?,
    val dateCalculationType: String,
    val fixedMonth: Int?,
    val fixedDay: Int?,
    val easterOffset: Int?,
    val precomputedDates: Map<String, String>?
)
