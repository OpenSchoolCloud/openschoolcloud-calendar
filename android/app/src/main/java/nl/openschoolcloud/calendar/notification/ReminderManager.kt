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
package nl.openschoolcloud.calendar.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.domain.model.Event
import nl.openschoolcloud.calendar.domain.model.ReminderTrigger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    companion object {
        private const val TAG = "ReminderManager"
    }

    private val alarmManager: AlarmManager
        get() = context.getSystemService(AlarmManager::class.java)

    /**
     * Schedule reminders for an event based on its reminder settings
     * or the default reminder preference.
     */
    fun scheduleReminder(event: Event) {
        if (!appPreferences.notificationsEnabled) return
        if (event.allDay) return // Skip all-day events

        val reminderMinutes = if (event.reminders.isNotEmpty()) {
            event.reminders.mapNotNull { reminder ->
                when (val trigger = reminder.trigger) {
                    is ReminderTrigger.BeforeStart -> trigger.minutes
                    is ReminderTrigger.AtTime -> null // Skip absolute time triggers
                }
            }
        } else {
            val defaultMinutes = appPreferences.defaultReminderMinutes
            if (defaultMinutes > 0) listOf(defaultMinutes) else emptyList()
        }

        reminderMinutes.forEachIndexed { index, minutes ->
            val triggerTime = event.dtStart.toEpochMilli() - (minutes * 60_000L)
            if (triggerTime > System.currentTimeMillis()) {
                scheduleAlarm(event, triggerTime, index)
            }
        }
    }

    /**
     * Schedule a reflection reminder for a learning agenda event.
     * Fires at event end time.
     */
    fun scheduleReflectionReminder(event: Event) {
        if (!appPreferences.reflectionNotificationsEnabled) return
        if (!event.isLearningAgenda) return
        if (event.allDay) return

        val endTime = event.dtEnd?.toEpochMilli() ?: return
        if (endTime <= System.currentTimeMillis()) return

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_REFLECTION
            putExtra(NotificationHelper.EXTRA_EVENT_ID, event.uid)
            putExtra(NotificationHelper.EXTRA_EVENT_TITLE, event.summary)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            event.uid.hashCode() + 200,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarm(endTime, pendingIntent)
        Log.d(TAG, "Scheduled reflection reminder for '${event.summary}' at ${java.time.Instant.ofEpochMilli(endTime)}")
    }

    /**
     * Cancel all reminders for an event.
     */
    fun cancelReminder(eventId: String) {
        // Cancel up to 5 possible reminder indices per event
        for (index in 0..4) {
            val pendingIntent = createPendingIntent(eventId, index)
            alarmManager.cancel(pendingIntent)
        }
    }

    /**
     * Reschedule a reminder (used for snooze).
     */
    fun snoozeReminder(
        eventId: String,
        title: String,
        startTime: Long,
        location: String?,
        snoozeMinutes: Int = NotificationHelper.SNOOZE_MINUTES
    ) {
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60_000L)

        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_REMINDER
            putExtra(NotificationHelper.EXTRA_EVENT_ID, eventId)
            putExtra(NotificationHelper.EXTRA_EVENT_TITLE, title)
            putExtra(NotificationHelper.EXTRA_EVENT_TIME, startTime)
            putExtra(NotificationHelper.EXTRA_EVENT_LOCATION, location)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode() + 100, // Offset for snooze
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarm(triggerTime, pendingIntent)
    }

    private fun scheduleAlarm(event: Event, triggerTimeMillis: Long, index: Int) {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_REMINDER
            putExtra(NotificationHelper.EXTRA_EVENT_ID, event.uid)
            putExtra(NotificationHelper.EXTRA_EVENT_TITLE, event.summary)
            putExtra(NotificationHelper.EXTRA_EVENT_TIME, event.dtStart.toEpochMilli())
            putExtra(
                NotificationHelper.EXTRA_EVENT_LOCATION,
                event.location
            )
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            createRequestCode(event.uid, index),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        scheduleExactAlarm(triggerTimeMillis, pendingIntent)
        Log.d(TAG, "Scheduled reminder for '${event.summary}' at ${Instant.ofEpochMilli(triggerTimeMillis)}")
    }

    private fun scheduleExactAlarm(triggerTimeMillis: Long, pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } else {
                // Fall back to inexact alarm
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent
            )
        }
    }

    private fun createPendingIntent(eventId: String, index: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_SHOW_REMINDER
        }
        return PendingIntent.getBroadcast(
            context,
            createRequestCode(eventId, index),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createRequestCode(eventId: String, index: Int): Int {
        return eventId.hashCode() + index
    }
}
