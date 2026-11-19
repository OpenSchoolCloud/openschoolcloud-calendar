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
package nl.openschoolcloud.calendar.presentation.screens.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.domain.model.Event
import nl.openschoolcloud.calendar.domain.model.SyncStatus
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import nl.openschoolcloud.calendar.domain.repository.EventRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class WeekPlanningViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val calendarRepository: CalendarRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeekPlanningUiState())
    val uiState: StateFlow<WeekPlanningUiState> = _uiState.asStateFlow()

    private val zoneId: ZoneId = ZoneId.systemDefault()

    init {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _uiState.update {
            it.copy(
                weekStart = weekStart,
                planningStreak = appPreferences.planningStreak,
                tasks = listOf(
                    PlanningTask(id = UUID.randomUUID().toString()),
                    PlanningTask(id = UUID.randomUUID().toString()),
                    PlanningTask(id = UUID.randomUUID().toString())
                )
            )
        }
        loadExistingTasks()
    }

    private fun loadExistingTasks() {
        viewModelScope.launch {
            val weekStart = _uiState.value.weekStart
            val weekEnd = weekStart.plusDays(7)
            val startInstant = weekStart.atStartOfDay(zoneId).toInstant()
            val endInstant = weekEnd.atStartOfDay(zoneId).toInstant()

            val existingTasks = eventRepository.getTasks(startInstant, endInstant).first()
            _uiState.update { it.copy(existingTasks = existingTasks) }
        }
    }

    fun addTask() {
        _uiState.update { state ->
            if (state.tasks.size >= 10) return@update state
            state.copy(
                tasks = state.tasks + PlanningTask(id = UUID.randomUUID().toString())
            )
        }
    }

    fun removeTask(taskId: String) {
        _uiState.update { state ->
            state.copy(tasks = state.tasks.filter { it.id != taskId })
        }
    }

    fun updateTaskTitle(taskId: String, title: String) {
        _uiState.update { state ->
            state.copy(
                tasks = state.tasks.map {
                    if (it.id == taskId) it.copy(title = title) else it
                }
            )
        }
    }

    fun assignTaskToDay(taskId: String, day: LocalDate) {
        _uiState.update { state ->
            state.copy(
                tasks = state.tasks.map {
                    if (it.id == taskId) {
                        it.copy(assignedDay = if (it.assignedDay == day) null else day)
                    } else it
                }
            )
        }
    }

    fun nextStep() {
        _uiState.update { state ->
            when (state.step) {
                PlanningStep.ADD_TASKS -> {
                    val tasksWithText = state.tasks.filter { it.title.isNotBlank() }
                    if (tasksWithText.isEmpty()) return@update state
                    state.copy(step = PlanningStep.ASSIGN_DAYS, tasks = tasksWithText)
                }
                PlanningStep.ASSIGN_DAYS -> state.copy(step = PlanningStep.CONFIRM)
                PlanningStep.CONFIRM -> state
            }
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            when (state.step) {
                PlanningStep.ADD_TASKS -> state
                PlanningStep.ASSIGN_DAYS -> state.copy(step = PlanningStep.ADD_TASKS)
                PlanningStep.CONFIRM -> state.copy(step = PlanningStep.ASSIGN_DAYS)
            }
        }
    }

    fun savePlan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            val state = _uiState.value
            val defaultCalendarId = getDefaultCalendarId()

            val tasksToSave = state.tasks.filter { it.title.isNotBlank() }

            var allSuccess = true
            for (task in tasksToSave) {
                val assignedDay = task.assignedDay ?: state.weekStart
                val startOfDay = assignedDay.atStartOfDay(zoneId).toInstant()

                val event = Event(
                    uid = UUID.randomUUID().toString(),
                    calendarId = defaultCalendarId,
                    summary = task.title,
                    dtStart = startOfDay,
                    dtEnd = startOfDay,
                    allDay = true,
                    eventType = "TASK",
                    taskCompleted = false,
                    syncStatus = SyncStatus.PENDING_CREATE
                )

                val result = eventRepository.createEvent(event, sendInvites = false)
                if (result.isFailure) allSuccess = false
            }

            if (allSuccess) {
                updateStreak()
                _uiState.update { it.copy(isSaving = false, isComplete = true) }
            } else {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun updateStreak() {
        val weekStart = _uiState.value.weekStart
        val weekNumber = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())
        val year = weekStart.get(WeekFields.ISO.weekBasedYear())
        val currentWeekYear = year * 100 + weekNumber

        val lastWeekYear = appPreferences.planningLastWeekYear

        // Calculate expected previous week encoding
        val previousWeek = weekStart.minusWeeks(1)
        val prevWeekNumber = previousWeek.get(WeekFields.ISO.weekOfWeekBasedYear())
        val prevYear = previousWeek.get(WeekFields.ISO.weekBasedYear())
        val expectedPrevWeekYear = prevYear * 100 + prevWeekNumber

        val newStreak = if (lastWeekYear == expectedPrevWeekYear) {
            appPreferences.planningStreak + 1
        } else if (lastWeekYear == currentWeekYear) {
            // Already planned this week, keep current streak
            appPreferences.planningStreak
        } else {
            1
        }

        appPreferences.planningStreak = newStreak
        appPreferences.planningLastWeekYear = currentWeekYear
        _uiState.update { it.copy(planningStreak = newStreak) }
    }

    private suspend fun getDefaultCalendarId(): String {
        val calendars = calendarRepository.getCalendars().first()
        return calendars.firstOrNull { !it.readOnly }?.id
            ?: calendars.firstOrNull()?.id
            ?: "default"
    }
}

data class PlanningTask(
    val id: String,
    val title: String = "",
    val assignedDay: LocalDate? = null
)

enum class PlanningStep {
    ADD_TASKS,
    ASSIGN_DAYS,
    CONFIRM
}

data class WeekPlanningUiState(
    val step: PlanningStep = PlanningStep.ADD_TASKS,
    val tasks: List<PlanningTask> = emptyList(),
    val weekStart: LocalDate = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
    val isSaving: Boolean = false,
    val isComplete: Boolean = false,
    val planningStreak: Int = 0,
    val existingTasks: List<Event> = emptyList()
) {
    val weekDays: List<LocalDate>
        get() = (0..6).map { weekStart.plusDays(it.toLong()) }

    val weekNumber: Int
        get() = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())

    val tasksByDay: Map<LocalDate, List<PlanningTask>>
        get() = tasks.filter { it.assignedDay != null }.groupBy { it.assignedDay!! }

    val hasTasksWithText: Boolean
        get() = tasks.any { it.title.isNotBlank() }

    val allTasksAssigned: Boolean
        get() = tasks.filter { it.title.isNotBlank() }.all { it.assignedDay != null }
}
