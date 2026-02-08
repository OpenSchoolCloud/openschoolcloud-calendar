package nl.openschoolcloud.calendar.presentation.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val accountRepository: AccountRepository
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
