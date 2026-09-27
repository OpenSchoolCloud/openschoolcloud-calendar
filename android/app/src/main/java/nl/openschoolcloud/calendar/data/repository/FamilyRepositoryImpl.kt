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
package nl.openschoolcloud.calendar.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.openschoolcloud.calendar.data.local.dao.FamilyDao
import nl.openschoolcloud.calendar.data.local.entity.FamilyEntity
import nl.openschoolcloud.calendar.domain.model.Family
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class FamilyRepositoryImpl @Inject constructor(
    private val familyDao: FamilyDao
) : FamilyRepository {

    override fun observeFamily(): Flow<Family?> =
        familyDao.observe().map { it?.toDomain() }

    override suspend fun createFamily(name: String, ownerAccountId: String): Family {
        val entity = FamilyEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            ownerAccountId = ownerAccountId,
            createdAt = Instant.now().toEpochMilli()
        )
        familyDao.insert(entity)
        return entity.toDomain()
    }

    private fun FamilyEntity.toDomain() =
        Family(id, name, ownerAccountId, Instant.ofEpochMilli(createdAt))
}
