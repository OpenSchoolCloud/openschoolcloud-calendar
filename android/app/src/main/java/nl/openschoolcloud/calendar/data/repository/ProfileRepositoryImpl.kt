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
import nl.openschoolcloud.calendar.data.local.dao.ProfileDao
import nl.openschoolcloud.calendar.data.local.entity.ProfileEntity
import nl.openschoolcloud.calendar.domain.model.Profile
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import java.util.UUID
import javax.inject.Inject

class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao
) : ProfileRepository {

    override fun observeProfiles(familyId: String): Flow<List<Profile>> =
        profileDao.observeByFamily(familyId).map { list -> list.map { it.toDomain() } }

    override suspend fun observeProfilesOnce(familyId: String): List<Profile> =
        profileDao.getByFamilySync(familyId).map { it.toDomain() }

    override suspend fun addProfile(
        familyId: String, name: String, color: Long, isOwner: Boolean
    ): Profile {
        val entity = ProfileEntity(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            displayName = name,
            color = color,
            avatar = null,
            isOwnerProfile = isOwner,
            syncScope = SyncScope.LOCAL.name
        )
        profileDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun setSyncScope(profileId: String, scope: SyncScope) {
        val current = profileDao.getById(profileId) ?: return
        profileDao.update(current.copy(syncScope = scope.name))
    }

    override suspend fun removeProfile(profileId: String) = profileDao.deleteById(profileId)

    private fun ProfileEntity.toDomain() =
        Profile(id, familyId, displayName, color, avatar, isOwnerProfile, SyncScope.valueOf(syncScope))
}
