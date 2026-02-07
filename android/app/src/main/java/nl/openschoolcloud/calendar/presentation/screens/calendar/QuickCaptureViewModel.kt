package nl.openschoolcloud.calendar.presentation.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.domain.usecase.ParseNaturalLanguageUseCase
import nl.openschoolcloud.calendar.domain.usecase.ParsedEvent
import javax.inject.Inject

@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val parseNaturalLanguageUseCase: ParseNaturalLanguageUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuickCaptureUiState())
    val uiState: StateFlow<QuickCaptureUiState> = _uiState.asStateFlow()

    private var parseJob: Job? = null

    fun onInputChange(input: String) {
        _uiState.update { it.copy(input = input) }

        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            delay(150) // Debounce 150ms after last keystroke
            if (input.length >= 3) {
                val parsed = parseNaturalLanguageUseCase.parse(input)
                _uiState.update { it.copy(parsedEvent = parsed) }
            } else {
                _uiState.update { it.copy(parsedEvent = null) }
            }
        }
    }

    fun clear() {
        parseJob?.cancel()
        _uiState.update { QuickCaptureUiState() }
    }
}

data class QuickCaptureUiState(
    val input: String = "",
    val parsedEvent: ParsedEvent? = null
)
