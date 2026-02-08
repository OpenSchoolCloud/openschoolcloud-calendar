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

    companion object {
        private const val KEY_SYNC_INTERVAL = "sync_interval_minutes"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_DEFAULT_REMINDER = "default_reminder_minutes"

        const val DEFAULT_SYNC_INTERVAL = 60L
        const val DEFAULT_REMINDER_MINUTES = 15
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
