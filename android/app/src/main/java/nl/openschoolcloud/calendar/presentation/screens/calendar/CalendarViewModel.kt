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
package nl.openschoolcloud.calendar.presentation.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.domain.model.Calendar
import nl.openschoolcloud.calendar.domain.model.Event
import nl.openschoolcloud.calendar.domain.model.HolidayEvent
import nl.openschoolcloud.calendar.domain.model.SyncStatus
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import nl.openschoolcloud.calendar.domain.repository.EventRepository
import nl.openschoolcloud.calendar.domain.repository.HolidayRepository
import nl.openschoolcloud.calendar.domain.usecase.ParsedEvent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the calendar screen
 *
 * Manages week view state, event loading, and sync operations
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val calendarRepository: CalendarRepository,
    private val holidayRepository: HolidayRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val isStandaloneMode: Boolean get() = appPreferences.isStandaloneMode

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val zoneId: ZoneId = ZoneId.systemDefault()

    init {
        // Load calendars and initial week
        loadCalendars()
        loadEventsForCurrentWeek()
    }

    /**
     * Load all visible calendars
     */
    private fun loadCalendars() {
        viewModelScope.launch {
            calendarRepository.getCalendars()
                .catch { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
                .collect { calendars ->
                    _uiState.update { it.copy(calendars = calendars) }
                }
        }
    }

    /**
     * Load events for the currently visible week
     */
    private fun loadEventsForCurrentWeek() {
        val state = _uiState.value
        val weekStart = state.selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekEnd = weekStart.plusDays(7)

        val startInstant = weekStart.atStartOfDay(zoneId).toInstant()
        val endInstant = weekEnd.atStartOfDay(zoneId).toInstant()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            eventRepository.getEvents(startInstant, endInstant)
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { events ->
                    // Group events by day
                    val eventsByDay = groupEventsByDay(events, weekStart)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            weekStart = weekStart,
                            eventsByDay = eventsByDay,
                            error = null
                        )
                    }
                }
        }

        // Load holiday events in parallel
        viewModelScope.launch {
            holidayRepository.getHolidayEvents(weekStart, weekEnd.minusDays(1))
                .catch { /* Holiday loading is non-critical */ }
                .collect { holidays ->
                    val holidaysByDay = holidays.groupBy { it.date }
                    _uiState.update { it.copy(holidayEventsByDay = holidaysByDay) }
                }
        }
    }

    /**
     * Group events by day for the week view
     */
    private fun groupEventsByDay(events: List<Event>, weekStart: LocalDate): Map<LocalDate, List<Event>> {
        val result = mutableMapOf<LocalDate, MutableList<Event>>()

        // Initialize all 7 days with empty lists
        for (i in 0..6) {
            result[weekStart.plusDays(i.toLong())] = mutableListOf()
        }

        // Assign events to their days
        events.forEach { event ->
            val eventDate = event.dtStart.atZone(zoneId).toLocalDate()
            result[eventDate]?.add(event)
        }

        // Sort events within each day by start time
        return result.mapValues { (_, eventList) ->
            eventList.sortedBy { it.dtStart }
        }
    }

    /**
     * Navigate to the previous week
     */
    fun previousWeek() {
        _uiState.update {
            it.copy(selectedDate = it.selectedDate.minusWeeks(1))
        }
        loadEventsForCurrentWeek()
    }

    /**
     * Navigate to the next week
     */
    fun nextWeek() {
        _uiState.update {
            it.copy(selectedDate = it.selectedDate.plusWeeks(1))
        }
        loadEventsForCurrentWeek()
    }

    /**
     * Navigate to today
     */
    fun goToToday() {
        _uiState.update {
            it.copy(selectedDate = LocalDate.now())
        }
        loadEventsForCurrentWeek()
    }

    /**
     * Select a specific date
     */
    fun selectDate(date: LocalDate) {
        _uiState.update {
            it.copy(selectedDate = date)
        }
        // If date is outside current week, reload events
        val currentWeekStart = _uiState.value.weekStart
        if (date.isBefore(currentWeekStart) || !date.isBefore(currentWeekStart.plusDays(7))) {
            loadEventsForCurrentWeek()
        }
    }

    /**
     * Toggle calendar visibility
     */
    fun toggleCalendarVisibility(calendarId: String) {
        viewModelScope.launch {
            val calendar = calendarRepository.getCalendar(calendarId) ?: return@launch
            val updated = calendar.copy(visible = !calendar.visible)
            calendarRepository.updateCalendar(updated)
            // The Flow will automatically update the UI
        }
    }

    /**
     * Sync all calendars
     */
    fun syncAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }

            val result = calendarRepository.syncAll()

            result.fold(
                onSuccess = { syncResults ->
                    val totalAdded = syncResults.sumOf { it.eventsAdded }
                    val totalUpdated = syncResults.sumOf { it.eventsUpdated }
                    val totalDeleted = syncResults.sumOf { it.eventsDeleted }
                    val failedCount = syncResults.count { !it.success }

                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncTime = Instant.now(),
                            syncMessage = if (failedCount > 0) {
                                "Sync completed with $failedCount errors"
                            } else {
                                "Synced: +$totalAdded, ~$totalUpdated, -$totalDeleted"
                            }
                        )
                    }

                    // Reload events after sync
                    loadEventsForCurrentWeek()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            error = "Sync failed: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Sync a specific calendar
     */
    fun syncCalendar(calendarId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }

            val result = calendarRepository.syncCalendar(calendarId)

            result.fold(
                onSuccess = { syncResult ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncTime = Instant.now(),
                            syncMessage = "Synced: +${syncResult.eventsAdded}, ~${syncResult.eventsUpdated}, -${syncResult.eventsDeleted}"
                        )
                    }
                    loadEventsForCurrentWeek()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            error = "Sync failed: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clear sync message
     */
    fun clearSyncMessage() {
        _uiState.update { it.copy(syncMessage = null) }
    }

    /**
     * Clear snackbar message
     */
    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Show holiday event detail
     */
    fun onHolidayEventClick(eventId: String) {
        viewModelScope.launch {
            val event = holidayRepository.getHolidayEvent(eventId)
            _uiState.update { it.copy(selectedHolidayEvent = event) }
        }
    }

    /**
     * Dismiss holiday detail
     */
    fun dismissHolidayDetail() {
        _uiState.update { it.copy(selectedHolidayEvent = null) }
    }

    /**
     * Create an event from Quick Capture parsed result
     */
    fun createEventFromParsed(parsed: ParsedEvent) {
        viewModelScope.launch {
            // Use selected date as fallback when no date was detected
            val effectiveDate = parsed.startDate ?: _uiState.value.selectedDate

            val startDateTime = LocalDateTime.of(
                effectiveDate,
                parsed.startTime ?: LocalTime.of(9, 0)
            )

            val endDateTime = if (parsed.endTime != null) {
                LocalDateTime.of(effectiveDate, parsed.endTime)
            } else {
                startDateTime.plus(parsed.duration)
            }

            val defaultCalendarId = getDefaultCalendarId()

            val event = Event(
                uid = UUID.randomUUID().toString(),
                calendarId = defaultCalendarId,
                summary = parsed.title,
                location = parsed.location,
                dtStart = startDateTime.atZone(zoneId).toInstant(),
                dtEnd = endDateTime.atZone(zoneId).toInstant(),
                allDay = false,
                syncStatus = SyncStatus.PENDING_CREATE
            )

            val result = eventRepository.createEvent(event)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(snackbarMessage = "\"${parsed.title}\" aangemaakt")
                    }
                    loadEventsForCurrentWeek()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(error = "Kon afspraak niet aanmaken: ${error.message}")
                    }
                }
            )
        }
    }

    /**
     * Get the default calendar ID (first visible writable calendar)
     */
    private suspend fun getDefaultCalendarId(): String {
        val calendars = _uiState.value.calendars
        return calendars.firstOrNull { !it.readOnly }?.id
            ?: calendars.firstOrNull()?.id
            ?: "default"
    }

    /**
     * Toggle task completion status
     */
    fun toggleTaskComplete(taskId: String) {
        viewModelScope.launch {
            eventRepository.toggleTaskCompleted(taskId).fold(
                onSuccess = { loadEventsForCurrentWeek() },
                onFailure = { error ->
                    _uiState.update { it.copy(error = error.message) }
                }
            )
        }
    }

    /**
     * Refresh the current view
     */
    fun refresh() {
        loadEventsForCurrentWeek()
    }
}

/**
 * UI state for the calendar screen
 */
data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val eventsByDay: Map<LocalDate, List<Event>> = emptyMap(),
    val holidayEventsByDay: Map<LocalDate, List<HolidayEvent>> = emptyMap(),
    val selectedHolidayEvent: HolidayEvent? = null,
    val calendars: List<Calendar> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val lastSyncTime: Instant? = null,
    val syncMessage: String? = null,
    val snackbarMessage: String? = null,
    val error: String? = null
) {
    /**
     * Get events for a specific day
     */
    fun getEventsForDay(date: LocalDate): List<Event> {
        return eventsByDay[date] ?: emptyList()
    }

    /**
     * Get holiday events for a specific day
     */
    fun getHolidayEventsForDay(date: LocalDate): List<HolidayEvent> {
        return holidayEventsByDay[date] ?: emptyList()
    }

    /**
     * Get all events in the current week
     */
    fun getAllEvents(): List<Event> {
        return eventsByDay.values.flatten()
    }

    /**
     * Get the days of the current week
     */
    fun getWeekDays(): List<LocalDate> {
        return (0..6).map { weekStart.plusDays(it.toLong()) }
    }

    /**
     * Check if a date is today
     */
    fun isToday(date: LocalDate): Boolean {
        return date == LocalDate.now()
    }

    /**
     * Check if a date is the selected date
     */
    fun isSelected(date: LocalDate): Boolean {
        return date == selectedDate
    }
}
