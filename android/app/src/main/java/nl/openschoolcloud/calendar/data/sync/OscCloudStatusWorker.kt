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
package nl.openschoolcloud.calendar.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import nl.openschoolcloud.calendar.data.remote.osccloud.OscCloudSyncProvider
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import nl.openschoolcloud.calendar.util.FeatureFlags
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically checks the family's OSC-Cloud
 * subscription status and soft-degrades profiles back to LOCAL when it no
 * longer entitles sync (see [OscCloudSyncProvider.refreshAndMaybeDegrade]).
 * A no-op while [FeatureFlags.oscCloudEnabled] is false or no profile uses
 * OSC_CLOUD sync.
 */
@HiltWorker
class OscCloudStatusWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val familyRepository: FamilyRepository,
    private val profileRepository: ProfileRepository,
    private val oscCloudSyncProvider: OscCloudSyncProvider
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!FeatureFlags.oscCloudEnabled) return Result.success()
        return try {
            checkAndDegradeIfNeeded(familyRepository, profileRepository, oscCloudSyncProvider)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "osc_cloud_status"

        /**
         * Shared decision logic: only calls [OscCloudSyncProvider.refreshAndMaybeDegrade]
         * when a family exists with at least one profile on OSC_CLOUD sync.
         * Extracted so both the periodic worker and the app-start opportunistic
         * check use the exact same rule, and so it can be unit tested without
         * standing up a CoroutineWorker.
         */
        suspend fun checkAndDegradeIfNeeded(
            familyRepository: FamilyRepository,
            profileRepository: ProfileRepository,
            oscCloudSyncProvider: OscCloudSyncProvider
        ) {
            val family = familyRepository.observeFamily().first() ?: return
            val profiles = profileRepository.observeProfilesOnce(family.id)
            if (profiles.any { it.syncScope == SyncScope.OSC_CLOUD }) {
                oscCloudSyncProvider.refreshAndMaybeDegrade(family.id)
            }
        }

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<OscCloudStatusWorker>(
                8, TimeUnit.HOURS
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}
