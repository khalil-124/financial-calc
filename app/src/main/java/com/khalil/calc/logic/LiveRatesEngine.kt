package com.khalil.calc.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object LiveRatesEngine {

    data class FedRate(
        val rate: Double,
        val date: String,
        val isSuccess: Boolean,
        val errorMessage: String? = null
    )

    suspend fun fetchLatestSOFR(): FedRate = withContext(Dispatchers.IO) {
        try {
            // New York Fed Public API for Secured Overnight Financing Rate (SOFR)
            val url = URL("https://markets.newyorkfed.org/api/rates/secured/sofr/last/1.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                
                // Parse the NY Fed JSON structure
                val ratesArray = json.getJSONObject("refRates").getJSONArray("refRates")
                if (ratesArray.length() > 0) {
                    val latestEntry = ratesArray.getJSONObject(0)
                    val percentRate = latestEntry.getDouble("percentRate")
                    val date = latestEntry.getString("effectiveDate")
                    
                    return@withContext FedRate(rate = percentRate, date = date, isSuccess = true)
                }
            }
            return@withContext FedRate(0.0, "", false, "Invalid API Response")
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback default if offline or API fails
            return@withContext FedRate(5.31, "Offline/Fallback", false, e.message)
        }
    }
}
