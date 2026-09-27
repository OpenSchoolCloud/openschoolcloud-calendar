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
package nl.openschoolcloud.calendar.presentation.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.data.remote.auth.AuthManager
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import nl.openschoolcloud.calendar.presentation.navigation.Route
import nl.openschoolcloud.calendar.presentation.navigation.decideStartRoute
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    authManager: AuthManager,
    familyRepository: FamilyRepository,
    appPreferences: AppPreferences
) : ViewModel() {

    private val _startRoute = MutableStateFlow<String?>(null)
    val startRoute: StateFlow<String?> = _startRoute.asStateFlow()

    init {
        viewModelScope.launch {
            val family = familyRepository.observeFamily().first()
            val isLoggedIn = authManager.isLoggedIn
            val syncChosen = appPreferences.hasCompletedModeSelection
            val decided = decideStartRoute(isLoggedIn, family != null, syncChosen)
            // decideStartRoute returns the raw SyncChoice route template
            // ("sync_choice/{familyId}") to stay pure/testable. Resolve it to the
            // concrete path here using the family id we already loaded, otherwise
            // familyId arrives downstream as the literal string "{familyId}".
            _startRoute.value = if (decided == Route.SyncChoice.route && family != null) {
                Route.SyncChoice.createRoute(family.id)
            } else {
                decided
            }
        }
    }
}
