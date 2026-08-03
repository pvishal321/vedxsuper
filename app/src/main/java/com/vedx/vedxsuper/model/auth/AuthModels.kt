package com.vedx.vedxsuper.model.auth

import com.google.gson.annotations.SerializedName

data class AngelLoginRequest(
    @SerializedName("clientcode")
    val clientCode: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("totp")
    val totp: String
)

data class AngelLoginResponse(
    @SerializedName("status")
    val status: Boolean,
    @SerializedName("message")
    val message: String?,
    @SerializedName("data")
    val data: LoginData?
)

data class LoginData(
    @SerializedName("jwtToken")
    val jwtToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String,
    @SerializedName("feedToken")
    val feedToken: String
)
