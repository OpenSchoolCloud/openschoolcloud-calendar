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
package nl.openschoolcloud.calendar.presentation.screens.modeselection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import javax.inject.Inject

@HiltViewModel
class ModeSelectionViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    fun selectStandalone(onComplete: () -> Unit) {
        appPreferences.isStandaloneMode = true
        appPreferences.hasCompletedModeSelection = true
        viewModelScope.launch {
            calendarRepository.ensureLocalCalendarExists()
            onComplete()
        }
    }

    fun selectNextcloud(onComplete: () -> Unit) {
        appPreferences.isStandaloneMode = false
        appPreferences.hasCompletedModeSelection = true
        onComplete()
    }
}
