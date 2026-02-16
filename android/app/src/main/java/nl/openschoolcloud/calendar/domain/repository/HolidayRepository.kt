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
package nl.openschoolcloud.calendar.domain.repository

import kotlinx.coroutines.flow.Flow
import nl.openschoolcloud.calendar.domain.model.HolidayCalendar
import nl.openschoolcloud.calendar.domain.model.HolidayEvent
import java.time.LocalDate

interface HolidayRepository {
    fun getHolidayCalendars(): Flow<List<HolidayCalendar>>
    fun getEnabledCalendars(): Flow<List<HolidayCalendar>>
    fun getHolidayEvents(start: LocalDate, end: LocalDate): Flow<List<HolidayEvent>>
    suspend fun getHolidayEvent(id: String): HolidayEvent?
    suspend fun setCalendarEnabled(id: String, enabled: Boolean)
    suspend fun seedIfNeeded()
}
