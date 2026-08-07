package com.vedx.vedxsuper.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vedx.vedxsuper.auth.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ============================================================
 * LOGIN VIEWMODEL V2 — UNIFIED AUTH STATE
 * ============================================================
 * 
 * Fixes:
 * 1. Single AuthState source — from AutoLoginManager
 * 2. No duplicate login state
 * 3. Specific error messages per exception type
 * 4. Loading states properly managed
 * 5. Biometric auth integrated properly
 * 6. No sensitive data in logs
 */

class LoginViewModelV2(
    private val authRepository: AuthRepository
) : ViewModel() {

    // ===== SINGLE SOURCE OF TRUTH =====
    val authState: StateFlow<AuthState> = authRepository.authState

    fun getPrefillData(): SecureTokenManagerV2.StoredTokens? {
        return authRepository.getCurrentTokens()
    }

    // UI-specific states derived from authState
    val isLoading: StateFlow<Boolean> = authState.map { state ->
        state is AuthState.Checking || state is AuthState.Refreshing
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val isLoggedIn: StateFlow<Boolean> = authState.map { state ->
        state is AuthState.Authenticated
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val errorMessage: StateFlow<String?> = authState.map { state ->
        when (state) {
            is AuthState.RequiresLogin -> state.reason
            is AuthState.Error -> state.message
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), null)

    val canRetry: StateFlow<Boolean> = authState.map { state ->
        when (state) {
            is AuthState.RequiresLogin -> state.canRetry
            is AuthState.Error -> state.isRecoverable
            else -> false
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    // ===== LOGIN =====
    fun login(clientCode: String, password: String, totp: String, apiKey: String) {
        viewModelScope.launch {
            authRepository.login(clientCode, password, totp, apiKey)
        }
    }

    // ===== AUTO LOGIN / RETRY =====
    fun retryAuth() {
        viewModelScope.launch {
            authRepository.refreshToken()
        }
    }

    // ===== LOGOUT =====
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    // ===== BIOMETRIC =====
    fun onBiometricSuccess() {
        // Biometric just unlocks the app — tokens already validated
        // AuthState remains as is
    }

    fun onBiometricFailed() {
        // Require manual login
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    // ===== NAVIGATION DECISION =====
    fun getNavigationRoute(): String {
        return when (val state = authState.value) {
            is AuthState.Authenticated -> "home"
            is AuthState.RequiresLogin -> "login"
            is AuthState.Error -> if (state.isRecoverable) "login" else "login"
            else -> "splash"
        }
    }
}
