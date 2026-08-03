package com.vedx.vedxsuper.repository

import com.vedx.vedxsuper.broker.AngelApiClient
import com.vedx.vedxsuper.broker.SecureTokenManager
import com.vedx.vedxsuper.broker.TotpGenerator
import com.vedx.vedxsuper.model.auth.AngelLoginRequest
import com.vedx.vedxsuper.model.auth.AngelLoginResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.NetworkInterface

class AuthRepository(private val tokenManager: SecureTokenManager) {

    suspend fun login(clientId: String, password: String, totpKey: String, apiKey: String): Result<AngelLoginResponse> = withContext(Dispatchers.IO) {
        try {
            val totp = TotpGenerator.generate(totpKey)
            if (totp.isEmpty()) {
                return@withContext Result.failure(Exception("Failed to generate TOTP"))
            }

            val mac = getMacAddress()
            val ip = getPublicIp()

            val response = AngelApiClient.api.login(
                apiKey = apiKey,
                clientLocalIp = ip,
                clientPublicIp = ip,
                macAddress = mac,
                request = AngelLoginRequest(clientId, password, totp)
            )

            if (response.isSuccessful && response.body()?.status == true) {
                val data = response.body()!!.data!!
                tokenManager.saveTokens(data.jwtToken, data.refreshToken, data.feedToken)
                tokenManager.saveCredentials(clientId, apiKey, password, totpKey)
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.body()?.message ?: "Login failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun silentLogin(): Result<AngelLoginResponse> {
        val creds = tokenManager.getCredentials()
        val clientId = creds["clientId"]
        val apiKey = creds["apiKey"]
        val password = creds["password"]
        val totpKey = creds["totpKey"]

        return if (clientId != null && apiKey != null && password != null && totpKey != null) {
            login(clientId, password, totpKey, apiKey)
        } else {
            Result.failure(Exception("Missing stored credentials"))
        }
    }

    private fun getPublicIp(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.isSiteLocalAddress) {
                        return address.hostAddress ?: "10.0.0.1"
                    }
                }
            }
            "10.0.0.1"
        } catch (e: Exception) {
            "10.0.0.1"
        }
    }

    private fun getMacAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val mac = networkInterface.hardwareAddress
                if (mac != null) {
                    val sb = StringBuilder()
                    for (i in mac.indices) {
                        sb.append(String.format("%02X%s", mac[i], if (i < mac.size - 1) ":" else ""))
                    }
                    return sb.toString()
                }
            }
            "02:00:00:00:00:00"
        } catch (e: Exception) {
            "02:00:00:00:00:00"
        }
    }
}
