package com.vedx.vedxsuper.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class OptionDataManager(private val context: Context) {

    companion object {
        const val SCRIP_MASTER_URL = "https://margincalculator.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json"
        const val INSTRUMENT_OPTIDX = "OPTIDX"
        const val MAX_WS_TOKENS = 1000
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var scripMasterData: List<ScripInstrument> = emptyList()
    private var lastFetchTime: Long = 0

    data class ScripInstrument(
        val token: String,
        val symbol: String,
        val name: String,
        val expiry: String,
        val strike: Double,
        val lotSize: String,
        val instrumentType: String,
        val exchange: String,
        val tickSize: String,
        val tokenType: String
    )

    data class OptionChain(
        val underlying: String,
        val expiry: String,
        val atmStrike: Double,
        val strikes: List<Double>,
        val calls: Map<Double, ScripInstrument>,
        val puts: Map<Double, ScripInstrument>
    )

    suspend fun fetchScripMaster(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(SCRIP_MASTER_URL).build()
            val response = client.newCall(request).execute()
            val jsonString = response.body?.string() ?: return@withContext false

            val jsonArray = JSONArray(jsonString)
            val instruments = mutableListOf<ScripInstrument>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val instrumentType = obj.optString("instrumenttype", "")
                if (instrumentType == INSTRUMENT_OPTIDX) {
                    instruments.add(
                        ScripInstrument(
                            token = obj.optString("token", ""),
                            symbol = obj.optString("symbol", ""),
                            name = obj.optString("name", ""),
                            expiry = obj.optString("expiry", ""),
                            strike = obj.optString("strike", "0").toDoubleOrNull() ?: 0.0,
                            lotSize = obj.optString("lotsize", "0"),
                            instrumentType = instrumentType,
                            exchange = obj.optString("exch_seg", ""),
                            tickSize = obj.optString("tick_size", ""),
                            tokenType = obj.optString("token_type", "")
                        )
                    )
                }
            }

            scripMasterData = instruments
            lastFetchTime = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun needsRefresh(): Boolean {
        return scripMasterData.isEmpty() ||
                (System.currentTimeMillis() - lastFetchTime) > 12 * 60 * 60 * 1000
    }

    fun getOptionChain(underlying: String, expiry: String? = null): OptionChain? {
        if (scripMasterData.isEmpty()) return null

        val options = scripMasterData.filter {
            it.name.equals(underlying, ignoreCase = true) &&
            it.instrumentType == INSTRUMENT_OPTIDX
        }

        if (options.isEmpty()) return null

        val expiries = options.map { it.expiry }.distinct()
            .sortedWith(compareBy { parseExpiry(it) })

        val selectedExpiry = expiry ?: expiries.firstOrNull() ?: return null
        val expiryOptions = options.filter { it.expiry == selectedExpiry }

        val calls = expiryOptions.filter { it.symbol.endsWith("CE") }
            .associateBy { it.strike }.toSortedMap()

        val puts = expiryOptions.filter { it.symbol.endsWith("PE") }
            .associateBy { it.strike }.toSortedMap()

        val allStrikes = (calls.keys + puts.keys).distinct().sorted()

        return OptionChain(
            underlying = underlying,
            expiry = selectedExpiry,
            atmStrike = 0.0,
            strikes = allStrikes,
            calls = calls,
            puts = puts
        )
    }

    fun getATMStrikes(optionChain: OptionChain, currentPrice: Double, range: Int = 5): OptionChain {
        val atmStrike = optionChain.strikes.minByOrNull { kotlin.math.abs(it - currentPrice) } ?: 0.0
        val atmIndex = optionChain.strikes.indexOf(atmStrike)
        val start = (atmIndex - range).coerceAtLeast(0)
        val end = (atmIndex + range + 1).coerceAtMost(optionChain.strikes.size)
        val nearbyStrikes = optionChain.strikes.subList(start, end)

        return optionChain.copy(
            atmStrike = atmStrike,
            strikes = nearbyStrikes,
            calls = optionChain.calls.filterKeys { it in nearbyStrikes },
            puts = optionChain.puts.filterKeys { it in nearbyStrikes }
        )
    }

    fun getTokensForStrikes(optionChain: OptionChain, strikes: List<Double>): List<String> {
        val tokens = mutableListOf<String>()
        strikes.forEach { strike ->
            optionChain.calls[strike]?.let { tokens.add(it.token) }
            optionChain.puts[strike]?.let { tokens.add(it.token) }
        }
        return tokens
    }

    fun getAllOptionTokens(optionChain: OptionChain): List<String> {
        val tokens = mutableListOf<String>()
        optionChain.strikes.forEach { strike ->
            optionChain.calls[strike]?.let { tokens.add(it.token) }
            optionChain.puts[strike]?.let { tokens.add(it.token) }
        }
        return tokens.take(MAX_WS_TOKENS)
    }

    fun getInstrumentByToken(token: String): ScripInstrument? {
        return scripMasterData.find { it.token == token }
    }

    fun getExpiries(underlying: String): List<String> {
        return scripMasterData
            .filter {
                it.name.equals(underlying, ignoreCase = true) &&
                        it.instrumentType == INSTRUMENT_OPTIDX
            }
            .map { it.expiry }
            .distinct()
            .sortedWith(compareBy { parseExpiry(it) })
    }

    private fun parseExpiry(expiryStr: String): Date {
        return try {
            SimpleDateFormat("ddMMMyyyy", Locale.US).parse(expiryStr) ?: Date(0)
        } catch (e: Exception) { Date(0) }
    }
}
