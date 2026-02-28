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
import nl.openschoolcloud.calendar.domain.repository.AccountRepository
import android.util.Log
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
                Log.d("LoginViewModel", "Connecting to ${state.serverUrl} as ${state.username}")
                val result = accountRepository.addAccount(
                    serverUrl = state.serverUrl,
                    username = state.username,
                    password = state.password
                )

                result.fold(
                    onSuccess = { account ->
                        Log.d("LoginViewModel", "Login success, accountId=${account.id}")
                        // Clear standalone mode when connecting Nextcloud
                        appPreferences.isStandaloneMode = false
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
                Log.e("LoginViewModel", "Unexpected error during connect", e)
                _uiState.update { it.copy(isLoading = false, error = LoginError.SERVER) }
            }
        }
    }

    /**
     * Map exceptions to user-friendly error types
     */
    private fun mapErrorToLoginError(error: Throwable): LoginError {
        Log.e("LoginViewModel", "Login error: ${error.message}", error)
        return when {
            error.message?.contains("401") == true -> LoginError.AUTH_FAILED
            error.message?.contains("No calendars") == true -> LoginError.NO_CALENDARS
            error is UnknownHostException -> LoginError.NETWORK
            error is SocketTimeoutException -> LoginError.NETWORK
            error.message?.contains("Unable to resolve host") == true -> LoginError.NETWORK
            error.message?.contains("timeout") == true -> LoginError.NETWORK
            error.message?.contains("Connection refused") == true -> LoginError.NETWORK
            error.message?.contains("Could not find principal") == true -> LoginError.SERVER
            error.message?.contains("Could not find calendar") == true -> LoginError.SERVER
            else -> LoginError.SERVER
        }
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
