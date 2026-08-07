package com.vedx.vedxsuper.auth

import kotlinx.coroutines.*

/**
 * ============================================================
 * BROKER AUTH MANAGER V2 — PROPER STARTUP SEQUENCE
 * ============================================================
 * 
 * Fixes:
 * 1. Sync session AFTER token validation (not parallel)
 * 2. Feed connection AFTER auth sync (not before)
 * 3. Proper disconnect on logout
 * 4. No duplicate auth state
 * 5. Connection state exposed via AutoLoginManager's AuthState
 */

class BrokerAuthManagerV2(
    private val webSocketManager: WebSocketManager  // Your WS manager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Sync broker session — call AFTER token is validated
     */
    suspend fun syncSession(
        subscribeToIndices: Boolean = true,
        subscribeToOptions: Boolean = true
    ): Boolean {
        return try {
            // 1. Validate broker session
            val sessionValid = webSocketManager.validateAuth()
            if (!sessionValid) {
                return false
            }

            // 2. Connect to market feed
            webSocketManager.connectFeed()

            // 3. Subscribe to required channels
            if (subscribeToIndices) webSocketManager.subscribeToIndices()
            if (subscribeToOptions) webSocketManager.subscribeToOptions()

            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Disconnect everything on logout
     */
    suspend fun disconnect() {
        try {
            webSocketManager.unsubscribeAll()
            webSocketManager.disconnectFeed()
            webSocketManager.disconnect()
        } catch (e: Exception) {
            // Ignore disconnect errors
        }
    }

    interface WebSocketManager {
        suspend fun validateAuth(): Boolean
        suspend fun connectFeed()
        suspend fun subscribeToIndices()
        suspend fun subscribeToOptions()
        suspend fun unsubscribeAll()
        suspend fun disconnectFeed()
        suspend fun disconnect()
    }
}
