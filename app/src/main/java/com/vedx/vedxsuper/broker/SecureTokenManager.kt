package com.vedx.vedxsuper.broker

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.concurrent.TimeUnit

class SecureTokenManager(context: Context) {

    companion object {
        private const val PREFS_FILE = "vedx_secure_tokens"
        private const val KEY_JWT_TOKEN = "jwt_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_CLIENT_CODE = "client_code"
        private const val KEY_LOGIN_TIMESTAMP = "login_timestamp"
        private const val KEY_AUTO_LOGIN_ENABLED = "auto_login_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_FEED_TOKEN = "feed_token"
        private const val SESSION_VALIDITY_HOURS = 24L
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(jwtToken: String, refreshToken: String, clientCode: String, feedToken: String = "") {
        encryptedPrefs.edit {
            putString(KEY_JWT_TOKEN, jwtToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_CLIENT_CODE, clientCode)
            putString(KEY_FEED_TOKEN, feedToken)
            putLong(KEY_LOGIN_TIMESTAMP, System.currentTimeMillis())
        }
    }

    fun getJwtToken(): String? = encryptedPrefs.getString(KEY_JWT_TOKEN, null)
    fun getRefreshToken(): String? = encryptedPrefs.getString(KEY_REFRESH_TOKEN, null)
    fun getClientCode(): String? = encryptedPrefs.getString(KEY_CLIENT_CODE, null)
    fun getFeedToken(): String? = encryptedPrefs.getString(KEY_FEED_TOKEN, null)

    fun hasValidSession(): Boolean {
        val token = getJwtToken() ?: return false
        val loginTime = encryptedPrefs.getLong(KEY_LOGIN_TIMESTAMP, 0L)
        if (loginTime == 0L) return false
        val elapsedHours = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - loginTime)
        return token.isNotBlank() && elapsedHours < SESSION_VALIDITY_HOURS
    }

    fun isSessionExpiringSoon(): Boolean {
        val loginTime = encryptedPrefs.getLong(KEY_LOGIN_TIMESTAMP, 0L)
        if (loginTime == 0L) return true
        val elapsedHours = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - loginTime)
        return elapsedHours >= (SESSION_VALIDITY_HOURS - 2)
    }

    fun clearSession() {
        encryptedPrefs.edit { clear() }
    }

    fun isAutoLoginEnabled(): Boolean = encryptedPrefs.getBoolean(KEY_AUTO_LOGIN_ENABLED, false)
    fun setAutoLoginEnabled(enabled: Boolean) {
        encryptedPrefs.edit { putBoolean(KEY_AUTO_LOGIN_ENABLED, enabled) }
    }

    fun isBiometricEnabled(): Boolean = encryptedPrefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
    fun setBiometricEnabled(enabled: Boolean) {
        encryptedPrefs.edit { putBoolean(KEY_BIOMETRIC_ENABLED, enabled) }
    }

    // Migrate from old plain prefs
    fun migrateFromLegacyPrefs(context: Context) {
        val legacy = context.getSharedPreferences("v", Context.MODE_PRIVATE)
        val oldToken = legacy.getString("tok", null)
        val oldClientCode = legacy.getString("cc", null)
        if (oldToken != null && getJwtToken() == null) {
            saveTokens(oldToken, "", oldClientCode ?: "")
            legacy.edit { remove("tok"); remove("cc") }
        }
    }
}
