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
package nl.openschoolcloud.calendar.presentation.screens.holidays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.domain.model.HolidayCalendar
import nl.openschoolcloud.calendar.domain.repository.HolidayRepository
import javax.inject.Inject

@HiltViewModel
class HolidayViewModel @Inject constructor(
    private val holidayRepository: HolidayRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HolidayDiscoverUiState())
    val uiState: StateFlow<HolidayDiscoverUiState> = _uiState.asStateFlow()

    init {
        loadCalendars()
    }

    private fun loadCalendars() {
        viewModelScope.launch {
            holidayRepository.getHolidayCalendars()
                .catch { /* non-critical */ }
                .collect { calendars ->
                    _uiState.update { it.copy(calendars = calendars) }
                }
        }
    }

    fun toggleCalendar(id: String, enabled: Boolean) {
        viewModelScope.launch {
            holidayRepository.setCalendarEnabled(id, enabled)
        }
    }
}

data class HolidayDiscoverUiState(
    val calendars: List<HolidayCalendar> = emptyList()
)
