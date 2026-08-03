package com.vedx.vedxsuper.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repository: AuthRepository,
    private val tokenManager: SecureTokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    val tokenState = tokenManager.tokenState

    fun getSavedCredentials() = tokenManager.getCredentials()

    fun login(clientId: String, password: String, totpKey: String, apiKey: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            val result = repository.login(clientId, password, totpKey, apiKey)
            result.onSuccess {
                _uiState.value = LoginUiState.Success
            }.onFailure {
                _uiState.value = LoginUiState.Error(it.message ?: "Unknown error")
            }
        }
    }

    /**
     * [FIXED] Point 7: Handles silent login and provides feedback on failure.
     */
    fun trySilentLogin() {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            val result = repository.silentLogin()
            result.onSuccess {
                _uiState.value = LoginUiState.Success
            }.onFailure {
                _uiState.value = LoginUiState.Error("Session Expired: Please login manually. (${it.message})")
            }
        }
    }

    sealed class LoginUiState {
        object Idle : LoginUiState()
        object Loading : LoginUiState()
        object Success : LoginUiState()
        data class Error(val message: String) : LoginUiState()
    }
}
