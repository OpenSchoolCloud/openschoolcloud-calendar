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
package nl.openschoolcloud.calendar.presentation.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.openschoolcloud.calendar.data.local.AppPreferences
import nl.openschoolcloud.calendar.data.remote.caldav.CalDavException
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject

/**
 * ViewModel for the login screen
 *
 * Handles login form state, validation, and CalDAV account discovery
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Update the server URL field
     */
    fun onServerUrlChange(url: String) {
        _uiState.update { it.copy(serverUrl = url, error = null) }
    }

    /**
     * Update the username field
     */
    fun onUsernameChange(username: String) {
        _uiState.update { it.copy(username = username, error = null) }
    }

    /**
     * Update the password field
     */
    fun onPasswordChange(password: String) {
        _uiState.update { it.copy(password = password, error = null) }
    }

    /**
     * Attempt to connect and add the account
     */
    fun connect() {
        val state = _uiState.value

        // Validate input
        if (state.serverUrl.isBlank()) {
            _uiState.update { it.copy(error = LoginError.INVALID_URL) }
            return
        }
        if (state.username.isBlank()) {
            _uiState.update { it.copy(error = LoginError.EMPTY_USERNAME) }
            return
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = LoginError.EMPTY_PASSWORD) }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                // Do not log server URL or username — both are PII (user identity
                // on the Nextcloud instance), and the URL can identify a school.
                Log.d("LoginViewModel", "Starting CalDAV discovery")
                val result = accountRepository.addAccount(
                    serverUrl = state.serverUrl,
                    username = state.username,
                    password = state.password
                )

                result.fold(
                    onSuccess = { account ->
                        appPreferences.isStandaloneMode = false
                        // Linking a Nextcloud account is a completed mode-selection
                        // exit from SyncChoice too (see SyncChoiceViewModel.choose()
                        // for the LOCAL/OSC counterpart) — without this, own-Nextcloud
                        // users loop back to SyncChoice on every cold start.
                        appPreferences.hasCompletedModeSelection = true
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                isSuccess = true,
                                accountId = account.id
                            )
                        }
                    },
                    onFailure = { error ->
                        val loginError = mapErrorToLoginError(error)
                        _uiState.update { it.copy(isLoading = false, error = loginError) }
                    }
                )
            } catch (e: Exception) {
                Log.w("LoginViewModel", "Unexpected error during connect (${e.javaClass.simpleName})")
                _uiState.update { it.copy(isLoading = false, error = LoginError.SERVER) }
            }
        }
    }

    /**
     * Map exceptions to user-friendly error types.
     *
     * Prefer exception type and HTTP status code over message-string matching:
     * messages are localised by the OS or server and brittle to changes.
     * The string-matching branch remains as a final fallback for wrapped
     * exceptions where we can't introspect the cause.
     */
    private fun mapErrorToLoginError(error: Throwable): LoginError {
        // Avoid logging the full URL/username; just the type and HTTP code.
        Log.w("LoginViewModel", "Login failed (${error.javaClass.simpleName})")

        // 1. CalDAV-specific failures: use HTTP status code where available.
        if (error is CalDavException) {
            when (error.httpCode) {
                401, 403 -> return LoginError.AUTH_FAILED
                in 500..599 -> return LoginError.SERVER
            }
            // Treat "no calendars" as its own bucket so the UI can hint.
            if (error.message?.contains("No calendars", ignoreCase = true) == true) {
                return LoginError.NO_CALENDARS
            }
        }

        // 2. Network-layer failures by exception type.
        when (error) {
            is UnknownHostException, is SocketTimeoutException, is IOException ->
                return LoginError.NETWORK
        }

        // 3. Same checks against the cause chain (CalDavException wraps IO errors).
        var cause: Throwable? = error.cause
        while (cause != null) {
            when (cause) {
                is UnknownHostException, is SocketTimeoutException, is IOException ->
                    return LoginError.NETWORK
            }
            cause = cause.cause
        }

        // 4. Last-resort message scan. Kept for "No calendars" raised as plain Exception.
        val msg = error.message.orEmpty()
        if (msg.contains("No calendars", ignoreCase = true)) return LoginError.NO_CALENDARS
        return LoginError.SERVER
    }
}

/**
 * UI state for the login screen
 */
data class LoginUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val error: LoginError? = null,
    val isSuccess: Boolean = false,
    val accountId: String? = null
)

/**
 * Login error types
 */
enum class LoginError {
    INVALID_URL,
    EMPTY_USERNAME,
    EMPTY_PASSWORD,
    AUTH_FAILED,
    NETWORK,
    SERVER,
    NO_CALENDARS
}
