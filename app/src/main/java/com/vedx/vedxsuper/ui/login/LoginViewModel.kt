package com.vedx.vedxsuper.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.api.AngelClient
import com.vedx.vedxsuper.auth.AutoLoginManager
import com.vedx.vedxsuper.auth.AutoLoginResult
import com.vedx.vedxsuper.broker.SecureTokenManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val autoLoginEnabled: Boolean = false,
    val biometricEnabled: Boolean = false
)

class LoginViewModel(
    private val angelClient: AngelClient,
    private val tokenManager: SecureTokenManager,
    private val autoLoginManager: AutoLoginManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        _uiState.value = _uiState.value.copy(
            autoLoginEnabled = tokenManager.isAutoLoginEnabled(),
            biometricEnabled = tokenManager.isBiometricEnabled()
        )
    }

    fun login(clientCode: String, password: String, totp: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val success = angelClient.login(clientCode, password, totp)
                if (success) {
                    // Save tokens securely
                    tokenManager.saveTokens(
                        jwtToken = angelClient.token,
                        refreshToken = "", // Angel One doesn't provide refresh token in basic API
                        clientCode = clientCode
                    )
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Invalid credentials or TOTP"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun attemptAutoLogin() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = autoLoginManager.attemptAutoLogin()) {
                is AutoLoginResult.Success -> {
                    _uiState.value = _uiState.value.copy(isLoading = false, isLoggedIn = true)
                }
                is AutoLoginResult.RequiresLogin -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = false,
                        error = null // Don't show error, just show login screen
                    )
                }
                is AutoLoginResult.Error -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = result.exception.message
                    )
                }
            }
        }
    }

    fun setAutoLoginEnabled(enabled: Boolean) {
        autoLoginManager.enableAutoLogin(enabled)
        _uiState.value = _uiState.value.copy(autoLoginEnabled = enabled)
    }

    fun logout() {
        tokenManager.clearSession()
        autoLoginManager.enableAutoLogin(false)
        _uiState.value = LoginUiState()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
