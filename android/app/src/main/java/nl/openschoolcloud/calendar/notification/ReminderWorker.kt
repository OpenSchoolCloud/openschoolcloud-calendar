package nl.openschoolcloud.calendar.notification

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.domain.repository.EventRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that periodically schedules reminders for upcoming events.
 * Acts as a backup to ensure reminders are set even if the app was killed.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val eventRepository: EventRepository,
    private val reminderManager: ReminderManager,
    private val appPreferences: AppPreferences
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val WORK_NAME = "reminder_scheduler"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(
                1, TimeUnit.HOURS,
                15, TimeUnit.MINUTES
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        if (!appPreferences.notificationsEnabled) return Result.success()

        // Schedule reminders for the next 24 hours
        val now = Instant.now()
        val tomorrow = now.plus(24, ChronoUnit.HOURS)

        val events = eventRepository.getEvents(now, tomorrow).first()
        events.forEach { event ->
            reminderManager.scheduleReminder(event)
        }

        return Result.success()
    }
}
