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
package nl.openschoolcloud.calendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.data.sync.CalendarSyncWorker
import nl.openschoolcloud.calendar.notification.ReminderWorker

/**
 * Broadcast receiver that starts background sync and reminder scheduling after device boot
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Read the user's configured sync interval from preferences
            val prefs = AppPreferences(context)
            val intervalMinutes = prefs.syncIntervalMinutes

            CalendarSyncWorker.schedulePeriodic(
                context = context,
                intervalMinutes = intervalMinutes
            )
            // Reschedule reminders for upcoming events
            ReminderWorker.schedule(context)
        }
    }
}
