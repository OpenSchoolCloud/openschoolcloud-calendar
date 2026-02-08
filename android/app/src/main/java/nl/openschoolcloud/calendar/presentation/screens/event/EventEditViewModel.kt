package nl.openschoolcloud.calendar.presentation.screens.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.domain.model.Calendar
import nl.openschoolcloud.calendar.domain.model.Event
import nl.openschoolcloud.calendar.domain.model.SyncStatus
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import nl.openschoolcloud.calendar.domain.repository.EventRepository
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EventEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val eventRepository: EventRepository,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val eventId: String? = savedStateHandle["eventId"]
    private val initialDateStr: String? = savedStateHandle["date"]
    private val zoneId = ZoneId.systemDefault()

    val isEditing: Boolean = eventId != null

    private val _uiState = MutableStateFlow(EventEditUiState())
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    init {
        loadCalendars()
        if (isEditing && eventId != null) {
            loadEvent(eventId)
        } else {
            initNewEvent()
        }
    }

    private fun loadCalendars() {
        viewModelScope.launch {
            try {
                val calendars = calendarRepository.getCalendars().first()
                val writableCalendars = calendars.filter { !it.readOnly }
                _uiState.update { it.copy(availableCalendars = writableCalendars) }

                // Set default calendar if not yet set
                if (_uiState.value.selectedCalendarId == null && writableCalendars.isNotEmpty()) {
                    _uiState.update { it.copy(selectedCalendarId = writableCalendars.first().id) }
                }
            } catch (e: Exception) {
                // calendars will remain empty
            }
        }
    }

    private fun loadEvent(eventId: String) {
        _uiState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val event = eventRepository.getEvent(eventId)
                if (event != null) {
                    val startZoned = event.dtStart.atZone(zoneId)
                    val endZoned = event.dtEnd?.atZone(zoneId)

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            title = event.summary,
                            location = event.location ?: "",
                            description = event.description ?: "",
                            startDate = startZoned.toLocalDate(),
                            startTime = startZoned.toLocalTime(),
                            endDate = endZoned?.toLocalDate() ?: startZoned.toLocalDate(),
                            endTime = endZoned?.toLocalTime() ?: startZoned.toLocalTime().plusHours(1),
                            allDay = event.allDay,
                            selectedCalendarId = event.calendarId,
                            originalEvent = event
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Event niet gevonden") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun initNewEvent() {
        val date = try {
            initialDateStr?.let { LocalDate.parse(it) }
        } catch (e: Exception) {
            null
        } ?: LocalDate.now()

        val now = LocalTime.now()
        // Round to next half hour
        val startTime = if (now.minute < 30) {
            LocalTime.of(now.hour, 30)
        } else {
            LocalTime.of((now.hour + 1) % 24, 0)
        }

        _uiState.update {
            it.copy(
                startDate = date,
                endDate = date,
                startTime = startTime,
                endTime = startTime.plusHours(1)
            )
        }
    }

    fun onTitleChange(title: String) {
        _uiState.update { it.copy(title = title, hasChanges = true) }
    }

    fun onLocationChange(location: String) {
        _uiState.update { it.copy(location = location, hasChanges = true) }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description, hasChanges = true) }
    }

    fun onStartDateChange(date: LocalDate) {
        _uiState.update { state ->
            // If end date is before new start date, move it forward
            val endDate = if (state.endDate.isBefore(date)) date else state.endDate
            state.copy(startDate = date, endDate = endDate, hasChanges = true)
        }
    }

    fun onStartTimeChange(time: LocalTime) {
        _uiState.update { state ->
            // Auto-adjust end time to maintain duration
            val oldDuration = java.time.Duration.between(state.startTime, state.endTime)
            val newEndTime = time.plus(if (oldDuration.isNegative) java.time.Duration.ofHours(1) else oldDuration)
            state.copy(startTime = time, endTime = newEndTime, hasChanges = true)
        }
    }

    fun onEndDateChange(date: LocalDate) {
        _uiState.update { it.copy(endDate = date, hasChanges = true) }
    }

    fun onEndTimeChange(time: LocalTime) {
        _uiState.update { it.copy(endTime = time, hasChanges = true) }
    }

    fun onAllDayToggle(allDay: Boolean) {
        _uiState.update { it.copy(allDay = allDay, hasChanges = true) }
    }

    fun onCalendarSelected(calendarId: String) {
        _uiState.update { it.copy(selectedCalendarId = calendarId, hasChanges = true) }
    }

    fun save(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "Titel is verplicht") }
            return
        }

        _uiState.update { it.copy(isSaving = true, error = null) }

        viewModelScope.launch {
            val calendarId = state.selectedCalendarId
                ?: state.availableCalendars.firstOrNull()?.id
                ?: "default"

            val startInstant = state.startDate
                .atTime(if (state.allDay) LocalTime.MIDNIGHT else state.startTime)
                .atZone(zoneId).toInstant()

            val endInstant = state.endDate
                .atTime(if (state.allDay) LocalTime.MIDNIGHT else state.endTime)
                .atZone(zoneId).toInstant()

            if (isEditing && state.originalEvent != null) {
                val updated = state.originalEvent.copy(
                    summary = state.title,
                    location = state.location.ifBlank { null },
                    description = state.description.ifBlank { null },
                    dtStart = startInstant,
                    dtEnd = endInstant,
                    allDay = state.allDay,
                    calendarId = calendarId,
                    syncStatus = SyncStatus.PENDING_UPDATE,
                    lastModified = Instant.now()
                )

                val result = eventRepository.updateEvent(updated)
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isSaving = false) }
                        onSuccess()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(isSaving = false, error = "Opslaan mislukt: ${error.message}")
                        }
                    }
                )
            } else {
                val event = Event(
                    uid = UUID.randomUUID().toString(),
                    calendarId = calendarId,
                    summary = state.title,
                    location = state.location.ifBlank { null },
                    description = state.description.ifBlank { null },
                    dtStart = startInstant,
                    dtEnd = endInstant,
                    allDay = state.allDay,
                    syncStatus = SyncStatus.PENDING_CREATE,
                    created = Instant.now()
                )

                val result = eventRepository.createEvent(event)
                result.fold(
                    onSuccess = {
                        _uiState.update { it.copy(isSaving = false) }
                        onSuccess()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(isSaving = false, error = "Aanmaken mislukt: ${error.message}")
                        }
                    }
                )
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}

data class EventEditUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val title: String = "",
    val location: String = "",
    val description: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endDate: LocalDate = LocalDate.now(),
    val endTime: LocalTime = LocalTime.of(10, 0),
    val allDay: Boolean = false,
    val selectedCalendarId: String? = null,
    val availableCalendars: List<Calendar> = emptyList(),
    val hasChanges: Boolean = false,
    val originalEvent: Event? = null,
    val error: String? = null
) {
    fun formatStartDate(): String =
        startDate.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale("nl")))

    fun formatEndDate(): String =
        endDate.format(DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale("nl")))

    fun formatStartTime(): String =
        startTime.format(DateTimeFormatter.ofPattern("HH:mm"))

    fun formatEndTime(): String =
        endTime.format(DateTimeFormatter.ofPattern("HH:mm"))
}
