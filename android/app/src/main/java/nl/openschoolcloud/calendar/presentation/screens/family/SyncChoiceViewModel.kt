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
package nl.openschoolcloud.calendar.presentation.screens.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.domain.model.SyncScope
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import nl.openschoolcloud.calendar.util.FeatureFlags
import javax.inject.Inject

@HiltViewModel
class SyncChoiceViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val oscCloudAvailable: Boolean get() = FeatureFlags.oscCloudEnabled

    /** Apply the chosen scope to every profile in the family, then finish. */
    fun choose(scope: SyncScope, familyId: String, onDone: () -> Unit) {
        viewModelScope.launch {
            appPreferences.hasCompletedModeSelection = true
            appPreferences.isStandaloneMode = scope == SyncScope.LOCAL
            profileRepository.observeProfilesOnce(familyId).forEach {
                profileRepository.setSyncScope(it.id, scope)
            }
            onDone()
        }
    }
}
