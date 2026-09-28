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
package nl.openschoolcloud.calendar.data.remote.osccloud

import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wires the OSC-Cloud backend (subsystem C) into the existing CalDAV sync
 * stack: starting a trial links a shared Nextcloud account via the app's
 * normal account-discovery flow, and a periodic status check soft-degrades
 * back to LOCAL when the family's subscription is paused or lapsed. Local
 * data is never deleted by either path.
 */
@Singleton
class OscCloudSyncProvider @Inject constructor(
    private val api: OscCloudApi,
    private val accountRepository: AccountRepository,
    private val profileRepository: ProfileRepository
) {
    /** Starts (or resumes) the trial, links the shared CalDAV account, and moves every profile to OSC_CLOUD. */
    suspend fun startTrialAndActivate(familyId: String, memberCount: Int): Result<OscCloudStatus> {
        val status = api.startTrial(familyId, memberCount).getOrElse { return Result.failure(it) }

        val caldavUrl = status.caldavUrl
        val appPassword = status.appPassword
        if (caldavUrl != null && appPassword != null) {
            accountRepository.addAccount(caldavUrl, accountUsername(familyId), appPassword)
                .onFailure { return Result.failure(it) }
        }

        profileRepository.observeProfilesOnce(familyId).forEach {
            profileRepository.setSyncScope(it.id, SyncScope.OSC_CLOUD)
        }
        return Result.success(status)
    }

    /** Checks the current subscription status and soft-degrades profiles to LOCAL if it no longer entitles sync. */
    suspend fun refreshAndMaybeDegrade(familyId: String): Result<OscCloudStatus> {
        val status = api.status().getOrElse { return Result.failure(it) }
        if (shouldDegradeToLocal(status.status)) {
            profileRepository.observeProfilesOnce(familyId).forEach {
                profileRepository.setSyncScope(it.id, SyncScope.LOCAL)
            }
        }
        return Result.success(status)
    }

    private fun accountUsername(familyId: String) = "osc-cloud-$familyId"
}
