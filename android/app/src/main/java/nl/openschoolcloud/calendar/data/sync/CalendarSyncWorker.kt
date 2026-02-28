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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker for background calendar synchronization
 *
 * Syncs all visible calendars with the Nextcloud CalDAV server
 */
@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val calendarRepository: CalendarRepository,
    private val appPreferences: AppPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // Skip sync in standalone mode
        if (appPreferences.isStandaloneMode) {
            return Result.success()
        }

        return try {
            val syncResult = calendarRepository.syncAll()

            syncResult.fold(
                onSuccess = { results ->
                    // Check if any sync failed
                    val failures = results.count { !it.success }
                    if (failures > 0 && failures == results.size) {
                        // All syncs failed
                        Result.retry()
                    } else {
                        Result.success()
                    }
                },
                onFailure = { error ->
                    // Network or auth errors should retry
                    if (error.message?.contains("401") == true ||
                        error.message?.contains("403") == true
                    ) {
                        // Auth error - don't retry
                        Result.failure()
                    } else {
                        // Network or other transient error
                        Result.retry()
                    }
                }
            )
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "calendar_sync"
        private const val TAG = "CalendarSyncWorker"

        /**
         * Schedule periodic sync with the given interval
         */
        fun schedulePeriodic(
            context: Context,
            intervalMinutes: Long = 60,
            requiresNetwork: Boolean = true,
            requiresCharging: Boolean = false
        ) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED
                )
                .setRequiresCharging(requiresCharging)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                intervalMinutes, TimeUnit.MINUTES,
                // Flex interval: worker can run in the last 15 minutes of each period
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    1, TimeUnit.MINUTES
                )
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
                )
        }

        /**
         * Request immediate one-time sync
         */
        fun syncNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                .setConstraints(constraints)
                .addTag(TAG)
                .build()

            WorkManager.getInstance(context)
                .enqueue(workRequest)
        }

        /**
         * Cancel all sync work
         */
        fun cancelAll(context: Context) {
            WorkManager.getInstance(context)
                .cancelUniqueWork(WORK_NAME)
        }

        /**
         * Get sync interval options in minutes
         */
        fun getSyncIntervalOptions(): List<SyncIntervalOption> {
            return listOf(
                SyncIntervalOption(15, "Every 15 minutes"),
                SyncIntervalOption(30, "Every 30 minutes"),
                SyncIntervalOption(60, "Every hour"),
                SyncIntervalOption(240, "Every 4 hours"),
                SyncIntervalOption(0, "Manual only")
            )
        }
    }
}

/**
 * Sync interval option for settings
 */
data class SyncIntervalOption(
    val minutes: Long,
    val displayName: String
)

/**
 * Manager for sync operations
 */
class SyncManager(private val context: Context) {

    /**
     * Start periodic sync with the saved interval
     */
    fun startPeriodicSync(intervalMinutes: Long) {
        if (intervalMinutes > 0) {
            CalendarSyncWorker.schedulePeriodic(context, intervalMinutes)
        } else {
            // Manual only - cancel periodic work
            CalendarSyncWorker.cancelAll(context)
        }
    }

    /**
     * Request immediate sync
     */
    fun syncNow() {
        CalendarSyncWorker.syncNow(context)
    }

    /**
     * Stop all background sync
     */
    fun stopSync() {
        CalendarSyncWorker.cancelAll(context)
    }
}
