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
package nl.openschoolcloud.calendar.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.openschoolcloud.calendar.data.local.dao.ReflectionDao
import nl.openschoolcloud.calendar.data.local.entity.ReflectionEntity
import nl.openschoolcloud.calendar.domain.model.ReflectionEntry
import nl.openschoolcloud.calendar.domain.repository.ReflectionRepository
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReflectionRepositoryImpl @Inject constructor(
    private val reflectionDao: ReflectionDao
) : ReflectionRepository {

    override suspend fun saveReflection(reflection: ReflectionEntry) {
        reflectionDao.insert(reflection.toEntity())
    }

    override suspend fun getReflectionForEvent(eventId: String): ReflectionEntry? {
        return reflectionDao.getByEventId(eventId)?.toDomain()
    }

    override fun observeReflectionForEvent(eventId: String): Flow<ReflectionEntry?> {
        return reflectionDao.observeByEventId(eventId).map { it?.toDomain() }
    }

    override fun getReflectionsForWeek(start: Instant, end: Instant): Flow<List<ReflectionEntry>> {
        return reflectionDao.getInRange(start.toEpochMilli(), end.toEpochMilli())
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun hasReflection(eventId: String): Boolean {
        return reflectionDao.hasReflection(eventId)
    }

    override suspend fun deleteReflection(eventId: String) {
        reflectionDao.deleteByEventId(eventId)
    }
}

private fun ReflectionEntity.toDomain(): ReflectionEntry {
    return ReflectionEntry(
        id = id,
        eventId = eventId,
        mood = mood,
        whatWentWell = whatWentWell,
        whatToDoBetter = whatToDoBetter,
        createdAt = Instant.ofEpochMilli(createdAt)
    )
}

private fun ReflectionEntry.toEntity(): ReflectionEntity {
    return ReflectionEntity(
        id = id,
        eventId = eventId,
        mood = mood,
        whatWentWell = whatWentWell,
        whatToDoBetter = whatToDoBetter,
        createdAt = createdAt.toEpochMilli()
    )
}
