package com.openlight.cal.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openlight_prefs")

class AppPreferences(private val context: Context) {

    companion object {
        val KEY_DARK_MODE       = intPreferencesKey("dark_mode")       // 0=system, 1=light, 2=dark
        val KEY_THEME_SEED      = stringPreferencesKey("theme_seed")   // hex color for M3 seed
        val KEY_FONT_SIZE       = floatPreferencesKey("font_size")     // 1.0f = normal
        val KEY_FIRST_DAY_MON   = booleanPreferencesKey("first_day_mon")
        val KEY_24HR_CLOCK      = booleanPreferencesKey("24hr_clock")
        val KEY_DEFAULT_VIEW    = stringPreferencesKey("default_view") // MONTH|WEEK|DAY|AGENDA
        val KEY_KIOSK_MODE      = booleanPreferencesKey("kiosk_mode")
        val KEY_PARENTAL_PIN    = stringPreferencesKey("parental_pin") // "" = disabled
        val KEY_DEFAULT_REMINDER= intPreferencesKey("default_reminder")// minutes, -1=none
        val KEY_SYNC_WIFI_ONLY  = booleanPreferencesKey("sync_wifi_only")
        val KEY_SHOW_DECLINED   = booleanPreferencesKey("show_declined")
        val KEY_SHOW_WEEKENDS   = booleanPreferencesKey("show_weekends")
        val KEY_SETUP_DONE      = booleanPreferencesKey("setup_done")
        // Weather
        val KEY_WEATHER_LAT     = stringPreferencesKey("weather_lat")
        val KEY_WEATHER_LON     = stringPreferencesKey("weather_lon")
        val KEY_WEATHER_ENDPOINT= stringPreferencesKey("weather_endpoint")
        // Auto-archive — referenced by HearthboardApp.onCreate() and the
        // Settings UI. 0 = disabled. Value is months; events older than N
        // months are deleted at app startup.
        val KEY_AUTO_ARCHIVE_MONTHS = intPreferencesKey("auto_archive_months")
        // Mealie integration (optional self-hosted recipe service)
        val KEY_MEALIE_URL      = stringPreferencesKey("mealie_url")
        val KEY_MEALIE_TOKEN    = stringPreferencesKey("mealie_token")
        // Wall Mode — Skylight-style permanent display
        val KEY_WALL_MODE       = booleanPreferencesKey("wall_mode")
        val KEY_WALL_KEEP_ON    = booleanPreferencesKey("wall_keep_screen_on")
        // No telemetry keys - period.
    }

    val darkMode: Flow<Int>           = context.dataStore.data.map { it[KEY_DARK_MODE] ?: 0 }
    val themeSeedColor: Flow<String>  = context.dataStore.data.map { it[KEY_THEME_SEED] ?: "" }
    val fontSize: Flow<Float>         = context.dataStore.data.map { it[KEY_FONT_SIZE] ?: 1.0f }
    val firstDayMonday: Flow<Boolean> = context.dataStore.data.map { it[KEY_FIRST_DAY_MON] ?: false }
    val use24HrClock: Flow<Boolean>   = context.dataStore.data.map { it[KEY_24HR_CLOCK] ?: false }
    val defaultView: Flow<String>     = context.dataStore.data.map { it[KEY_DEFAULT_VIEW] ?: "MONTH" }
    val kioskMode: Flow<Boolean>      = context.dataStore.data.map { it[KEY_KIOSK_MODE] ?: false }
    val parentalPin: Flow<String>     = context.dataStore.data.map { it[KEY_PARENTAL_PIN] ?: "" }
    val defaultReminder: Flow<Int>    = context.dataStore.data.map { it[KEY_DEFAULT_REMINDER] ?: 15 }
    val syncWifiOnly: Flow<Boolean>   = context.dataStore.data.map { it[KEY_SYNC_WIFI_ONLY] ?: false }
    val showDeclined: Flow<Boolean>   = context.dataStore.data.map { it[KEY_SHOW_DECLINED] ?: false }
    val showWeekends: Flow<Boolean>   = context.dataStore.data.map { it[KEY_SHOW_WEEKENDS] ?: true }
    val setupDone: Flow<Boolean>      = context.dataStore.data.map { it[KEY_SETUP_DONE] ?: false }
    val weatherLat: Flow<String>      = context.dataStore.data.map { it[KEY_WEATHER_LAT] ?: "" }
    val weatherLon: Flow<String>      = context.dataStore.data.map { it[KEY_WEATHER_LON] ?: "" }
    val weatherEndpoint: Flow<String> = context.dataStore.data.map { it[KEY_WEATHER_ENDPOINT] ?: "" }
    val autoArchiveMonths: Flow<Int>  = context.dataStore.data.map { it[KEY_AUTO_ARCHIVE_MONTHS] ?: 0 }
    val mealieUrl: Flow<String>       = context.dataStore.data.map { it[KEY_MEALIE_URL] ?: "" }
    val mealieToken: Flow<String>     = context.dataStore.data.map { it[KEY_MEALIE_TOKEN] ?: "" }
    val wallMode: Flow<Boolean>       = context.dataStore.data.map { it[KEY_WALL_MODE] ?: false }
    val wallKeepScreenOn: Flow<Boolean> = context.dataStore.data.map { it[KEY_WALL_KEEP_ON] ?: true }

    suspend fun set(key: Preferences.Key<Int>, value: Int) {
        context.dataStore.edit { it[key] = value }
    }
    suspend fun set(key: Preferences.Key<String>, value: String) {
        context.dataStore.edit { it[key] = value }
    }
    suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        context.dataStore.edit { it[key] = value }
    }
    suspend fun set(key: Preferences.Key<Float>, value: Float) {
        context.dataStore.edit { it[key] = value }
    }
}
