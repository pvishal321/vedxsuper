package com.vedx.vedxsuper.model.market

import com.google.gson.annotations.SerializedName

data class TickData(
    val symbol: String,
    val token: String,
    val ltp: Double,
    val change: Double = 0.0,
    val changePercent: Double = 0.0,
    val high: Double = 0.0,
    val low: Double = 0.0,
    val prevClose: Double = 0.0,
    val openInterest: Long = 0,
    val volume: Long = 0,
    val bid: Double = 0.0,
    val ask: Double = 0.0,
    val iv: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class Candle(
    val timestamp: Long, // Start of the minute
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long = 0,
    val isComplete: Boolean = false
)

data class IndexData(
    @SerializedName("symbol") val symbol: String,
    @SerializedName("last_traded_price") val lastTradedPrice: Double,
    @SerializedName("change") val change: Double,
    @SerializedName("change_percent") val changePercent: Double,
    val high: Double = 0.0,
    val low: Double = 0.0
)

data class OptionStrike(
    val strikePrice: Double,
    val ceOI: Long = 0,
    val peOI: Long = 0,
    val ceLtp: Double = 0.0,
    val peLtp: Double = 0.0,
    val ceIv: Double = 0.0,
    val peIv: Double = 0.0
)

data class OptionChain(
    val symbol: String,
    val spotPrice: Double,
    val strikes: List<OptionStrike>,
    val pcr: Double = 1.0,
    val atmStrike: Double = 0.0
)

data class HistoricalDataRequest(
    @SerializedName("exchange") val exchange: String,
    @SerializedName("symboltoken") val symbolToken: String,
    @SerializedName("interval") val interval: String,
    @SerializedName("fromdate") val fromDate: String,
    @SerializedName("todate") val toDate: String
)

data class HistoricalDataResponse(
    @SerializedName("status") val status: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("errorcode") val errorCode: String,
    @SerializedName("data") val data: List<List<Any>>?
)
