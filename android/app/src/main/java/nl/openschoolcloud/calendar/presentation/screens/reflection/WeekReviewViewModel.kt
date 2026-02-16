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
package nl.openschoolcloud.calendar.presentation.screens.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.domain.model.Event
import nl.openschoolcloud.calendar.domain.model.ReflectionEntry
import nl.openschoolcloud.calendar.domain.repository.EventRepository
import nl.openschoolcloud.calendar.domain.repository.ReflectionRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import javax.inject.Inject

@HiltViewModel
class WeekReviewViewModel @Inject constructor(
    private val reflectionRepository: ReflectionRepository,
    private val eventRepository: EventRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeekReviewUiState())
    val uiState: StateFlow<WeekReviewUiState> = _uiState.asStateFlow()

    init {
        val today = LocalDate.now()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _uiState.update { it.copy(weekStart = weekStart) }
        loadWeek(weekStart)
    }

    fun previousWeek() {
        val newWeekStart = _uiState.value.weekStart.minusWeeks(1)
        _uiState.update { it.copy(weekStart = newWeekStart) }
        loadWeek(newWeekStart)
    }

    fun nextWeek() {
        val newWeekStart = _uiState.value.weekStart.plusWeeks(1)
        _uiState.update { it.copy(weekStart = newWeekStart) }
        loadWeek(newWeekStart)
    }

    private fun loadWeek(weekStart: LocalDate) {
        val zone = ZoneId.systemDefault()
        val start = weekStart.atStartOfDay(zone).toInstant()
        val end = weekStart.plusDays(7).atStartOfDay(zone).toInstant()

        viewModelScope.launch {
            combine(
                reflectionRepository.getReflectionsForWeek(start, end),
                eventRepository.getEvents(start, end)
            ) { reflections, events ->
                val learningEvents = events.filter { it.isLearningAgenda }
                val reflectionsByEventId = reflections.associateBy { it.eventId }

                val dayEntries = (0..6).map { dayOffset ->
                    val date = weekStart.plusDays(dayOffset.toLong())
                    val dayStart = date.atStartOfDay(zone).toInstant()
                    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()

                    val dayLearningEvents = learningEvents.filter { event ->
                        val eventStart = event.dtStart
                        eventStart >= dayStart && eventStart < dayEnd
                    }

                    val dayReflections = dayLearningEvents.mapNotNull { event ->
                        reflectionsByEventId[event.uid]?.let { reflection ->
                            DayReflection(
                                eventTitle = event.summary,
                                mood = reflection.mood
                            )
                        }
                    }

                    DayEntry(
                        date = date,
                        learningEventCount = dayLearningEvents.size,
                        reflections = dayReflections
                    )
                }

                val totalLearningEvents = learningEvents.size
                val totalReflections = reflections.size
                val averageMood = if (reflections.isNotEmpty()) {
                    reflections.map { it.mood }.average().toFloat()
                } else null

                _uiState.update {
                    it.copy(
                        dayEntries = dayEntries,
                        totalLearningEvents = totalLearningEvents,
                        totalReflections = totalReflections,
                        averageMood = averageMood
                    )
                }
            }.collect { /* state updated inside combine */ }
        }
    }
}

data class WeekReviewUiState(
    val weekStart: LocalDate = LocalDate.now(),
    val dayEntries: List<DayEntry> = emptyList(),
    val totalLearningEvents: Int = 0,
    val totalReflections: Int = 0,
    val averageMood: Float? = null
) {
    val weekNumber: Int
        get() = weekStart.get(WeekFields.ISO.weekOfWeekBasedYear())
}

data class DayEntry(
    val date: LocalDate,
    val learningEventCount: Int,
    val reflections: List<DayReflection>
)

data class DayReflection(
    val eventTitle: String,
    val mood: Int
)
