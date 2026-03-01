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

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.BuildConfig
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.data.sync.CalendarSyncWorker
import nl.openschoolcloud.calendar.domain.model.Account
import nl.openschoolcloud.calendar.domain.model.Calendar
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import nl.openschoolcloud.calendar.notification.PlanningReminderWorker
import nl.openschoolcloud.calendar.notification.ReminderWorker
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val accountRepository: AccountRepository,
    private val calendarRepository: CalendarRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadAccount()
        loadCalendars()
        loadPreferences()
        loadPromoState()
    }

    private fun loadAccount() {
        viewModelScope.launch {
            try {
                val account = accountRepository.getDefaultAccount()
                _uiState.update { it.copy(account = account) }
            } catch (_: Exception) {
                // Account info is non-critical
            }
        }
    }

    private fun loadCalendars() {
        viewModelScope.launch {
            // Use getAllCalendars() so hidden calendars remain visible in Settings
            // and can be toggled back on (getCalendars() only returns visible ones)
            calendarRepository.getAllCalendars()
                .catch { /* ignore */ }
                .collect { calendars ->
                    // Filter out local-only calendars when not in standalone mode
                    // to prevent duplicates (local + Nextcloud showing same-named calendars)
                    val filtered = if (appPreferences.isStandaloneMode) {
                        calendars
                    } else {
                        calendars.filter { it.accountId != "local" }
                    }
                    _uiState.update { it.copy(calendars = filtered) }
                }
        }
    }

    private fun loadPreferences() {
        val lastSync = appPreferences.lastSyncTimestamp
        _uiState.update {
            it.copy(
                syncIntervalMinutes = appPreferences.syncIntervalMinutes,
                themeMode = appPreferences.themeMode,
                lastSyncTime = if (lastSync > 0) Instant.ofEpochMilli(lastSync) else null,
                notificationsEnabled = appPreferences.notificationsEnabled,
                defaultReminderMinutes = appPreferences.defaultReminderMinutes,
                reflectionNotificationsEnabled = appPreferences.reflectionNotificationsEnabled,
                planningNotificationsEnabled = appPreferences.weekPlanningNotificationsEnabled,
                planningDay = appPreferences.planningDayOfWeek,
                isStandaloneMode = appPreferences.isStandaloneMode
            )
        }
    }

    fun setSyncInterval(minutes: Long) {
        appPreferences.syncIntervalMinutes = minutes
        _uiState.update { it.copy(syncIntervalMinutes = minutes) }

        // Reschedule WorkManager
        if (minutes > 0) {
            CalendarSyncWorker.schedulePeriodic(appContext, minutes)
        } else {
            CalendarSyncWorker.cancelAll(appContext)
        }
    }

    fun setThemeMode(mode: String) {
        appPreferences.themeMode = mode
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }

            val result = calendarRepository.syncAll()
            result.fold(
                onSuccess = { results ->
                    val now = Instant.now()
                    appPreferences.lastSyncTimestamp = now.toEpochMilli()

                    val failures = results.filter { !it.success }
                    val errorMsg = if (failures.isNotEmpty()) {
                        "Sync voltooid met ${failures.size} fout(en): " +
                                failures.joinToString("; ") { it.error ?: "onbekend" }
                    } else null

                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncTime = now,
                            error = errorMsg
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isSyncing = false, error = error.message)
                    }
                }
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        appPreferences.notificationsEnabled = enabled
        _uiState.update { it.copy(notificationsEnabled = enabled) }

        if (enabled) {
            ReminderWorker.schedule(appContext)
        } else {
            ReminderWorker.cancel(appContext)
        }
    }

    fun setDefaultReminderMinutes(minutes: Int) {
        appPreferences.defaultReminderMinutes = minutes
        _uiState.update { it.copy(defaultReminderMinutes = minutes) }
    }

    fun setReflectionNotificationsEnabled(enabled: Boolean) {
        appPreferences.reflectionNotificationsEnabled = enabled
        _uiState.update { it.copy(reflectionNotificationsEnabled = enabled) }
    }

    fun setPlanningNotificationsEnabled(enabled: Boolean) {
        appPreferences.weekPlanningNotificationsEnabled = enabled
        _uiState.update { it.copy(planningNotificationsEnabled = enabled) }

        if (enabled) {
            PlanningReminderWorker.schedule(appContext)
        } else {
            PlanningReminderWorker.cancel(appContext)
        }
    }

    fun cyclePlanningDay() {
        val current = appPreferences.planningDayOfWeek
        val next = if (current >= 7) 1 else current + 1
        appPreferences.planningDayOfWeek = next
        _uiState.update { it.copy(planningDay = next) }
    }

    fun toggleCalendarVisibility(calendarId: String) {
        viewModelScope.launch {
            val calendar = calendarRepository.getCalendar(calendarId) ?: return@launch
            calendarRepository.updateCalendar(calendar.copy(visible = !calendar.visible))
        }
    }

    private fun loadPromoState() {
        _uiState.update { it.copy(showPromo = !appPreferences.promoDismissed) }
    }

    fun dismissPromo() {
        appPreferences.promoDismissed = true
        _uiState.update { it.copy(showPromo = false) }
    }

    fun exportDebugLog() {
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingDebugLog = true) }
            try {
                val diagnostics = calendarRepository.getDiagnosticInfo()
                val log = buildString {
                    appendLine("OSC Calendar v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})")
                    appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    appendLine("Standalone: ${appPreferences.isStandaloneMode}")
                    appendLine("Sync interval: ${appPreferences.syncIntervalMinutes} min")
                    appendLine("Last sync: ${appPreferences.lastSyncTimestamp}")
                    appendLine()
                    append(diagnostics)
                }
                _uiState.update { it.copy(isGeneratingDebugLog = false, debugLogText = log) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isGeneratingDebugLog = false,
                        error = "Fout bij genereren debug log: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearDebugLog() {
        _uiState.update { it.copy(debugLogText = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class SettingsUiState(
    val account: Account? = null,
    val calendars: List<Calendar> = emptyList(),
    val syncIntervalMinutes: Long = AppPreferences.DEFAULT_SYNC_INTERVAL,
    val themeMode: String = AppPreferences.THEME_SYSTEM,
    val isSyncing: Boolean = false,
    val lastSyncTime: Instant? = null,
    val notificationsEnabled: Boolean = true,
    val defaultReminderMinutes: Int = AppPreferences.DEFAULT_REMINDER_MINUTES,
    val reflectionNotificationsEnabled: Boolean = true,
    val planningNotificationsEnabled: Boolean = true,
    val planningDay: Int = 1,
    val showPromo: Boolean = false,
    val isStandaloneMode: Boolean = false,
    val isGeneratingDebugLog: Boolean = false,
    val debugLogText: String? = null,
    val error: String? = null
)
