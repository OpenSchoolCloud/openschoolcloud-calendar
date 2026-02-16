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
        const val ACTION_SHOW_REFLECTION = "nl.openschoolcloud.calendar.ACTION_SHOW_REFLECTION"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SHOW_REMINDER -> handleShowReminder(intent)
            NotificationHelper.ACTION_SNOOZE -> handleSnooze(intent)
            ACTION_SHOW_REFLECTION -> handleShowReflection(intent)
            else -> { /* ignore unknown actions */ }
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

    private fun handleShowReflection(intent: Intent) {
        val eventId = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_ID) ?: return
        val title = intent.getStringExtra(NotificationHelper.EXTRA_EVENT_TITLE) ?: return

        notificationHelper.showReflectionPrompt(eventId = eventId, title = title)
    }
}
