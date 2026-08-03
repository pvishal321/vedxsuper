package com.vedx.vedxsuper.broker

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.vedx.vedxsuper.model.auth.TokenState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class SecureTokenManager(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "vedx_super_secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _tokenState = MutableStateFlow(TokenState.NO_SESSION)
    val tokenState = _tokenState.asStateFlow()

    init {
        updateInternalState()
    }

    private fun updateInternalState() {
        _tokenState.value = when {
            getJwtToken().isNullOrBlank() -> TokenState.NO_SESSION
            isExpired() -> TokenState.EXPIRED
            else -> TokenState.VALID
        }
    }

    fun saveTokens(jwt: String, refresh: String, feed: String) {
        val jwtExpiry = getExpiryFromJwt(jwt)
        val expiryTime = if (jwtExpiry > 0) jwtExpiry else System.currentTimeMillis() + (12 * 60 * 60 * 1000)
        
        prefs.edit().apply {
            putString("jwt_token", jwt)
            putString("refresh_token", refresh)
            putString("feed_token", feed)
            putLong("token_expiry", expiryTime)
            apply()
        }
        updateInternalState()
    }

    private fun getExpiryFromJwt(jwt: String): Long {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return 0
            val payload = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING))
            val json = JSONObject(payload)
            json.getLong("exp") * 1000
        } catch (e: Exception) {
            0
        }
    }

    fun getJwtToken(): String? = prefs.getString("jwt_token", null)
    fun getFeedToken(): String? = prefs.getString("feed_token", null)
    fun getRefreshToken(): String? = prefs.getString("refresh_token", null)

    fun isExpired(): Boolean {
        val jwt = getJwtToken()
        val expiry = prefs.getLong("token_expiry", 0)
        return jwt.isNullOrEmpty() || System.currentTimeMillis() > (expiry - 5 * 60 * 1000)
    }

    fun hasValidSession(): Boolean {
        return !isExpired() && !getJwtToken().isNullOrBlank() && !getFeedToken().isNullOrBlank()
    }

    fun canAttemptRefresh(): Boolean {
        return !getRefreshToken().isNullOrBlank() && !getCredentials()["api_key"].isNullOrBlank()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        updateInternalState()
    }

    fun saveCredentials(clientId: String, apiKey: String, password: String, totpKey: String) {
        prefs.edit()
            .putString("client_id", clientId)
            .putString("api_key", apiKey)
            .putString("password", password)
            .putString("totp_key", totpKey)
            .apply()
    }

    fun getCredentials(): Map<String, String?> {
        return mapOf(
            "client_id" to prefs.getString("client_id", null),
            "api_key" to prefs.getString("api_key", null),
            "password" to prefs.getString("password", null),
            "totp_key" to prefs.getString("totp_key", null)
        )
    }

    fun getTokens(): Map<String, String?> {
        return mapOf(
            "jwt" to getJwtToken(),
            "feed" to getFeedToken(),
            "refresh" to getRefreshToken()
        )
    }
}
