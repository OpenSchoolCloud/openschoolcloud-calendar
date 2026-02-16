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
import nl.openschoolcloud.calendar.domain.model.ReflectionEntry
import java.time.Instant

interface ReflectionRepository {
    suspend fun saveReflection(reflection: ReflectionEntry)
    suspend fun getReflectionForEvent(eventId: String): ReflectionEntry?
    fun observeReflectionForEvent(eventId: String): Flow<ReflectionEntry?>
    fun getReflectionsForWeek(start: Instant, end: Instant): Flow<List<ReflectionEntry>>
    suspend fun hasReflection(eventId: String): Boolean
    suspend fun deleteReflection(eventId: String)
}
