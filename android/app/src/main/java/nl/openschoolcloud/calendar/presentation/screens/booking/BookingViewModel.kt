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
package nl.openschoolcloud.calendar.presentation.screens.booking

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.domain.model.BookingConfig
import nl.openschoolcloud.calendar.domain.repository.BookingRepository
import javax.inject.Inject

@HiltViewModel
class BookingViewModel @Inject constructor(
    private val bookingRepository: BookingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookingUiState())
    val uiState: StateFlow<BookingUiState> = _uiState.asStateFlow()

    init {
        loadBookingConfigs()
    }

    fun loadBookingConfigs() {
        Log.d(TAG, "Loading booking configs...")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = bookingRepository.getBookingConfigs()
            result.fold(
                onSuccess = { configs ->
                    Log.d(TAG, "Loaded ${configs.size} booking configs")
                    configs.forEach { config ->
                        Log.d(TAG, "  - ${config.name} (token=${config.token}, duration=${config.duration}min, url=${config.bookingUrl})")
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            configs = configs,
                            isSupported = true
                        )
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to load booking configs", error)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val TAG = "BookingViewModel"
    }
}

data class BookingUiState(
    val isLoading: Boolean = false,
    val configs: List<BookingConfig> = emptyList(),
    val isSupported: Boolean = true,
    val error: String? = null
)
