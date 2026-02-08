package nl.openschoolcloud.calendar.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * BroadcastReceiver that handles alarm triggers for event reminders
 * and snooze actions.
 */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var reminderManager: ReminderManager

    companion object {
        const val ACTION_SHOW_REMINDER = "nl.openschoolcloud.calendar.ACTION_SHOW_REMINDER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHOW_REMINDER -> handleShowReminder(intent)
            NotificationHelper.ACTION_SNOOZE -> handleSnooze(intent)
        }
    }

    private fun handleShowReminder(intent: Intent) {
        val eventId = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_TITLE) ?: return
        val startTime = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_TIME, 0L)
        val location = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_LOCATION)

        if (startTime == 0L) return

        notificationHelper.showEventReminder(
            eventId = eventId,
            title = title,
            startTime = startTime,
            endTime = null,
            location = location
        )
    }

    private fun handleSnooze(intent: Intent) {
        val eventId = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_TITLE) ?: return
        val startTime = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_TIME, 0L)
        val location = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_LOCATION)

        // Dismiss current notification
        notificationHelper.cancelNotification(eventId)

        // Schedule snooze
        reminderManager.snoozeReminder(
            eventId = eventId,
            title = title,
            startTime = startTime,
            location = location
        )
    }
}
