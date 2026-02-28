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
package nl.openschoolcloud.calendar.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("osc_preferences", Context.MODE_PRIVATE)

    var syncIntervalMinutes: Long
        get() = prefs.getLong(KEY_SYNC_INTERVAL, DEFAULT_SYNC_INTERVAL)
        set(value) = prefs.edit().putLong(KEY_SYNC_INTERVAL, value).apply()

    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_SYNC, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SYNC, value).apply()

    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, value).apply()

    var defaultReminderMinutes: Int
        get() = prefs.getInt(KEY_DEFAULT_REMINDER, DEFAULT_REMINDER_MINUTES)
        set(value) = prefs.edit().putInt(KEY_DEFAULT_REMINDER, value).apply()

    var onboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, value).apply()

    var promoDismissed: Boolean
        get() = prefs.getBoolean(KEY_PROMO_DISMISSED, false)
        set(value) = prefs.edit().putBoolean(KEY_PROMO_DISMISSED, value).apply()

    var holidaySeedVersion: Int
        get() = prefs.getInt(KEY_HOLIDAY_SEED_VERSION, 0)
        set(value) = prefs.edit().putInt(KEY_HOLIDAY_SEED_VERSION, value).apply()

    var reflectionNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_REFLECTION_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_REFLECTION_NOTIFICATIONS, value).apply()

    var weekPlanningNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_PLANNING_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_PLANNING_NOTIFICATIONS, value).apply()

    var planningDayOfWeek: Int
        get() = prefs.getInt(KEY_PLANNING_DAY, 1) // 1 = Monday (ISO DayOfWeek)
        set(value) = prefs.edit().putInt(KEY_PLANNING_DAY, value).apply()

    var planningTimeHour: Int
        get() = prefs.getInt(KEY_PLANNING_HOUR, 8)
        set(value) = prefs.edit().putInt(KEY_PLANNING_HOUR, value).apply()

    var planningTimeMinute: Int
        get() = prefs.getInt(KEY_PLANNING_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_PLANNING_MINUTE, value).apply()

    var planningLastWeekYear: Int
        get() = prefs.getInt(KEY_PLANNING_LAST_WEEK_YEAR, 0)
        set(value) = prefs.edit().putInt(KEY_PLANNING_LAST_WEEK_YEAR, value).apply()

    var planningStreak: Int
        get() = prefs.getInt(KEY_PLANNING_STREAK, 0)
        set(value) = prefs.edit().putInt(KEY_PLANNING_STREAK, value).apply()

    var isStandaloneMode: Boolean
        get() = prefs.getBoolean(KEY_STANDALONE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_STANDALONE_MODE, value).apply()

    var hasCompletedModeSelection: Boolean
        get() = prefs.getBoolean(KEY_MODE_SELECTION_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_MODE_SELECTION_COMPLETED, value).apply()

    companion object {
        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_DEFAULT_REMINDER = "default_reminder_minutes"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_PROMO_DISMISSED = "promo_dismissed"
        private const val KEY_HOLIDAY_SEED_VERSION = "holiday_seed_version"
        private const val KEY_REFLECTION_NOTIFICATIONS = "reflection_notifications_enabled"
        private const val KEY_PLANNING_NOTIFICATIONS = "planning_notifications_enabled"
        private const val KEY_PLANNING_DAY = "planning_day_of_week"
        private const val KEY_PLANNING_HOUR = "planning_time_hour"
        private const val KEY_PLANNING_MINUTE = "planning_time_minute"
        private const val KEY_PLANNING_LAST_WEEK_YEAR = "planning_last_week_year"
        private const val KEY_PLANNING_STREAK = "planning_streak"
        private const val KEY_STANDALONE_MODE = "standalone_mode"
        private const val KEY_MODE_SELECTION_COMPLETED = "mode_selection_completed"

        const val DEFAULT_SYNC_INTERVAL = 60L
        const val DEFAULT_REMINDER_MINUTES = 15
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
