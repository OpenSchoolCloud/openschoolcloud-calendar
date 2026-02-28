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

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * AppWidgetProvider for the "Dagplanning" home screen widget.
 * Shows today's date and all events for the day with color dots.
 */
class TodayWidgetReceiver : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { widgetId ->
            TodayWidget.updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        // Handle refresh button tap
        if (intent.action == TodayWidget.ACTION_REFRESH) {
            refreshAll(context)
        }
    }

    override fun onEnabled(context: Context) {
        // First widget placed
    }

    override fun onDisabled(context: Context) {
        // Last widget removed
    }

    companion object {
        /**
         * Trigger a refresh of all Dagplanning widgets.
         * Call this after event create/edit/delete.
         */
        fun refreshAll(context: Context) {
            val intent = Intent(context, TodayWidgetReceiver::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, TodayWidgetReceiver::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
