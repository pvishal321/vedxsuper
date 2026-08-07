package com.vedx.vedxsuper.api

import okhttp3.ResponseBody.Companion.toResponseBody
import com.vedx.vedxsuper.auth.*
import com.vedx.vedxsuper.broker.TotpGenerator
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.*
import retrofit2.http.*
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.annotations.SerializedName

interface AngelApi {
    @POST("rest/auth/angelbroking/user/v1/loginByPassword")
    suspend fun login(@Body r: LoginReq): Response<LoginResp>

    @POST("rest/secure/angelbroking/order/v1/placeOrder")
    suspend fun order(@Body r: OrderReq): Response<OrderResp>

    @POST("rest/auth/angelbroking/jwt/v1/generateTokens")
    suspend fun renewToken(@Body r: RenewTokenReq): Response<RenewTokenResp>

    @POST("rest/secure/angelbroking/historical/v1/getCandleData")
    suspend fun candles(@Body r: CandleReq): Response<CandleResp>
}

data class LoginReq(val clientcode: String, val password: String, val totp: String)

data class RenewTokenReq(
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("jwtToken") val jwtToken: String
)

data class RenewTokenResp(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: LoginData?
)

data class LoginData(
    @SerializedName("jwtToken") val jwtToken: String?,
    @SerializedName("refreshToken") val refreshToken: String?,
    @SerializedName("feedToken") val feedToken: String?
)

data class LoginResp(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String?,
    @SerializedName("data") val data: LoginData?
)

data class OrderReq(
    val variety: String,
    val tradingsymbol: String,
    val symboltoken: String,
    val transactiontype: String,
    val exchange: String,
    val ordertype: String,
    val producttype: String,
    val duration: String,
    val price: String,
    val squareoff: String,
    val stoploss: String,
    val quantity: String
)

data class OrderResp(val orderid: String?, val status: String?, val message: String?)

data class CandleReq(
    val exchange: String = "NSE",
    val symboltoken: String = "99926000",
    val interval: String = "ONE_MINUTE",
    val fromdate: String,
    val todate: String
)

data class CandleResp(
    val data: List<List<String>>?,
    val status: Boolean = false,
    val errorcode: String? = null,
    val message: String? = null
)

class AngelClient : AuthApiService {
    @Volatile var apiKey: String = ""
    @Volatile var token: String = ""
    @Volatile var refreshToken: String = ""
    @Volatile var feedToken: String = ""
    @Volatile var isPaperTrading: Boolean = true

    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-UserType", "USER")
            .header("X-SourceID", "WEB")
            .header("X-PrivateKey", apiKey)
        
        if (token.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        chain.proceed(requestBuilder.build())
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val api: AngelApi = Retrofit.Builder()
        .baseUrl("https://apiconnect.angelone.in/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AngelApi::class.java)

    override suspend fun refreshToken(req: RefreshTokenRequest): Response<TokenResponse> {
        val r = api.renewToken(RenewTokenReq(req.refreshToken, req.jwtToken))
        return if (r.isSuccessful && r.body()?.status == true) {
            val d = r.body()?.data
            Response.success(TokenResponse(d?.jwtToken ?: "", d?.refreshToken ?: "", d?.feedToken))
        } else {
            val errorCode = r.code()
            val errorMessage = r.body()?.message ?: "Token Renewal Failed"
            val errorBody = r.errorBody() ?: errorMessage.toResponseBody(null)
            Response.error(errorCode, errorBody)
        }
    }

    override suspend fun validateSession(jwt: String): Response<Unit> {
        // Placeholder implementation for validateSession
        return Response.success(Unit)
    }

    override suspend fun getFeedToken(jwt: String): Response<FeedTokenResponse> {
        // Feed token is usually obtained during login or refresh in Angel One
        return Response.success(FeedTokenResponse(feedToken))
    }

    override suspend fun login(req: LoginRequest): Response<LoginResponse> {
        this.apiKey = req.apiKey
        val totp = if (req.totp.length > 6) TotpGenerator.generate(req.totp) else req.totp
        val r = api.login(LoginReq(req.clientcode, req.password, totp))
        return if (r.isSuccessful && r.body()?.status == true) {
            val d = r.body()?.data
            Response.success(LoginResponse(d?.jwtToken ?: "", d?.refreshToken ?: "", d?.feedToken ?: ""))
        } else {
            val errorCode = r.code()
            val errorMessage = r.body()?.message ?: "Login Failed"
            val errorBody = r.errorBody() ?: errorMessage.toResponseBody(null)
            Response.error(errorCode, errorBody)
        }
    }

    suspend fun login(clientCode: String, password: String, totpSecret: String, key: String): Pair<Boolean, String> {
        val r = login(LoginRequest(clientCode, password, totpSecret, key))
        return if (r.isSuccessful) {
            val body = r.body()!!
            token = body.jwt
            refreshToken = body.refreshToken
            feedToken = body.feedToken
            Pair(true, "Success")
        } else {
            Pair(false, "Login Failed: ${r.code()}")
        }
    }

    suspend fun renewToken(jwtToken: String, refreshToken: String): Pair<Boolean, String> {
        val r = refreshToken(RefreshTokenRequest(jwtToken, refreshToken, "")) // clientCode not needed for renew if using this endpoint
        return if (r.isSuccessful) {
            val body = r.body()!!
            token = body.jwt
            this.refreshToken = body.refreshToken
            feedToken = body.feedToken ?: ""
            Pair(true, "Success")
        } else {
            Pair(false, "Token Renewal Failed: ${r.code()}")
        }
    }

    suspend fun buy(sym: String, tok: String, qty: Int, price: Double, sl: Double): Result<OrderResp> = runCatching {
        val resp = api.order(OrderReq("NORMAL", sym, tok, "BUY", "NFO", "MARKET", "INTRADAY", "DAY", price.toString(), "0", sl.toString(), qty.toString()))
        if (!resp.isSuccessful) throw java.io.IOException(resp.errorBody()?.string() ?: "Unknown Order Error")
        resp.body() ?: throw java.io.IOException("Empty order response")
    }

    suspend fun sell(sym: String, tok: String, qty: Int, price: Double, sl: Double): Result<OrderResp> = runCatching {
        val resp = api.order(OrderReq("NORMAL", sym, tok, "SELL", "NFO", "MARKET", "INTRADAY", "DAY", price.toString(), "0", sl.toString(), qty.toString()))
        if (!resp.isSuccessful) throw java.io.IOException(resp.errorBody()?.string() ?: "Unknown Order Error")
        resp.body() ?: throw java.io.IOException("Empty order response")
    }
}
