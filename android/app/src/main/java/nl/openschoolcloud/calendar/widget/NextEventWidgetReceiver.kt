/*
 * OSC Calendar - Privacy-first calendar for Dutch education
 * Copyright (C) 2025 Aldewereld Consultancy (OpenSchoolCloud)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 */
package nl.openschoolcloud.calendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * AppWidgetProvider for the "Volgende Les" home screen widget.
 * Shows the next upcoming event with 4 states: upcoming, in-progress,
 * reflection prompt, and empty. Uses AlarmManager for precise refresh
 * at event boundaries.
 */
class NextEventWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (widgetId in appWidgetIds) {
            try {
                NextEventWidget.updateWidget(context, appWidgetManager, widgetId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update widget $widgetId", e)
            }
        }
        try {
            scheduleNextAlarm(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule alarm", e)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == NextEventWidget.ACTION_WIDGET_REFRESH) {
            refreshAll(context)
        }
    }

    override fun onEnabled(context: Context) {
        try {
            scheduleNextAlarm(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule alarm on enable", e)
        }
    }

    override fun onDisabled(context: Context) {
        try {
            cancelAlarm(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel alarm on disable", e)
        }
    }

    companion object {
        private const val TAG = "NextEventWidget"
        private const val ALARM_REQUEST_CODE = 9001

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NextEventWidgetReceiver::class.java)
            )
            if (ids.isEmpty()) return

            val intent = Intent(context, NextEventWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }

        fun scheduleNextAlarm(context: Context) {
            val nextAlarmTime = NextEventWidget.getNextAlarmTime(context) ?: return

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return

            val intent = Intent(context, NextEventWidgetReceiver::class.java).apply {
                action = NextEventWidget.ACTION_WIDGET_REFRESH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Use setExact for precise updates at event boundaries.
            // Fall back to inexact alarm if exact alarm permission is not granted (Android 12+).
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExact(AlarmManager.RTC, nextAlarmTime, pendingIntent)
                    } else {
                        alarmManager.set(AlarmManager.RTC, nextAlarmTime, pendingIntent)
                    }
                } else {
                    alarmManager.setExact(AlarmManager.RTC, nextAlarmTime, pendingIntent)
                }
            } catch (e: SecurityException) {
                // Exact alarm permission revoked at runtime — fall back to inexact
                alarmManager.set(AlarmManager.RTC, nextAlarmTime, pendingIntent)
            }
        }

        private fun cancelAlarm(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                ?: return
            val intent = Intent(context, NextEventWidgetReceiver::class.java).apply {
                action = NextEventWidget.ACTION_WIDGET_REFRESH
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        }
    }
}
