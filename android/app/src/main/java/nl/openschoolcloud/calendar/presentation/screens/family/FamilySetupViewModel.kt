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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.FamilyRepository
import nl.openschoolcloud.calendar.domain.repository.ProfileRepository
import javax.inject.Inject

data class FamilySetupUiState(
    val familyName: String = "",
    val children: List<String> = emptyList(),
    val saving: Boolean = false
)

@HiltViewModel
class FamilySetupViewModel @Inject constructor(
    private val familyRepository: FamilyRepository,
    private val profileRepository: ProfileRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FamilySetupUiState())
    val uiState: StateFlow<FamilySetupUiState> = _uiState.asStateFlow()

    // OSC blue as the owner's default profile color.
    private val ownerColor = 0xFF3B9FD9L

    fun onFamilyNameChange(name: String) = _uiState.update { it.copy(familyName = name) }

    fun addChild(name: String) {
        if (name.isBlank()) return
        _uiState.update { it.copy(children = it.children + name.trim()) }
    }

    fun save(onDone: (String) -> Unit) {
        val state = _uiState.value
        if (state.familyName.isBlank()) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            val ownerId = accountRepository.getDefaultAccountId()
            val family = familyRepository.createFamily(state.familyName.trim(), ownerId)
            profileRepository.addProfile(family.id, state.familyName.trim(), ownerColor, isOwner = true)
            state.children.forEach { child ->
                profileRepository.addProfile(family.id, child, ownerColor, isOwner = false)
            }
            _uiState.update { it.copy(saving = false) }
            onDone(family.id)
        }
    }
}
