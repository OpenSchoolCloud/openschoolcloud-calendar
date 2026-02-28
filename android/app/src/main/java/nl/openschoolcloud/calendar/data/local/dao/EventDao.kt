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
package nl.openschoolcloud.calendar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import nl.openschoolcloud.calendar.data.local.entity.EventEntity

/**
 * Data Access Object for event operations
 */
@Dao
interface EventDao {

    @Query("""
        SELECT * FROM events
        WHERE dtStart < :endMillis AND (dtEnd > :startMillis OR dtEnd IS NULL)
        AND calendarId IN (SELECT id FROM calendars WHERE visible = 1)
        ORDER BY dtStart
    """)
    fun getInRange(startMillis: Long, endMillis: Long): Flow<List<EventEntity>>

    @Query("""
        SELECT * FROM events
        WHERE dtStart < :endMillis AND (dtEnd > :startMillis OR dtEnd IS NULL)
        AND calendarId IN (SELECT id FROM calendars WHERE visible = 1)
        ORDER BY dtStart
    """)
    fun getInRangeSync(startMillis: Long, endMillis: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE uid = :uid")
    suspend fun getByUid(uid: String): EventEntity?

    @Query("SELECT * FROM events WHERE calendarId = :calendarId")
    fun getByCalendar(calendarId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE calendarId = :calendarId")
    suspend fun getByCalendarSync(calendarId: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE syncStatus != 'SYNCED'")
    suspend fun getPending(): List<EventEntity>

    @Query("""
        SELECT * FROM events
        WHERE summary LIKE '%' || :query || '%'
           OR location LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
        ORDER BY dtStart DESC
        LIMIT 50
    """)
    fun search(query: String): Flow<List<EventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Update
    suspend fun update(event: EventEntity)

    @Query("DELETE FROM events WHERE uid = :uid")
    suspend fun deleteByUid(uid: String)

    @Query("DELETE FROM events WHERE calendarId = :calendarId")
    suspend fun deleteByCalendar(calendarId: String)

    @Query("""
        SELECT * FROM events
        WHERE eventType = 'TASK'
        AND dtStart >= :startMillis AND dtStart < :endMillis
        ORDER BY taskCompleted ASC, dtStart ASC
    """)
    fun getTasksInRange(startMillis: Long, endMillis: Long): Flow<List<EventEntity>>

    @Query("""
        SELECT COUNT(*) FROM events
        WHERE eventType = 'TASK'
        AND taskCompleted = 1
        AND dtStart >= :startMillis AND dtStart < :endMillis
    """)
    fun getCompletedTaskCountInRange(startMillis: Long, endMillis: Long): Flow<Int>

    @Query("""
        SELECT COUNT(*) FROM events
        WHERE eventType = 'TASK'
        AND dtStart >= :startMillis AND dtStart < :endMillis
    """)
    fun getTaskCountInRange(startMillis: Long, endMillis: Long): Flow<Int>
}
