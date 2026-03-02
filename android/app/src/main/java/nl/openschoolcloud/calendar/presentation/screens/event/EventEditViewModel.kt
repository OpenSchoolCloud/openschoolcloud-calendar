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
package nl.openschoolcloud.calendar.presentation.screens.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.domain.model.Attendee
import nl.openschoolcloud.calendar.domain.model.AttendeeRole
import nl.openschoolcloud.calendar.domain.model.AttendeeStatus
import nl.openschoolcloud.calendar.domain.model.Calendar
import nl.openschoolcloud.calendar.domain.model.Contact
import nl.openschoolcloud.calendar.domain.model.Event
import nl.openschoolcloud.calendar.domain.model.SyncStatus
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import nl.openschoolcloud.calendar.domain.repository.CalendarRepository
import nl.openschoolcloud.calendar.domain.repository.ContactRepository
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
    private val calendarRepository: CalendarRepository,
    private val contactRepository: ContactRepository,
    private val accountRepository: AccountRepository
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

                // Load organizer email from default account
                try {
                    val account = accountRepository.getDefaultAccount()
                    account?.email?.let { email ->
                        _uiState.update { it.copy(organizerEmail = email) }
                    }
                } catch (_: Exception) {}
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
                            originalEvent = event,
                            isLearningAgenda = event.isLearningAgenda,
                            learningGoal = event.learningGoal ?: "",
                            learningNeeds = event.learningNeeds ?: "",
                            attendees = event.attendees
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

    fun onLearningAgendaToggle(enabled: Boolean) {
        _uiState.update { it.copy(isLearningAgenda = enabled, hasChanges = true) }
    }

    fun onLearningGoalChange(goal: String) {
        _uiState.update { it.copy(learningGoal = goal, hasChanges = true) }
    }

    fun onLearningNeedsChange(needs: String) {
        _uiState.update { it.copy(learningNeeds = needs, hasChanges = true) }
    }

    private var searchJob: Job? = null

    fun onAttendeeSearchChange(query: String) {
        _uiState.update { it.copy(attendeeSearchQuery = query) }

        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(contactSuggestions = emptyList(), isSearchingContacts = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _uiState.update { it.copy(isSearchingContacts = true) }
            try {
                val contacts = contactRepository.searchContactsSync(query)
                // Filter out contacts already added as attendees
                val existingEmails = _uiState.value.attendees.map { it.email }.toSet()
                val filtered = contacts.filter { contact ->
                    contact.email != null && contact.email !in existingEmails
                }
                _uiState.update { it.copy(contactSuggestions = filtered, isSearchingContacts = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(contactSuggestions = emptyList(), isSearchingContacts = false) }
            }
        }
    }

    fun addAttendeeFromContact(contact: Contact) {
        val email = contact.email ?: return
        val attendee = Attendee(
            email = email,
            name = contact.displayName,
            role = AttendeeRole.REQ_PARTICIPANT,
            status = AttendeeStatus.NEEDS_ACTION
        )
        _uiState.update {
            it.copy(
                attendees = it.attendees + attendee,
                attendeeSearchQuery = "",
                contactSuggestions = emptyList(),
                hasChanges = true
            )
        }
    }

    fun addAttendeeByEmail(email: String) {
        if (!email.contains("@") || email.isBlank()) return
        // Avoid duplicates
        if (_uiState.value.attendees.any { it.email == email }) return

        val attendee = Attendee(
            email = email.lowercase().trim(),
            name = null,
            role = AttendeeRole.REQ_PARTICIPANT,
            status = AttendeeStatus.NEEDS_ACTION
        )
        _uiState.update {
            it.copy(
                attendees = it.attendees + attendee,
                attendeeSearchQuery = "",
                contactSuggestions = emptyList(),
                hasChanges = true
            )
        }
    }

    fun removeAttendee(attendee: Attendee) {
        _uiState.update {
            it.copy(
                attendees = it.attendees.filter { a -> a.email != attendee.email },
                hasChanges = true
            )
        }
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
                val description = buildDescription(state)
                val updated = state.originalEvent.copy(
                    summary = state.title,
                    location = state.location.ifBlank { null },
                    description = description,
                    dtStart = startInstant,
                    dtEnd = endInstant,
                    allDay = state.allDay,
                    calendarId = calendarId,
                    attendees = state.attendees,
                    syncStatus = SyncStatus.PENDING_UPDATE,
                    lastModified = Instant.now(),
                    isLearningAgenda = state.isLearningAgenda,
                    learningGoal = state.learningGoal.ifBlank { null },
                    learningNeeds = state.learningNeeds.ifBlank { null }
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
                val description = buildDescription(state)
                val event = Event(
                    uid = UUID.randomUUID().toString(),
                    calendarId = calendarId,
                    summary = state.title,
                    location = state.location.ifBlank { null },
                    description = description,
                    dtStart = startInstant,
                    dtEnd = endInstant,
                    allDay = state.allDay,
                    attendees = state.attendees,
                    syncStatus = SyncStatus.PENDING_CREATE,
                    created = Instant.now(),
                    isLearningAgenda = state.isLearningAgenda,
                    learningGoal = state.learningGoal.ifBlank { null },
                    learningNeeds = state.learningNeeds.ifBlank { null }
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

    private fun buildDescription(state: EventEditUiState): String? {
        return if (state.isLearningAgenda) {
            val parts = mutableListOf<String>()
            if (state.learningGoal.isNotBlank()) {
                parts.add("Wat ga ik doen: ${state.learningGoal}")
            }
            if (state.learningNeeds.isNotBlank()) {
                parts.add("Wat heb ik nodig: ${state.learningNeeds}")
            }
            parts.joinToString("\n\n").ifBlank { null }
        } else {
            state.description.ifBlank { null }
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
    val error: String? = null,
    val isLearningAgenda: Boolean = false,
    val learningGoal: String = "",
    val learningNeeds: String = "",
    val attendees: List<Attendee> = emptyList(),
    val contactSuggestions: List<Contact> = emptyList(),
    val attendeeSearchQuery: String = "",
    val isSearchingContacts: Boolean = false,
    val organizerEmail: String? = null
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
