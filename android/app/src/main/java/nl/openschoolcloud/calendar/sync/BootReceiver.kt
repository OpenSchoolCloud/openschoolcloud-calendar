package nl.openschoolcloud.calendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nl.openschoolcloud.calendar.data.sync.CalendarSyncWorker
import nl.openschoolcloud.calendar.notification.ReminderWorker

/**
 * Broadcast receiver that starts background sync and reminder scheduling after device boot
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Schedule periodic sync with default 1 hour interval
            // The actual interval should be read from preferences
            CalendarSyncWorker.schedulePeriodic(
                context = context,
                intervalMinutes = 60
            )
            // Reschedule reminders for upcoming events
            ReminderWorker.schedule(context)
        }
    }
}
