package com.openlight.cal.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.CalendarAccount
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.preferences.EncryptedPassword
import com.openlight.cal.data.sync.CalDAVClient
import com.openlight.cal.data.sync.CalDAVSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────────────────────
// Settings ViewModel
// ─────────────────────────────────────────────────────────────
class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs      = (app as HearthboardApp).preferences
    private val encryptor  = (app as HearthboardApp).encryptor
    private val accR       = (app as HearthboardApp).accountRepository

    val darkMode       = prefs.darkMode.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val themeSeedColor = prefs.themeSeedColor.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val fontSize       = prefs.fontSize.stateIn(viewModelScope, SharingStarted.Eagerly, 1.0f)
    val firstDayMon    = prefs.firstDayMonday.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val use24Hr        = prefs.use24HrClock.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val kioskMode      = prefs.kioskMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val parentalPin    = prefs.parentalPin.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val syncWifiOnly   = prefs.syncWifiOnly.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val defaultView    = prefs.defaultView.stateIn(viewModelScope, SharingStarted.Eagerly, "WEEK")
    val showWeekends   = prefs.showWeekends.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val weatherLat     = prefs.weatherLat.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val weatherLon     = prefs.weatherLon.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val weatherEndpoint= prefs.weatherEndpoint.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val wallMode       = prefs.wallMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val wallKeepOn     = prefs.wallKeepScreenOn.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val wallIdleTimeoutSeconds = prefs.wallIdleTimeoutSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val familyName = prefs.familyName.stateIn(viewModelScope, SharingStarted.Eagerly, "Family")
    val tempUnit = prefs.tempUnit.stateIn(viewModelScope, SharingStarted.Eagerly, "F")
    val autoArchiveMonths = prefs.autoArchiveMonths.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val mealieUrl      = prefs.mealieUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val mealieToken    = prefs.mealieToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val accounts: StateFlow<List<CalendarAccount>> = accR.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    fun setDarkMode(value: Int)        = viewModelScope.launch { prefs.set(AppPreferences.KEY_DARK_MODE, value) }
    fun setThemeSeed(hex: String)      = viewModelScope.launch { prefs.set(AppPreferences.KEY_THEME_SEED, hex) }
    fun setFontSize(v: Float)          = viewModelScope.launch { prefs.set(AppPreferences.KEY_FONT_SIZE, v) }
    fun setFirstDayMon(v: Boolean)     = viewModelScope.launch { prefs.set(AppPreferences.KEY_FIRST_DAY_MON, v) }
    fun set24Hr(v: Boolean)            = viewModelScope.launch { prefs.set(AppPreferences.KEY_24HR_CLOCK, v) }
    fun setKioskMode(v: Boolean)       = viewModelScope.launch { prefs.set(AppPreferences.KEY_KIOSK_MODE, v) }
    fun setParentalPin(v: String)      = viewModelScope.launch {
        prefs.set(AppPreferences.KEY_PARENTAL_PIN, encryptor.encryptPin(v))
    }
    fun setSyncWifiOnly(v: Boolean)    = viewModelScope.launch { prefs.set(AppPreferences.KEY_SYNC_WIFI_ONLY, v) }
    fun setDefaultView(v: String)      = viewModelScope.launch { prefs.set(AppPreferences.KEY_DEFAULT_VIEW, v) }
    fun setShowWeekends(v: Boolean)    = viewModelScope.launch { prefs.set(AppPreferences.KEY_SHOW_WEEKENDS, v) }
    fun setAutoArchiveMonths(v: Int)   = viewModelScope.launch { prefs.set(AppPreferences.KEY_AUTO_ARCHIVE_MONTHS, v) }
    fun setMealieUrl(v: String)        = viewModelScope.launch { prefs.set(AppPreferences.KEY_MEALIE_URL, v) }
    fun setMealieToken(v: String)      = viewModelScope.launch { prefs.set(AppPreferences.KEY_MEALIE_TOKEN, v) }
    fun setWallMode(v: Boolean)        = viewModelScope.launch { prefs.set(AppPreferences.KEY_WALL_MODE, v) }
    fun setWallKeepOn(v: Boolean)      = viewModelScope.launch { prefs.set(AppPreferences.KEY_WALL_KEEP_ON, v) }
    fun setWallIdleTimeoutSeconds(v: Int) = viewModelScope.launch { prefs.set(AppPreferences.KEY_WALL_IDLE_SECS, v) }
    fun setFamilyName(v: String) = viewModelScope.launch { prefs.set(AppPreferences.KEY_FAMILY_NAME, v) }
    fun setTempUnit(v: String) = viewModelScope.launch { prefs.set(AppPreferences.KEY_TEMP_UNIT, v) }
    fun setWeatherLat(v: String)       = viewModelScope.launch { prefs.set(AppPreferences.KEY_WEATHER_LAT, v) }
    fun setWeatherLon(v: String)       = viewModelScope.launch { prefs.set(AppPreferences.KEY_WEATHER_LON, v) }
    fun setWeatherEndpoint(v: String)  = viewModelScope.launch { prefs.set(AppPreferences.KEY_WEATHER_ENDPOINT, v) }

    suspend fun saveAccountSync(account: CalendarAccount) {
        accR.save(account)
    }

    fun saveAccount(account: CalendarAccount) {
        viewModelScope.launch { accR.save(account) }
    }

    fun deleteAccount(account: CalendarAccount) {
        viewModelScope.launch { accR.delete(account) }
    }

    fun syncNow(context: Context, accountId: Long = -1L) {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncStatus.value = "Syncing\u2026"
            val result = withContext(Dispatchers.IO) {
                val db = AppDatabase.getInstance(context)
                val engine = CalDAVSyncEngine(db, EncryptedPassword(context))
                val accounts = if (accountId == -1L) {
                    db.calendarAccountDao().getAll().filter { it.enabled }
                } else {
                    listOfNotNull(db.calendarAccountDao().getById(accountId))
                }
                accounts.map { engine.syncAccount(it) }
            }
            _syncStatus.value = summarize(result)
            _isSyncing.value = false
        }
    }

    private fun summarize(results: List<CalDAVSyncEngine.SyncResult>): String {
        val ok   = results.count { it.isSuccess }
        val fail = results.count { !it.isSuccess }
        val events = results.sumOf { it.eventsImported }
        val tasks = results.sumOf { it.tasksImported }
        return when {
            results.isEmpty() -> "No accounts configured"
            fail == 0 -> if (events + tasks == 0)
                "Sync complete: ${ok} account(s), no new events"
            else
                "Sync complete: $events events, $tasks tasks"
            ok > 0 -> "Partially synced: $ok ok, $fail failed"
            else -> "Sync failed: ${results.first().error}"
        }
    }

    fun encodePassword(raw: String): String =
        EncryptedPassword(getApplication()).encrypt(raw)

    suspend fun testConnection(url: String, user: String, pass: String) =
        accR.testConnection(url, user, pass)
}
