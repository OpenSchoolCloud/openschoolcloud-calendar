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
import nl.openschoolcloud.calendar.data.local.entity.ReflectionEntity

@Dao
interface ReflectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReflectionEntity)

    @Query("SELECT * FROM reflection_entries WHERE eventId = :eventId LIMIT 1")
    suspend fun getByEventId(eventId: String): ReflectionEntity?

    @Query("SELECT * FROM reflection_entries WHERE eventId = :eventId LIMIT 1")
    fun observeByEventId(eventId: String): Flow<ReflectionEntity?>

    @Query("SELECT * FROM reflection_entries WHERE createdAt >= :startMillis AND createdAt < :endMillis ORDER BY createdAt ASC")
    fun getInRange(startMillis: Long, endMillis: Long): Flow<List<ReflectionEntity>>

    @Query("DELETE FROM reflection_entries WHERE eventId = :eventId")
    suspend fun deleteByEventId(eventId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM reflection_entries WHERE eventId = :eventId)")
    suspend fun hasReflection(eventId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM reflection_entries WHERE eventId = :eventId)")
    fun hasReflectionSync(eventId: String): Boolean
}
