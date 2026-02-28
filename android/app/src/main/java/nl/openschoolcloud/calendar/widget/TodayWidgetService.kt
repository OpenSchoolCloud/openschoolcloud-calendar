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

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.room.Room
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.data.local.AppDatabase
import nl.openschoolcloud.calendar.notification.NotificationHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * RemoteViewsService that provides the factory for the Dagplanning widget's event list.
 */
class TodayWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return TodayWidgetFactory(applicationContext)
    }
}

/**
 * Factory that loads today's events from the database and provides
 * RemoteViews for each event row in the widget with color dots,
 * time ranges, and optional location.
 */
class TodayWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var events: List<WidgetEvent> = emptyList()
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("nl"))

    private val db: AppDatabase by lazy {
        Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "openschoolcloud_calendar.db"
        ).allowMainThreadQueries().build()
    }

    data class WidgetEvent(
        val uid: String,
        val summary: String,
        val startTime: Long,
        val endTime: Long?,
        val location: String?,
        val calendarColor: Int
    )

    override fun onCreate() {
        // Initial data load happens in onDataSetChanged
    }

    override fun onDataSetChanged() {
        try {
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()
            val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

            val eventDao = db.eventDao()
            val calendarDao = db.calendarDao()

            // Build calendar color map
            val calendars = calendarDao.getVisibleCalendarsSync()
            val colorMap = calendars.associate { it.id to it.colorInt }
            val visibleCalendarIds = calendars.map { it.id }.toSet()

            // Load today's events
            val rawEvents = eventDao.getInRangeSync(startOfDay, endOfDay)

            events = rawEvents
                .filter { it.calendarId in visibleCalendarIds && it.syncStatus != "PENDING_DELETE" }
                .sortedBy { it.dtStart }
                .map { entity ->
                    WidgetEvent(
                        uid = entity.uid,
                        summary = entity.summary,
                        startTime = entity.dtStart,
                        endTime = entity.dtEnd,
                        location = entity.location,
                        calendarColor = colorMap[entity.calendarId] ?: Color.parseColor("#3B9FD9")
                    )
                }
        } catch (e: Exception) {
            events = emptyList()
        }
    }

    override fun onDestroy() {
        events = emptyList()
    }

    override fun getCount(): Int = events.size

    override fun getViewAt(position: Int): RemoteViews {
        val event = events[position]
        val views = RemoteViews(context.packageName, R.layout.widget_event_row)
        val zone = ZoneId.systemDefault()

        // Color dot (tinted via setColorFilter)
        views.setInt(R.id.widget_event_color, "setColorFilter", event.calendarColor)

        // Time range: "09:00 - 10:30" or just "09:00"
        val startFormatted = Instant.ofEpochMilli(event.startTime)
            .atZone(zone)
            .format(timeFormatter)
        val timeText = if (event.endTime != null) {
            val endFormatted = Instant.ofEpochMilli(event.endTime)
                .atZone(zone)
                .format(timeFormatter)
            "$startFormatted - $endFormatted"
        } else {
            startFormatted
        }
        views.setTextViewText(R.id.widget_event_time, timeText)

        // Title
        views.setTextViewText(R.id.widget_event_title, event.summary)

        // Location (only show if present)
        if (!event.location.isNullOrBlank()) {
            views.setTextViewText(R.id.widget_event_location, event.location)
            views.setViewVisibility(R.id.widget_event_location, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_event_location, View.GONE)
        }

        // Fill-in intent for click handling
        val fillInIntent = Intent().apply {
            putExtra(NotificationHelper.EXTRA_EVENT_ID, event.uid)
        }
        views.setOnClickFillInIntent(R.id.widget_event_row, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = events[position].uid.hashCode().toLong()

    override fun hasStableIds(): Boolean = true
}
