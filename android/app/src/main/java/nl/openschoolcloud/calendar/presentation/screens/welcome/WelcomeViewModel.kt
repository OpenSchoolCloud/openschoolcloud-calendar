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
package nl.openschoolcloud.calendar.presentation.screens.welcome

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.remote.auth.AuthManager
import javax.inject.Inject

data class WelcomeUiState(
    val loading: Boolean = false,
    val authenticated: Boolean = false,
    val error: Boolean = false
)

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val authManager: AuthManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    fun buildAuthIntent(): Intent = authManager.buildAuthRequestIntent()

    fun onAuthResult(data: Intent) {
        _uiState.update { it.copy(loading = true, error = false) }
        viewModelScope.launch {
            val result = authManager.handleAuthResponse(data)
            _uiState.update {
                if (result.isSuccess) it.copy(loading = false, authenticated = true)
                else it.copy(loading = false, error = true)
            }
        }
    }
}
