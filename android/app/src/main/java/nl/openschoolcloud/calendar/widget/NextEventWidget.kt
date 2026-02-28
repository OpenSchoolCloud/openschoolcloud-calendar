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

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import androidx.room.Room
import nl.openschoolcloud.calendar.MainActivity
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.data.local.AppDatabase
import nl.openschoolcloud.calendar.data.local.entity.EventEntity
import nl.openschoolcloud.calendar.notification.NotificationHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the RemoteViews for the "Volgende Les" widget (3x2).
 * Supports 4 states: upcoming, in-progress, reflection, and empty.
 *
 * Runs synchronously — widget providers have a 10-second ANR window,
 * and our Room queries complete well within that. Using Executors caused
 * "Kan widget niet laden" on Xiaomi/MIUI because the process was killed
 * before the background thread could deliver RemoteViews.
 */
object NextEventWidget {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale("nl"))

    const val ACTION_WIDGET_REFRESH = "nl.openschoolcloud.calendar.WIDGET_NEXT_REFRESH"
    const val ACTION_REFLECT_EVENT = "nl.openschoolcloud.calendar.ACTION_REFLECT_EVENT"
    const val EXTRA_MOOD = "extra_mood"

    private const val REFLECTION_WINDOW_MINUTES = 15L
    private const val DEFAULT_COLOR = 0xFF3B9FD9.toInt()

    private enum class WidgetState {
        UPCOMING, IN_PROGRESS, REFLECTION, EMPTY
    }

    fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        // Build synchronously — avoids process-kill race on MIUI
        val views = buildViews(context, widgetId)
        try {
            appWidgetManager.updateAppWidget(widgetId, views)
        } catch (e: Exception) {
            // Ignore — widget may have been removed
        }
    }

    /**
     * Returns the next alarm time (epoch millis) for widget auto-refresh,
     * or null if no upcoming events need refreshing.
     */
    fun getNextAlarmTime(context: Context): Long? {
        return try {
            val db = getDatabase(context)
            val now = System.currentTimeMillis()
            val todayEvents = getTodayEvents(db)
            if (todayEvents.isEmpty()) return null

            val alarmTimes = mutableListOf<Long>()
            for (event in todayEvents) {
                if (event.dtStart > now) {
                    alarmTimes.add(event.dtStart)
                }
                val end = event.dtEnd
                if (end != null && end > now) {
                    alarmTimes.add(end)
                    alarmTimes.add(end + REFLECTION_WINDOW_MINUTES * 60_000L)
                }
            }

            alarmTimes.filter { it > now }.minOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun getDatabase(context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "openschoolcloud_calendar.db"
        ).allowMainThreadQueries().build()
    }

    private fun buildViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_next_event)

        // Hide all states — layout default has state_empty visible as safe fallback
        views.setViewVisibility(R.id.state_upcoming, View.GONE)
        views.setViewVisibility(R.id.state_in_progress, View.GONE)
        views.setViewVisibility(R.id.state_reflection, View.GONE)
        views.setViewVisibility(R.id.state_empty, View.GONE)

        try {
            val db = getDatabase(context)
            val now = System.currentTimeMillis()
            val todayEvents = getTodayEvents(db)
            val calendarDao = db.calendarDao()
            val reflectionDao = db.reflectionDao()

            val calendars = try {
                calendarDao.getVisibleCalendarsSync()
            } catch (e: Exception) {
                emptyList()
            }
            val colorMap = calendars.associate { it.id to it.colorInt }

            // Find current, previous, and next events
            val currentEvent = todayEvents.firstOrNull { event ->
                event.dtStart <= now && (event.dtEnd ?: Long.MAX_VALUE) > now
            }
            val nextEvent = todayEvents.firstOrNull { it.dtStart > now }
            val previousEvent = todayEvents.lastOrNull { event ->
                val end = event.dtEnd ?: event.dtStart
                end <= now
            }

            // Determine state
            val state: WidgetState
            val relevantEvent: EventEntity?

            if (currentEvent != null) {
                state = WidgetState.IN_PROGRESS
                relevantEvent = currentEvent
            } else if (previousEvent != null) {
                val prevEnd = previousEvent.dtEnd ?: previousEvent.dtStart
                val minutesSinceEnd = (now - prevEnd) / 60_000L
                val hasReflection = try {
                    reflectionDao.hasReflectionSync(previousEvent.uid)
                } catch (e: Exception) {
                    true
                }

                if (minutesSinceEnd <= REFLECTION_WINDOW_MINUTES && !hasReflection) {
                    state = WidgetState.REFLECTION
                    relevantEvent = previousEvent
                } else if (nextEvent != null) {
                    state = WidgetState.UPCOMING
                    relevantEvent = nextEvent
                } else {
                    state = WidgetState.EMPTY
                    relevantEvent = null
                }
            } else if (nextEvent != null) {
                state = WidgetState.UPCOMING
                relevantEvent = nextEvent
            } else {
                state = WidgetState.EMPTY
                relevantEvent = null
            }

            when (state) {
                WidgetState.UPCOMING -> {
                    views.setViewVisibility(R.id.state_upcoming, View.VISIBLE)
                    val event = relevantEvent ?: return views
                    val color = colorMap[event.calendarId] ?: DEFAULT_COLOR

                    views.setInt(R.id.upcoming_color_bar, "setBackgroundColor", color)
                    views.setTextViewText(R.id.upcoming_title, event.summary)

                    val diffMinutes = (event.dtStart - now) / 60_000L
                    val countdownText = if (diffMinutes < 60) {
                        context.getString(R.string.widget_in_minutes, diffMinutes.toInt())
                    } else {
                        context.getString(R.string.widget_in_hours, (diffMinutes / 60).toInt())
                    }
                    views.setTextViewText(R.id.upcoming_countdown, countdownText)

                    val zone = ZoneId.systemDefault()
                    val startStr = Instant.ofEpochMilli(event.dtStart).atZone(zone).format(timeFormatter)
                    val timeText = if (event.dtEnd != null) {
                        val endStr = Instant.ofEpochMilli(event.dtEnd).atZone(zone).format(timeFormatter)
                        "$startStr - $endStr"
                    } else {
                        startStr
                    }
                    views.setTextViewText(R.id.upcoming_time, timeText)

                    if (!event.location.isNullOrBlank()) {
                        views.setTextViewText(R.id.upcoming_location, event.location)
                        views.setViewVisibility(R.id.upcoming_location, View.VISIBLE)
                    } else {
                        views.setViewVisibility(R.id.upcoming_location, View.GONE)
                    }

                    setEventClickIntent(context, views, R.id.state_upcoming, event.uid, widgetId)
                }

                WidgetState.IN_PROGRESS -> {
                    views.setViewVisibility(R.id.state_in_progress, View.VISIBLE)
                    val event = relevantEvent ?: return views
                    val color = colorMap[event.calendarId] ?: DEFAULT_COLOR

                    views.setInt(R.id.progress_color_bar, "setBackgroundColor", color)
                    views.setTextViewText(R.id.progress_title, event.summary)

                    if (event.dtEnd != null) {
                        val remainingMinutes = ((event.dtEnd - now) / 60_000L).toInt()
                        views.setTextViewText(
                            R.id.progress_remaining,
                            context.getString(R.string.widget_time_remaining, remainingMinutes)
                        )
                    } else {
                        views.setViewVisibility(R.id.progress_remaining, View.GONE)
                    }

                    setEventClickIntent(context, views, R.id.state_in_progress, event.uid, widgetId)
                }

                WidgetState.REFLECTION -> {
                    views.setViewVisibility(R.id.state_reflection, View.VISIBLE)
                    val event = relevantEvent ?: return views

                    val prompt = context.getString(R.string.widget_how_was_it, event.summary)
                    views.setTextViewText(R.id.reflection_prompt, prompt)

                    setReflectionClickIntent(context, views, R.id.reflection_bad, event.uid, 1, widgetId)
                    setReflectionClickIntent(context, views, R.id.reflection_ok, event.uid, 3, widgetId)
                    setReflectionClickIntent(context, views, R.id.reflection_good, event.uid, 5, widgetId)
                }

                WidgetState.EMPTY -> {
                    views.setViewVisibility(R.id.state_empty, View.VISIBLE)
                    setCalendarClickIntent(context, views, R.id.state_empty, widgetId)
                }
            }
        } catch (e: Exception) {
            // Show empty state on any error
            views.setViewVisibility(R.id.state_empty, View.VISIBLE)
            setCalendarClickIntent(context, views, R.id.state_empty, widgetId)
        }

        return views
    }

    private fun getTodayEvents(db: AppDatabase): List<EventEntity> {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val eventDao = db.eventDao()
        val calendarDao = db.calendarDao()

        val visibleIds = calendarDao.getVisibleCalendarsSync().map { it.id }.toSet()

        return eventDao.getInRangeSync(startOfDay, endOfDay)
            .filter { it.calendarId in visibleIds && it.syncStatus != "PENDING_DELETE" }
            .sortedBy { it.dtStart }
    }

    private fun setCalendarClickIntent(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        widgetId: Int
    ) {
        val calendarIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId + 2000,
            calendarIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(viewId, pendingIntent)
    }

    private fun setEventClickIntent(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        eventUid: String,
        widgetId: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = NotificationHelper.ACTION_VIEW_EVENT
            putExtra(NotificationHelper.EXTRA_EVENT_ID, eventUid)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId + 2000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(viewId, pendingIntent)
    }

    private fun setReflectionClickIntent(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        eventUid: String,
        mood: Int,
        widgetId: Int
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_REFLECT_EVENT
            putExtra(NotificationHelper.EXTRA_EVENT_ID, eventUid)
            putExtra(EXTRA_MOOD, mood)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            widgetId + 3000 + mood,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(viewId, pendingIntent)
    }
}
