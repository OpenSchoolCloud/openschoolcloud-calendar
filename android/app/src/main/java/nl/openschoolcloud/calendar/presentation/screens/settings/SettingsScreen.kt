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
package nl.openschoolcloud.calendar.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import nl.openschoolcloud.calendar.BuildConfig
import nl.openschoolcloud.calendar.R
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.domain.model.Calendar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    onHolidayDiscoverClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showSyncIntervalDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showReminderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.settings_logout)) },
            text = { Text(stringResource(R.string.settings_logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) {
                    Text(stringResource(R.string.dialog_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.dialog_no))
                }
            }
        )
    }

    if (showSyncIntervalDialog) {
        SyncIntervalDialog(
            currentInterval = uiState.syncIntervalMinutes,
            onIntervalSelected = { minutes ->
                viewModel.setSyncInterval(minutes)
                showSyncIntervalDialog = false
            },
            onDismiss = { showSyncIntervalDialog = false }
        )
    }

    if (showThemeDialog) {
        ThemeModeDialog(
            currentMode = uiState.themeMode,
            onModeSelected = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false }
        )
    }

    if (showReminderDialog) {
        DefaultReminderDialog(
            currentMinutes = uiState.defaultReminderMinutes,
            onMinutesSelected = { minutes ->
                viewModel.setDefaultReminderMinutes(minutes)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.a11y_close)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Account section
            uiState.account?.let { account ->
                SettingsSection(title = stringResource(R.string.settings_account)) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = account.displayName ?: account.username,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = account.serverUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Calendars section
            if (uiState.calendars.isNotEmpty()) {
                SettingsSection(title = stringResource(R.string.settings_calendars)) {
                    uiState.calendars.forEach { calendar ->
                        CalendarRow(
                            calendar = calendar,
                            onToggleVisibility = { viewModel.toggleCalendarVisibility(calendar.id) }
                        )
                    }
                }
            }

            // Sync section
            SettingsSection(title = stringResource(R.string.settings_sync)) {
                // Sync interval
                SettingsRow(
                    title = stringResource(R.string.settings_sync_interval),
                    value = formatSyncInterval(uiState.syncIntervalMinutes),
                    onClick = { showSyncIntervalDialog = true }
                )

                // Sync now
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !uiState.isSyncing) { viewModel.syncNow() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.settings_sync_now),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Last sync time
                uiState.lastSyncTime?.let { lastSync ->
                    Text(
                        text = stringResource(
                            R.string.settings_last_sync,
                            formatTimestamp(lastSync)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                } ?: run {
                    Text(
                        text = stringResource(R.string.settings_never_synced),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }

            // Notifications section
            SettingsSection(title = stringResource(R.string.settings_notifications)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setNotificationsEnabled(!uiState.notificationsEnabled) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_reminders_toggle),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                    )
                }
                if (uiState.notificationsEnabled) {
                    SettingsRow(
                        title = stringResource(R.string.settings_default_reminder),
                        value = formatReminderMinutes(uiState.defaultReminderMinutes),
                        onClick = { showReminderDialog = true }
                    )
                }
            }

            // Learning agenda & Reflection section
            SettingsSection(title = stringResource(R.string.settings_learning_agenda)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setReflectionNotificationsEnabled(!uiState.reflectionNotificationsEnabled) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_reflection_notifications),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.settings_reflection_notifications_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.reflectionNotificationsEnabled,
                        onCheckedChange = { viewModel.setReflectionNotificationsEnabled(it) }
                    )
                }
            }

            // Holiday calendars section
            SettingsSection(title = stringResource(R.string.holiday_settings_section)) {
                SettingsRow(
                    title = stringResource(R.string.holiday_discover_title),
                    value = "",
                    onClick = onHolidayDiscoverClick
                )
            }

            // Display section
            SettingsSection(title = stringResource(R.string.settings_display)) {
                SettingsRow(
                    title = stringResource(R.string.settings_theme),
                    value = formatThemeMode(uiState.themeMode),
                    onClick = { showThemeDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout button
            TextButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.settings_logout),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }

            // Promo card
            if (uiState.showPromo) {
                PromoCard(
                    onDismiss = { viewModel.dismissPromo() },
                    onMoreInfo = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://openschoolcloud.nl")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // About section
            AboutSection(
                onWebsiteClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://openschoolcloud.nl")
                    )
                    context.startActivity(intent)
                },
                onPrivacyClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://openschoolcloud.nl/juridisch/privacy")
                    )
                    context.startActivity(intent)
                },
                onTermsClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://openschoolcloud.nl/juridisch/voorwaarden")
                    )
                    context.startActivity(intent)
                },
                onCookiesClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://openschoolcloud.nl/juridisch/cookies")
                    )
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
private fun PromoCard(
    onDismiss: () -> Unit,
    onMoreInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE8F4FB)
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Color(0xFF3B9FD9),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.promo_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.promo_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onMoreInfo) {
                    Text(stringResource(R.string.promo_button))
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(32.dp)
                    .offset(x = (-4).dp, y = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.a11y_close),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CalendarRow(
    calendar: Calendar,
    onToggleVisibility: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleVisibility)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(calendar.color))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = calendar.displayName,
                style = MaterialTheme.typography.bodyLarge
            )
            if (calendar.readOnly) {
                Text(
                    text = stringResource(R.string.discovery_read_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = calendar.visible,
            onCheckedChange = { onToggleVisibility() }
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SyncIntervalDialog(
    currentInterval: Long,
    onIntervalSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        15L to stringResource(R.string.sync_interval_15_min),
        30L to stringResource(R.string.sync_interval_30_min),
        60L to stringResource(R.string.sync_interval_1_hour),
        240L to stringResource(R.string.sync_interval_4_hours),
        0L to stringResource(R.string.sync_interval_manual)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_sync_interval)) },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onIntervalSelected(minutes) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = minutes == currentInterval,
                            onClick = { onIntervalSelected(minutes) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ThemeModeDialog(
    currentMode: String,
    onModeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        AppPreferences.THEME_SYSTEM to stringResource(R.string.settings_dark_mode_system),
        AppPreferences.THEME_LIGHT to stringResource(R.string.settings_dark_mode_light),
        AppPreferences.THEME_DARK to stringResource(R.string.settings_dark_mode_dark)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme)) },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun AboutSection(
    onWebsiteClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onTermsClick: () -> Unit,
    onCookiesClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.osc_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.width(80.dp),
            contentScale = ContentScale.FillWidth
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(R.string.about_privacy_tagline),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onWebsiteClick) {
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(text = "openschoolcloud.nl")
        }

        TextButton(onClick = onPrivacyClick) {
            Icon(
                Icons.Default.Policy,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(text = stringResource(R.string.settings_privacy_policy))
        }

        TextButton(onClick = onTermsClick) {
            Icon(
                Icons.Default.Gavel,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(text = stringResource(R.string.about_terms))
        }

        TextButton(onClick = onCookiesClick) {
            Icon(
                Icons.Default.Cookie,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp)
            )
            Text(text = stringResource(R.string.about_cookies))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.about_made_with),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DefaultReminderDialog(
    currentMinutes: Int,
    onMinutesSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        0 to stringResource(R.string.reminder_none),
        5 to stringResource(R.string.reminder_5_min),
        10 to stringResource(R.string.reminder_10_min),
        15 to stringResource(R.string.reminder_15_min),
        30 to stringResource(R.string.reminder_30_min),
        60 to stringResource(R.string.reminder_1_hour)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_default_reminder)) },
        text = {
            Column {
                options.forEach { (minutes, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMinutesSelected(minutes) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = minutes == currentMinutes,
                            onClick = { onMinutesSelected(minutes) }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun formatReminderMinutes(minutes: Int): String {
    return when (minutes) {
        0 -> stringResource(R.string.reminder_none)
        5 -> stringResource(R.string.reminder_5_min)
        10 -> stringResource(R.string.reminder_10_min)
        15 -> stringResource(R.string.reminder_15_min)
        30 -> stringResource(R.string.reminder_30_min)
        60 -> stringResource(R.string.reminder_1_hour)
        else -> stringResource(R.string.reminder_15_min)
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun formatSyncInterval(minutes: Long): String {
    return when (minutes) {
        15L -> stringResource(R.string.sync_interval_15_min)
        30L -> stringResource(R.string.sync_interval_30_min)
        60L -> stringResource(R.string.sync_interval_1_hour)
        240L -> stringResource(R.string.sync_interval_4_hours)
        0L -> stringResource(R.string.sync_interval_manual)
        else -> stringResource(R.string.sync_interval_1_hour)
    }
}

@Composable
private fun formatThemeMode(mode: String): String {
    return when (mode) {
        AppPreferences.THEME_SYSTEM -> stringResource(R.string.settings_dark_mode_system)
        AppPreferences.THEME_LIGHT -> stringResource(R.string.settings_dark_mode_light)
        AppPreferences.THEME_DARK -> stringResource(R.string.settings_dark_mode_dark)
        else -> stringResource(R.string.settings_dark_mode_system)
    }
}

private fun formatTimestamp(instant: Instant): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale("nl"))
    return instant.atZone(ZoneId.systemDefault()).format(formatter)
}
