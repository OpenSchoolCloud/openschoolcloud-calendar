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
package nl.openschoolcloud.calendar.domain.model

import java.time.LocalDate

/**
 * Categories of holiday calendars with associated display colors (ARGB).
 */
enum class HolidayCategory(val colorArgb: Int) {
    DUTCH_NATIONAL(0xFFFF6D00.toInt()),
    ISLAMIC(0xFF2E7D32.toInt()),
    CHRISTIAN(0xFF7B1FA2.toInt()),
    JEWISH(0xFF1565C0.toInt()),
    HINDU(0xFFE65100.toInt()),
    CHINESE_ASIAN(0xFFC62828.toInt()),
    INTERNATIONAL(0xFF00838F.toInt())
}

/**
 * How a holiday date is calculated each year.
 */
enum class DateCalculationType {
    FIXED,
    EASTER_BASED,
    PRECOMPUTED
}

/**
 * A subscribable holiday calendar (e.g. "Nederlandse feestdagen").
 */
data class HolidayCalendar(
    val id: String,
    val category: HolidayCategory,
    val displayName: String,
    val description: String,
    val enabled: Boolean
)

/**
 * A single holiday event within a calendar.
 */
data class HolidayEvent(
    val id: String,
    val calendarId: String,
    val category: HolidayCategory,
    val title: String,
    val description: String,
    val classroomTip: String?,
    val date: LocalDate,
    val endDate: LocalDate?,
    val dateCalculationType: DateCalculationType,
    val easterOffset: Int?,
    val fixedMonth: Int?,
    val fixedDay: Int?
)
