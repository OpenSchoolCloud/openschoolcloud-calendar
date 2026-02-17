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
package nl.openschoolcloud.calendar.presentation.screens.feedback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nl.openschoolcloud.calendar.BuildConfig
import javax.inject.Inject

enum class FeedbackCategory(val label: String, val emoji: String) {
    BUG("Bug melden", "\uD83D\uDC1B"),
    FEATURE("Idee / Verzoek", "\uD83D\uDCA1"),
    UX("Gebruikservaring", "\uD83C\uDFA8"),
    OTHER("Overig", "\uD83D\uDCEC")
}

data class FeedbackUiState(
    val category: FeedbackCategory? = null,
    val message: String = "",
    val isSending: Boolean = false,
    val isSent: Boolean = false
) {
    val canSend: Boolean
        get() = category != null && message.isNotBlank()
}

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FeedbackUiState())
    val uiState: StateFlow<FeedbackUiState> = _uiState.asStateFlow()

    fun selectCategory(category: FeedbackCategory) {
        _uiState.update { it.copy(category = category) }
    }

    fun updateMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun sendFeedback(): Intent? {
        val state = _uiState.value
        val category = state.category ?: return null
        if (state.message.isBlank()) return null

        val subject = "[OSC Calendar ${BuildConfig.VERSION_NAME}] ${category.emoji} ${category.label}"
        val body = buildString {
            appendLine("[CATEGORIE] ${category.label}")
            appendLine("[VERSIE] ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("[BERICHT]")
            appendLine(state.message)
            appendLine()
            appendLine("---")
            appendLine("Verstuurd vanuit OSC Calendar app")
        }

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("feedback@openschoolcloud.nl"))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        _uiState.update { it.copy(isSent = true) }
        return intent
    }

    fun reset() {
        _uiState.value = FeedbackUiState()
    }
}
