package com.openlight.cal.data.weather

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** One day's weather forecast. */
@Immutable
data class DailyForecast(
    val date: LocalDate,
    val tempHigh: Double,
    val tempLow: Double,
    val weatherCode: Int     // WMO weather code (0=clear, 1-3=cloudy, etc.)
) {
    /** Human-readable condition label. */
    val conditionLabel: String get() = when (weatherCode) {
        0              -> "Clear"
        1, 2, 3        -> "Cloudy"
        45, 48         -> "Fog"
        51, 53, 55     -> "Drizzle"
        56, 57         -> "Freezing Drizzle"
        61, 63, 65     -> "Rain"
        66, 67         -> "Freezing Rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82     -> "Rain Showers"
        85, 86         -> "Snow Showers"
        95, 96, 99     -> "Thunderstorm"
        else           -> ""
    }

    /** Short emoji-free icon character for the condition. */
    val iconChar: String get() = when (weatherCode) {
        0              -> "\u2600\uFE0F"  // ☀️
        1, 2, 3        -> "\u26C5"        // ⛅
        45, 48         -> "\uD83C\uDF2B"  // 🌫
        51, 53, 55, 56, 57 -> "\uD83C\uDF26" // 🌦
        61, 63, 65, 66, 67 -> "\uD83C\uDF27" // 🌧
        71, 73, 75, 77     -> "\u2744\uFE0F" // ❄️
        80, 81, 82         -> "\uD83C\uDF27" // 🌧
        85, 86             -> "\u2744\uFE0F" // ❄️
        95, 96, 99         -> "\u26A1"        // ⚡
        else               -> ""
    }
}

/**
 * Minimal Open-Meteo weather client.
 * Free API: https://open-meteo.com/ (no API key required).
 */
class WeatherApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch 7-day forecast for the given coordinates.
     * @param endpoint custom API base URL (defaults to Open-Meteo)
     */
    suspend fun fetchForecast(
        lat: Double,
        lon: Double,
        endpoint: String = "https://api.open-meteo.com/v1/forecast"
    ): List<DailyForecast> = withContext(Dispatchers.IO) {
        val url = buildString {
            append(endpoint.trimEnd('/'))
            append("?latitude=$lat&longitude=$lon")
            append("&daily=temperature_2m_max,temperature_2m_min,weathercode")
            append("&timezone=auto&forecast_days=7")
        }

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: return@withContext emptyList()

        parseForecastResponse(body)
    }

    private fun parseForecastResponse(json: String): List<DailyForecast> {
        val root = JSONObject(json)
        val daily = root.optJSONObject("daily") ?: return emptyList()

        val times = daily.optJSONArray("time") ?: return emptyList()
        val highs = daily.optJSONArray("temperature_2m_max") ?: return emptyList()
        val lows  = daily.optJSONArray("temperature_2m_min") ?: return emptyList()
        val codes = daily.optJSONArray("weathercode") ?: return emptyList()

        val result = mutableListOf<DailyForecast>()
        for (i in 0 until times.length()) {
            val date = try { LocalDate.parse(times.optString(i)) } catch (_: Exception) { continue }
            val high = highs.optDouble(i, 0.0)
            val low  = lows.optDouble(i, 0.0)
            val code = codes.optInt(i, 0)
            result.add(DailyForecast(date = date, tempHigh = high, tempLow = low, weatherCode = code))
        }
        return result
    }
}
