package nl.openschoolcloud.calendar.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.openschoolcloud.calendar.MainActivity
import nl.openschoolcloud.calendar.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val ACTION_VIEW_EVENT = "nl.openschoolcloud.calendar.ACTION_VIEW_EVENT"
        const val ACTION_SNOOZE = "nl.openschoolcloud.calendar.ACTION_SNOOZE"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_EVENT_TITLE = "event_title"
        const val EXTRA_EVENT_TIME = "event_time"
        const val EXTRA_EVENT_LOCATION = "event_location"
        const val SNOOZE_MINUTES = 5
    }

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("nl"))

    fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_desc)
            enableVibration(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun showEventReminder(
        eventId: String,
        title: String,
        startTime: Long,
        endTime: Long?,
        location: String?
    ) {
        if (!hasNotificationPermission()) return

        val viewIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_VIEW_EVENT
            putExtra(EXTRA_EVENT_ID, eventId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            viewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_SNOOZE
            putExtra(EXTRA_EVENT_ID, eventId)
            putExtra(EXTRA_EVENT_TITLE, title)
            putExtra(EXTRA_EVENT_TIME, startTime)
            putExtra(EXTRA_EVENT_LOCATION, location)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode() + 1,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val timeText = formatTimeRange(startTime, endTime)

        val contentText = buildString {
            append(timeText)
            if (!location.isNullOrBlank()) {
                append(" · ")
                append(location)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(viewPendingIntent)
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_view),
                viewPendingIntent
            )
            .addAction(
                R.drawable.ic_notification,
                context.getString(R.string.notification_action_snooze),
                snoozePendingIntent
            )
            .build()

        NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
    }

    fun cancelNotification(eventId: String) {
        NotificationManagerCompat.from(context).cancel(eventId.hashCode())
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun formatTimeRange(startMillis: Long, endMillis: Long?): String {
        val start = Instant.ofEpochMilli(startMillis)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)
        return if (endMillis != null) {
            val end = Instant.ofEpochMilli(endMillis)
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter)
            "$start - $end"
        } else {
            start
        }
    }
}
