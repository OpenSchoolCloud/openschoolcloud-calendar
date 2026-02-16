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
package nl.openschoolcloud.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import nl.openschoolcloud.calendar.data.local.entity.HolidayCalendarEntity
import nl.openschoolcloud.calendar.data.local.entity.HolidayEventEntity

@Dao
interface HolidayDao {

    @Query("SELECT * FROM holiday_calendars ORDER BY category")
    fun getAllCalendars(): Flow<List<HolidayCalendarEntity>>

    @Query("SELECT * FROM holiday_calendars WHERE enabled = 1 ORDER BY category")
    fun getEnabledCalendars(): Flow<List<HolidayCalendarEntity>>

    @Query("UPDATE holiday_calendars SET enabled = :enabled WHERE id = :id")
    suspend fun setCalendarEnabled(id: String, enabled: Boolean)

    @Query("""
        SELECT * FROM holiday_events
        WHERE date >= :startMillis AND date <= :endMillis
        AND calendarId IN (SELECT id FROM holiday_calendars WHERE enabled = 1)
        ORDER BY date
    """)
    fun getEventsInRange(startMillis: Long, endMillis: Long): Flow<List<HolidayEventEntity>>

    @Query("SELECT * FROM holiday_events WHERE id = :id")
    suspend fun getEventById(id: String): HolidayEventEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendars(calendars: List<HolidayCalendarEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<HolidayEventEntity>)

    @Query("DELETE FROM holiday_events")
    suspend fun deleteAllEvents()

    @Query("SELECT COUNT(*) FROM holiday_events")
    suspend fun getEventCount(): Int
}
