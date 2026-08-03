package com.vedx.vedxsuper.broker

import com.vedx.vedxsuper.model.auth.AngelLoginRequest
import com.vedx.vedxsuper.model.auth.AngelLoginResponse
import com.vedx.vedxsuper.model.market.HistoricalDataRequest
import com.vedx.vedxsuper.model.market.HistoricalDataResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AngelOneApiService {

    @POST("rest/auth/angelbroking/user/v1/loginByPassword")
    suspend fun login(
        @Header("X-PrivateKey") apiKey: String,
        @Header("Accept") accept: String = "application/json",
        @Header("Content-Type") contentType: String = "application/json",
        @Header("X-SourceID") sourceId: String = "WEB",
        @Header("X-ClientLocalIP") clientLocalIp: String,
        @Header("X-ClientPublicIP") clientPublicIp: String,
        @Header("X-MACAddress") macAddress: String,
        @Header("X-UserType") userType: String = "USER",
        @Body request: AngelLoginRequest
    ): Response<AngelLoginResponse>

    @POST("rest/auth/angelbroking/user/v1/renewToken")
    suspend fun renewToken(
        @Header("X-PrivateKey") apiKey: String,
        @Header("Authorization") jwtToken: String,
        @Header("Accept") accept: String = "application/json",
        @Header("Content-Type") contentType: String = "application/json",
        @Header("X-SourceID") sourceId: String = "WEB",
        @Header("X-ClientLocalIP") clientLocalIp: String,
        @Header("X-ClientPublicIP") clientPublicIp: String,
        @Header("X-MACAddress") macAddress: String,
        @Header("X-UserType") userType: String = "USER",
        @Body request: Map<String, String> // Should contain "refreshToken"
    ): Response<AngelLoginResponse>

    @POST("rest/secure/angelbroking/historical/v1/getCandle")
    suspend fun getHistoricalData(
        @Header("X-PrivateKey") apiKey: String,
        @Header("Authorization") jwtToken: String,
        @Header("Accept") accept: String = "application/json",
        @Header("Content-Type") contentType: String = "application/json",
        @Header("X-SourceID") sourceId: String = "WEB",
        @Header("X-ClientLocalIP") clientLocalIp: String,
        @Header("X-ClientPublicIP") clientPublicIp: String,
        @Header("X-MACAddress") macAddress: String,
        @Header("X-UserType") userType: String = "USER",
        @Body request: HistoricalDataRequest
    ): Response<HistoricalDataResponse>
}
