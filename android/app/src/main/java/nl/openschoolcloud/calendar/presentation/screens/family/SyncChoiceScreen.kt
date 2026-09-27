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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.domain.model.SyncScope

@Composable
fun SyncChoiceScreen(
    familyId: String,
    onLocalOrOsc: () -> Unit,
    onConnectNextcloud: () -> Unit,
    viewModel: SyncChoiceViewModel = hiltViewModel()
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.sync_choice_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(24.dp))

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.sync_local_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sync_local_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { viewModel.choose(SyncScope.LOCAL, familyId, onLocalOrOsc) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.sync_local_button)) }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.sync_nextcloud_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sync_nextcloud_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = onConnectNextcloud, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sync_nextcloud_button))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.sync_osc_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sync_osc_desc), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                if (viewModel.oscCloudAvailable) {
                    Button(
                        onClick = { viewModel.choose(SyncScope.OSC_CLOUD, familyId, onLocalOrOsc) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sync_osc_button)) }
                } else {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.sync_osc_soon))
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
