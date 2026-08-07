package com.vedx.vedxsuper.auth

import com.vedx.vedxsuper.data.Symbol

/**
 * ============================================================
 * UNIFIED AUTH STATE — Single Source of Truth
 * ============================================================
 * 
 * ALL login state lives here. No duplicate state in:
 * - SecureTokenManager
 * - AutoLoginManager  
 * - BrokerAuthManager
 * - ViewModel
 * 
 * Only ONE StateFlow<AuthState> in the entire app.
 */

sealed class AuthState {
    data object Unknown : AuthState()      // App just started, checking
    data object Checking : AuthState()     // Validating stored tokens
    data object Refreshing : AuthState()   // Token refresh in progress
    data class Authenticated(
        val jwtToken: String,
        val refreshToken: String,
        val feedToken: String,
        val clientCode: String,
        val broker: BrokerType,
        val expiresAt: Long  // JWT exp timestamp (ms)
    ) : AuthState()
    data class RequiresLogin(
        val reason: String = "",
        val canRetry: Boolean = false
    ) : AuthState()
    data class Error(
        val message: String,
        val isRecoverable: Boolean = true,
        val retryCount: Int = 0
    ) : AuthState()
}

enum class BrokerType {
    ANGEL_ONE,
    ZERODHA,
    UPSTOX,
    FYERS
}

sealed class LoginError {
    data object InvalidCredentials : LoginError()
    data object TokenExpired : LoginError()
    data object NetworkError : LoginError()
    data object ServerError : LoginError()
    data object RateLimited : LoginError()
    data object DeviceChanged : LoginError()
    data class Unknown(val message: String) : LoginError()
}
