package com.vedx.vedxsuper.api

import retrofit2.*
import retrofit2.http.*
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.annotations.SerializedName

interface AngelApi {
    @POST("rest/auth/angelbroking/user/v1/loginByPassword")
    suspend fun login(@Body r: LoginReq): Response<LoginResp>
    
    @POST("rest/secure/angelbroking/order/v1/placeOrder")
    suspend fun order(@Body r: OrderReq): Response<OrderResp>
    
    @POST("rest/secure/angelbroking/historical/v1/getCandleData")
    suspend fun candles(@Body r: CandleReq): Response<CandleResp>
}

data class LoginReq(val clientcode: String, val password: String, val totp: String)
data class LoginResp(@SerializedName("jwtToken") val token: String, @SerializedName("refreshToken") val refresh: String)
data class OrderReq(val variety: String, val tradingsymbol: String, val symboltoken: String, val transactiontype: String, val exchange: String, val ordertype: String, val producttype: String, val duration: String, val price: String, val squareoff: String, val stoploss: String, val quantity: String)
data class OrderResp(val orderid: String, val status: String)

data class CandleReq(
    val exchange: String = "NSE",
    val symboltoken: String = "99926000",
    val interval: String = "ONE_MINUTE",
    val fromdate: String,
    val todate: String
)
data class CandleResp(
    val data: List<List<Any>>?, 
    val status: Boolean = false, 
    val errorcode: String? = null, 
    val message: String? = null
)

class AngelClient {
    private val api: AngelApi = Retrofit.Builder()
        .baseUrl("https://apiconnect.angelone.in/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AngelApi::class.java)
    
    var token: String = ""
    
    suspend fun login(c: String, p: String, t: String): Boolean {
        val r = api.login(LoginReq(c, p, t))
        return if (r.isSuccessful) { token = r.body()?.token ?: return false; true } else false
    }
    
    suspend fun buy(sym: String, tok: String, qty: Int, price: Double, sl: Double) = 
        api.order(OrderReq("NORMAL", sym, tok, "BUY", "NFO", "MARKET", "INTRADAY", "DAY", price.toString(), "0", sl.toString(), qty.toString()))
    
    suspend fun sell(sym: String, tok: String, qty: Int, price: Double, sl: Double) = 
        api.order(OrderReq("NORMAL", sym, tok, "SELL", "NFO", "MARKET", "INTRADAY", "DAY", price.toString(), "0", sl.toString(), qty.toString()))
}
