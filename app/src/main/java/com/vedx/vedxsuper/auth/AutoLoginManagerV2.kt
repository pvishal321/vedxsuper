package com.vedx.vedxsuper.auth

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.provider.Settings
import com.vedx.vedxsuper.utils.VedxLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.*
import retrofit2.Response
import retrofit2.http.*

/**
 * ============================================================
 * AUTO LOGIN MANAGER V2 — PRODUCTION GRADE
 * ============================================================
 * 
 * Audit 1 Fixes:
 * 1. Refresh Scheduler: 5 min before buffer (in TokenManager) + Monitor
 * 2. Session Monitor: Every 30s background check
 * 3. Network Recovery: Connectivity observer re-validates session
 * 4. Device Binding: Store & check Android ID
 * 5. Login Analytics: Persistent event log for debugging
 */

class AutoLoginManagerV2(
    private val context: Context,
    private val tokenManager: SecureTokenManagerV2,
    private val apiService: AuthApiService,
    private val brokerAuth: BrokerAuthManagerV2
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            logAuthEvent("NETWORK_RECOVERED")
            if (_authState.value is AuthState.RequiresLogin && (_authState.value as AuthState.RequiresLogin).canRetry) {
                scope.launch { performStartupAuth() }
            }
        }
        override fun onLost(network: Network) {
            logAuthEvent("NETWORK_LOST")
        }
    }

    // ===== SINGLE SOURCE OF TRUTH =====
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _analytics = MutableStateFlow<List<String>>(emptyList())
    val analyticsLog: StateFlow<List<String>> = _analytics.asStateFlow()

    // Retry config
    private val maxRetries = 3
    private val initialRetryDelayMs = 1000L
    private var monitorJob: Job? = null

    init {
        setupDeviceBinding()
        observeNetwork()
        scope.launch { 
            performStartupAuth()
            startSessionMonitor()
        }
    }

    private fun setupDeviceBinding() {
        val currentId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val storedId = tokenManager.getDeviceId()
        if (storedId == null) {
            tokenManager.saveDeviceId(currentId)
        } else if (storedId != currentId) {
            logAuthEvent("DEVICE_MISMATCH: Stored=$storedId, Current=$currentId")
            // Potential security action: logout()
        }
    }

    private fun logAuthEvent(event: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val entry = "[$timestamp] $event"
        _analytics.value = (_analytics.value + entry).takeLast(50)
        VedxLogger.i("AUTH_EVENT: $entry")
    }

    private fun observeNetwork() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    /**
     * Audit 1.2: Session Monitor
     * Checks JWT status every 30 seconds and refreshes if expiring soon.
     */
    private fun startSessionMonitor() {
        monitorJob?.cancel()
        monitorJob = scope.launch {
            while (isActive) {
                delay(30_000) // Check every 30 seconds
                if (_authState.value is AuthState.Authenticated) {
                    if (tokenManager.isJwtExpired()) {
                        attemptTokenRefresh()
                    }
                }
            }
        }
    }

    /**
     * PROPER STARTUP SEQUENCE:
     * 1. Restore session
     * 2. Validate/Refresh token
     * 3. Backend auth sync
     * 4. Connect feed
     * 5. Navigate home
     */
    private suspend fun performStartupAuth() {
        _authState.value = AuthState.Checking

        val tokens = tokenManager.getStoredTokens()

        if (tokens == null) {
            _authState.value = AuthState.RequiresLogin("No stored tokens")
            return
        }

        // Check if JWT is expired
        if (tokenManager.isJwtExpired()) {
            // Try to refresh
            val refreshed = attemptTokenRefresh()
            if (!refreshed) {
                _authState.value = AuthState.RequiresLogin("Session expired", canRetry = true)
                return
            }
        }

        // Validate with backend (with retry)
        val validated = validateWithRetry()
        if (!validated) {
            _authState.value = AuthState.RequiresLogin("Backend validation failed", canRetry = true)
            return
        }

        // Sync with broker
        val brokerSynced = brokerAuth.syncSession()
        if (!brokerSynced) {
            _authState.value = AuthState.Error("Broker sync failed", isRecoverable = true)
            return
        }

        // Validate feed token
        if (!tokenManager.isFeedTokenValid()) {
            val feedOk = refreshFeedToken()
            if (!feedOk) {
                _authState.value = AuthState.Error("Feed token invalid", isRecoverable = true)
                return
            }
        }

        // Get updated tokens after all operations
        val finalTokens = tokenManager.getStoredTokens()!!

        _authState.value = AuthState.Authenticated(
            jwtToken = finalTokens.jwt,
            refreshToken = finalTokens.refreshToken,
            feedToken = finalTokens.feedToken,
            clientCode = finalTokens.clientCode,
            broker = BrokerType.ANGEL_ONE,
            expiresAt = finalTokens.jwtExpiry * 1000  // convert to ms
        )
    }

    /**
     * ACTUAL TOKEN REFRESH — calls broker refresh API
     * Protected by mutex to prevent race conditions
     * Added retry with exponential backoff as per Audit 3.5
     */
    suspend fun attemptTokenRefresh(): Boolean = refreshMutex.withLock {
        // If already refreshed by another concurrent caller, skip
        if (!tokenManager.isJwtExpired()) return@withLock true

        var attempt = 0
        var delayMs = initialRetryDelayMs

        while (attempt < maxRetries) {
            val tokens = tokenManager.getStoredTokens() ?: return@withLock false

            try {
                _authState.value = AuthState.Refreshing

                val response = apiService.refreshToken(
                    RefreshTokenRequest(
                        jwtToken = tokens.jwt,
                        refreshToken = tokens.refreshToken,
                        clientCode = tokens.clientCode
                    )
                )

                if (response.isSuccessful && response.body() != null) {
                    val newTokens = response.body()!!

                    if (newTokens.jwt.isBlank() || newTokens.refreshToken.isBlank()) {
                        return@withLock false
                    }

                    tokenManager.saveTokens(
                        jwt = newTokens.jwt,
                        refreshToken = newTokens.refreshToken,
                        feedToken = newTokens.feedToken ?: tokens.feedToken,
                        clientCode = tokens.clientCode,
                        broker = tokens.broker
                    )
                    logAuthEvent("REFRESH_SUCCESS")
                    return@withLock true
                } else {
                    logAuthEvent("REFRESH_FAILED: Code=${response.code()}")
                    when (response.code()) {
                        401 -> {
                            handleUnauthorized()
                            return@withLock false
                        }
                        403 -> {
                            handleForbidden()
                            return@withLock false
                        }
                        else -> {
                            // Transient error, retry
                            attempt++
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is SocketTimeoutException || e is IOException) {
                    attempt++
                } else {
                    return@withLock false
                }
            }

            if (attempt < maxRetries) {
                delay(delayMs)
                delayMs *= 2
            }
        }
        return@withLock false
    }

    /**
     * Validate current token with backend (with retry)
     */
    private suspend fun validateWithRetry(): Boolean {
        var attempt = 0
        var delayMs = initialRetryDelayMs

        while (attempt < maxRetries) {
            try {
                val tokens = tokenManager.getStoredTokens() ?: return false

                val response = apiService.validateSession(tokens.jwt)

                if (response.isSuccessful) {
                    return true
                }

                when (response.code()) {
                    401 -> {
                        // Token invalid — try refresh once
                        return attemptTokenRefresh()
                    }
                    503, 502, 504 -> {
                        // Server error — retry
                        attempt++
                        if (attempt < maxRetries) {
                            delay(delayMs)
                            delayMs *= 2  // Exponential backoff
                        }
                    }
                    else -> return false
                }
            } catch (e: SocketTimeoutException) {
                attempt++
                if (attempt < maxRetries) {
                    delay(delayMs)
                    delayMs *= 2
                }
            } catch (e: IOException) {
                // No network — retry
                attempt++
                if (attempt < maxRetries) {
                    delay(delayMs)
                    delayMs *= 2
                }
            }
        }

        return false
    }

    /**
     * Refresh feed token separately
     */
    private suspend fun refreshFeedToken(): Boolean {
        return try {
            val tokens = tokenManager.getStoredTokens() ?: return false
            val response = apiService.getFeedToken(tokens.jwt)

            if (response.isSuccessful && response.body() != null) {
                val feedToken = response.body()!!.feedToken
                tokenManager.saveTokens(
                    jwt = tokens.jwt,
                    refreshToken = tokens.refreshToken,
                    feedToken = feedToken,
                    clientCode = tokens.clientCode,
                    broker = tokens.broker
                )
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun handleUnauthorized() {
        // JWT completely invalid — force re-login
        scope.launch {
            tokenManager.clearAll()
            _authState.value = AuthState.RequiresLogin("Session expired", canRetry = false)
        }
    }

    private fun handleForbidden() {
        // Account suspended, device changed, etc.
        scope.launch {
            tokenManager.clearAll()
            _authState.value = AuthState.RequiresLogin("Account access denied", canRetry = false)
        }
    }

    /**
     * Manual login flow
     */
    suspend fun login(clientCode: String, password: String, totp: String, apiKey: String): LoginResult {
        return try {
            val response = apiService.login(LoginRequest(clientCode, password, totp, apiKey))

            if (response.isSuccessful && response.body() != null) {
                val result = response.body()!!
                logAuthEvent("LOGIN_SUCCESS: $clientCode")

                // Validate all tokens
                if (result.jwt.isBlank() || result.refreshToken.isBlank() || result.feedToken.isBlank()) {
                    return LoginResult.Error(LoginError.Unknown("Invalid token response"))
                }

                // Save atomically
                tokenManager.saveTokens(
                    jwt = result.jwt,
                    refreshToken = result.refreshToken,
                    feedToken = result.feedToken,
                    clientCode = clientCode,
                    broker = "ANGEL",
                    apiKey = apiKey
                )

                // Set authenticated state
                _authState.value = AuthState.Authenticated(
                    jwtToken = result.jwt,
                    refreshToken = result.refreshToken,
                    feedToken = result.feedToken,
                    clientCode = clientCode,
                    broker = BrokerType.ANGEL_ONE,
                    expiresAt = tokenManager.getStoredTokens()?.jwtExpiry?.times(1000) ?: 0
                )

                // Sync broker session after manual login
                if (!brokerAuth.syncSession()) {
                    _authState.value = AuthState.Error("Feed connection failed", isRecoverable = true)
                    return LoginResult.Error(LoginError.NetworkError)
                }

                LoginResult.Success
            } else {
                when (response.code()) {
                    401 -> LoginResult.Error(LoginError.InvalidCredentials)
                    403 -> LoginResult.Error(LoginError.DeviceChanged)
                    429 -> LoginResult.Error(LoginError.RateLimited)
                    in 500..599 -> LoginResult.Error(LoginError.ServerError)
                    else -> LoginResult.Error(LoginError.Unknown("Login failed: ${response.code()}"))
                }
            }
        } catch (e: SocketTimeoutException) {
            LoginResult.Error(LoginError.NetworkError)
        } catch (e: IOException) {
            LoginResult.Error(LoginError.NetworkError)
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> LoginResult.Error(LoginError.InvalidCredentials)
                403 -> LoginResult.Error(LoginError.DeviceChanged)
                else -> LoginResult.Error(LoginError.Unknown(e.message()))
            }
        }
    }

    /**
     * Complete logout with cleanup
     */
    suspend fun logout() {
        logAuthEvent("USER_LOGOUT")
        // 1. Disconnect WebSocket
        brokerAuth.disconnect()

        // 2. Clear all tokens
        tokenManager.clearAll()

        // 3. Reset auth state
        _authState.value = AuthState.RequiresLogin("Logged out")
    }

    sealed class LoginResult {
        data object Success : LoginResult()
        data class Error(val error: LoginError) : LoginResult()
    }

    fun cleanup() {
        monitorJob?.cancel()
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Callback might not be registered
        }
        scope.cancel()
    }
}

// Retrofit interface (placeholder — adapt to your API)
interface AuthApiService {
    @POST("rest/auth/angelbroking/jwt/v1/generateTokens")
    suspend fun refreshToken(
        @Body req: RefreshTokenRequest
    ): Response<TokenResponse>

    @GET("rest/secure/angelbroking/user/v1/getProfile")
    suspend fun validateSession(@Header("Authorization") jwt: String): Response<Unit>

    @GET("rest/auth/angelbroking/user/v1/getFeedToken")
    suspend fun getFeedToken(@Header("Authorization") jwt: String): Response<FeedTokenResponse>

    @POST("rest/auth/angelbroking/user/v1/loginByPassword")
    suspend fun login(
        @Body req: LoginRequest
    ): Response<LoginResponse>
}

data class RefreshTokenRequest(
    val jwtToken: String,
    val refreshToken: String,
    val clientCode: String
)

data class LoginRequest(
    val clientcode: String,
    val password: String,
    val totp: String,
    val apiKey: String
)

data class TokenResponse(val jwt: String, val refreshToken: String, val feedToken: String?)
data class FeedTokenResponse(val feedToken: String)
data class LoginResponse(val jwt: String, val refreshToken: String, val feedToken: String)
