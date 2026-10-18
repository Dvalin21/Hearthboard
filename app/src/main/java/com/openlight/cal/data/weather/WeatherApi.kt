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

/** Supported weather providers. */
enum class WeatherProvider { OPEN_METEO, TWC }

/**
 * Minimal weather client supporting Open-Meteo (free, no key) and
 * The Weather Channel (TWC) API (requires an API key, like the
 * Home Assistant TWC integration).
 */
class WeatherApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Fetch 7-day forecast for the given coordinates.
     * @param provider weather source
     * @param apiKey TWC API key (ignored for Open-Meteo)
     * @param tempUnit "C" or "F" (controls TWC units param)
     * @param endpoint custom API base URL (Open-Meteo only)
     */
    suspend fun fetchForecast(
        lat: Double,
        lon: Double,
        provider: WeatherProvider = WeatherProvider.OPEN_METEO,
        apiKey: String = "",
        tempUnit: String = "C",
        endpoint: String = ""
    ): List<DailyForecast> = withContext(Dispatchers.IO) {
        when (provider) {
            WeatherProvider.OPEN_METEO -> fetchOpenMeteo(lat, lon, endpoint)
            WeatherProvider.TWC -> fetchTWC(lat, lon, apiKey, tempUnit)
        }
    }

    private fun fetchOpenMeteo(lat: Double, lon: Double, endpoint: String): List<DailyForecast> {
        val base = endpoint.trimEnd('/').ifBlank { "https://api.open-meteo.com/v1/forecast" }
        if (!isAllowedEndpoint(base)) return emptyList()
        val url = buildString {
            append(base)
            append("?latitude=$lat&longitude=$lon")
            append("&daily=temperature_2m_max,temperature_2m_min,weathercode")
            append("&timezone=auto&forecast_days=7")
        }
        val request = Request.Builder().url(url).build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body?.string()
        } ?: return emptyList()
        return parseOpenMeteo(body)
    }

    private fun parseOpenMeteo(json: String): List<DailyForecast> {
        val root = org.json.JSONObject(json)
        val daily = root.optJSONObject("daily") ?: return emptyList()
        val times = daily.optJSONArray("time") ?: return emptyList()
        val highs = daily.optJSONArray("temperature_2m_max") ?: return emptyList()
        val lows  = daily.optJSONArray("temperature_2m_min") ?: return emptyList()
        val codes = daily.optJSONArray("weathercode") ?: return emptyList()
        val result = mutableListOf<DailyForecast>()
        for (i in 0 until times.length()) {
            val date = try { LocalDate.parse(times.optString(i)) } catch (_: Exception) { continue }
            result.add(DailyForecast(date = date, tempHigh = highs.optDouble(i, 0.0),
                tempLow = lows.optDouble(i, 0.0), weatherCode = codes.optInt(i, 0)))
        }
        return result
    }

    private fun fetchTWC(lat: Double, lon: Double, apiKey: String, tempUnit: String): List<DailyForecast> {
        if (apiKey.isBlank()) return emptyList()
        val units = if (tempUnit == "F") "e" else "m"
        val url = "https://api.weather.com/v3/wx/forecast/daily/7day" +
            "?geocode=$lat,$lon&format=json&units=$units&language=en-US&apiKey=$apiKey"
        if (!isAllowedEndpoint(url)) return emptyList()
        val request = Request.Builder().url(url).build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.body?.string()
        } ?: return emptyList()
        return parseTWC(body, units == "e")
    }

    private fun parseTWC(json: String, imperial: Boolean): List<DailyForecast> {
        val root = org.json.JSONObject(json)
        val days = root.optJSONArray("dayOfWeek") ?: return emptyList()
        val maxT = root.optJSONArray("temperatureMax") ?: return emptyList()
        val minT = root.optJSONArray("temperatureMin") ?: return emptyList()
        val icons = root.optJSONArray("iconCode") ?: return emptyList()
        val times = root.optJSONArray("validTimeLocal") ?: return emptyList()
        val result = mutableListOf<DailyForecast>()
        for (i in 0 until days.length()) {
            val date = try {
                val t = times.optString(i)
                LocalDate.parse(t.substring(0, 10))
            } catch (_: Exception) { continue }
            var high = maxT.optDouble(i, 0.0)
            var low = minT.optDouble(i, 0.0)
            // TWC may return °F; convert to °C for consistent storage
            if (imperial) {
                high = (high - 32) * 5.0 / 9.0
                low = (low - 32) * 5.0 / 9.0
            }
            val code = twcIconToWmo(icons.optInt(i, 0))
            result.add(DailyForecast(date = date, tempHigh = high, tempLow = low, weatherCode = code))
        }
        return result
    }

    /** Map TWC icon codes to the closest WMO weather code. */
    private fun twcIconToWmo(code: Int): Int = when (code) {
        32, 34, 36 -> 0          // Sunny / Fair / Hot
        31, 33 -> 0               // Clear night / Fair night
        30, 29, 44 -> 2           // Partly Cloudy
        28, 27, 26 -> 3           // Mostly Cloudy / Cloudy
        20, 19 -> 45              // Fog / Haze / Dust
        9, 11, 12, 40 -> 80       // Drizzle / Showers
        10 -> 66                  // Freezing Rain
        8 -> 56                   // Freezing Drizzle
        13, 14, 42, 46 -> 71     // Snow Flurries / Snow Showers
        16, 41, 43 -> 75         // Snow / Heavy Snow
        5, 6, 7, 18 -> 66         // Mixed
        15 -> 77                  // Blowing Snow
        17, 35 -> 96              // Hail
        4, 37, 38, 39, 45 -> 95  // Thunderstorms
        0, 1, 2, 3 -> 95          // Tornado/Tropical/Hurricane/Severe
        23, 24 -> 3               // Blustery/Windy -> Cloudy-ish
        25 -> 0                   // Cold
        else -> 0
    }

    private fun isAllowedEndpoint(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.startsWith("https://", ignoreCase = true)) return true
        if (!trimmed.startsWith("http://", ignoreCase = true)) return false
        val host = runCatching { java.net.URI(trimmed).host ?: "" }.getOrDefault("")
        return host == "localhost" || host == "127.0.0.1" ||
            host.startsWith("10.") || host.startsWith("192.168.") ||
            host.startsWith("172.")
    }
}
