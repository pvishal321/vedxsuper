package com.vedx.vedxsuper.auth

/**
 * ============================================================
 * AUTH REPOSITORY — SINGLE ENTRY POINT
 * ============================================================
 * 
 * All auth operations go through here.
 * No direct access to SecureTokenManager or AutoLoginManager from UI.
 */

class AuthRepository(
    private val autoLoginManager: AutoLoginManagerV2,
    private val tokenManager: SecureTokenManagerV2
) {
    val authState = autoLoginManager.authState

    suspend fun login(clientCode: String, password: String, totp: String, apiKey: String) =
        autoLoginManager.login(clientCode, password, totp, apiKey)

    suspend fun logout() = autoLoginManager.logout()

    suspend fun refreshToken() = autoLoginManager.attemptTokenRefresh()

    fun getCurrentTokens() = tokenManager.getStoredTokens()

    fun isSessionValid() = tokenManager.hasValidSession()
}
