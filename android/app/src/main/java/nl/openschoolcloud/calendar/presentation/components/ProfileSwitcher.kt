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
package nl.openschoolcloud.calendar.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.domain.model.Profile

/**
 * Horizontal row of filter chips to switch which family profile's events
 * are shown on the calendar. "Hele gezin" (null) always shows everything.
 */
@Composable
fun ProfileSwitcher(
    profiles: List<Profile>,
    selectedProfileId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
        FilterChip(
            selected = selectedProfileId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.profile_all)) },
            modifier = Modifier.padding(end = 8.dp)
        )
        profiles.forEach { profile ->
            FilterChip(
                selected = selectedProfileId == profile.id,
                onClick = { onSelect(profile.id) },
                label = { Text(profile.displayName) },
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
}
