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
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.data.sync.CalendarSyncWorker
import nl.openschoolcloud.calendar.domain.model.Account
import nl.openschoolcloud.calendar.domain.model.Calendar
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
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
            calendarRepository.getCalendars()
                .catch { /* ignore */ }
                .collect { calendars ->
                    _uiState.update { it.copy(calendars = calendars) }
                }
        }
    }

    private fun loadPreferences() {
        val lastSync = appPreferences.lastSyncTimestamp
        _uiState.update {
            it.copy(
                syncIntervalMinutes = appPreferences.syncIntervalMinutes,
                themeMode = appPreferences.themeMode,
                lastSyncTime = if (lastSync > 0) Instant.ofEpochMilli(lastSync) else null
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
                onSuccess = {
                    val now = Instant.now()
                    appPreferences.lastSyncTimestamp = now.toEpochMilli()
                    _uiState.update {
                        it.copy(isSyncing = false, lastSyncTime = now)
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

    fun toggleCalendarVisibility(calendarId: String) {
        viewModelScope.launch {
            val calendar = calendarRepository.getCalendar(calendarId) ?: return@launch
            calendarRepository.updateCalendar(calendar.copy(visible = !calendar.visible))
        }
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
    val error: String? = null
)
