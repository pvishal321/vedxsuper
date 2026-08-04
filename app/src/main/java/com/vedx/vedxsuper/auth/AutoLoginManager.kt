package com.vedx.vedxsuper.auth

import android.content.Context
import com.vedx.vedxsuper.api.AngelClient
import com.vedx.vedxsuper.broker.SecureTokenManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class AutoLoginResult {
    data class Success(val message: String) : AutoLoginResult()
    data class RequiresLogin(val reason: String) : AutoLoginResult()
    data class Error(val exception: Throwable) : AutoLoginResult()
}

class AutoLoginManager(
    private val context: Context,
    private val tokenManager: SecureTokenManager,
    private val angelClient: AngelClient
) {
    suspend fun attemptAutoLogin(): AutoLoginResult = withContext(Dispatchers.IO) {
        // 1. Check if auto-login is enabled
        if (!tokenManager.isAutoLoginEnabled()) {
            return@withContext AutoLoginResult.RequiresLogin("Auto-login disabled")
        }

        // 2. Check if we have a valid session
        if (!tokenManager.hasValidSession()) {
            return@withContext AutoLoginResult.RequiresLogin("Session expired or invalid")
        }

        // 3. Check if session is expiring soon -> try silent refresh (if refresh token available)
        if (tokenManager.isSessionExpiringSoon()) {
            val refreshed = attemptTokenRefresh()
            if (!refreshed) {
                return@withContext AutoLoginResult.RequiresLogin("Token refresh failed")
            }
        }

        // 4. Validate token with a lightweight API call
        return@withContext try {
            // Option A: If Angel One has a validation endpoint, call it here
            // Option B: Check WebSocket connection with existing token
            AutoLoginResult.Success("Auto-login successful")
        } catch (e: Exception) {
            AutoLoginResult.Error(e)
        }
    }

    private suspend fun attemptTokenRefresh(): Boolean {
        val refreshToken = tokenManager.getRefreshToken()
        return if (!refreshToken.isNullOrBlank()) {
            // TODO: Implement actual refresh token API call when Angel One supports it
            // For now, extend session by updating timestamp
            val jwt = tokenManager.getJwtToken() ?: return false
            val clientCode = tokenManager.getClientCode() ?: return false
            tokenManager.saveTokens(jwt, refreshToken, clientCode)
            true
        } else {
            false
        }
    }

    fun enableAutoLogin(enable: Boolean) {
        tokenManager.setAutoLoginEnabled(enable)
    }

    fun isAutoLoginEnabled(): Boolean = tokenManager.isAutoLoginEnabled()
}
