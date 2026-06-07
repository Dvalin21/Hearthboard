package com.openlight.cal.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.*
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.preferences.EncryptedPassword
import com.openlight.cal.data.repository.*
import com.openlight.cal.data.sync.CalDAVClient
import com.openlight.cal.data.sync.CalDAVSyncEngine
import com.openlight.cal.data.sync.ICalParser
import com.openlight.cal.data.weather.DailyForecast
import com.openlight.cal.data.weather.WeatherApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*

// ─────────────────────────────────────────────────────────────
// Calendar ViewModel
// ─────────────────────────────────────────────────────────────
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as HearthboardApp).calendarRepository
    private val prefs = (app as HearthboardApp).preferences
    private val weatherApi = WeatherApi()

    private val _viewMode = MutableStateFlow("MONTH")
    val viewMode: StateFlow<String> = _viewMode

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _showAddEvent = MutableStateFlow(false)
    val showAddEvent: StateFlow<Boolean> = _showAddEvent

    private val _editEvent = MutableStateFlow<CalendarEvent?>(null)
    val editEvent: StateFlow<CalendarEvent?> = _editEvent

    @OptIn(ExperimentalCoroutinesApi::class)
    val eventsThisMonth: StateFlow<List<CalendarEvent>> = _selectedDate
        .flatMapLatest { date ->
            val (start, end) = repo.getMonthRange(date.year, date.monthValue)
            val bufStart = start - java.time.Duration.ofDays(7).toMillis()
            val bufEnd   = end   + java.time.Duration.ofDays(7).toMillis()
            repo.getEventsInRange(bufStart, bufEnd)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val countdowns: StateFlow<List<CalendarEvent>> = repo.getCountdowns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Weather ────────────────────────────────────────────────
    private val _forecasts = MutableStateFlow<Map<LocalDate, DailyForecast>>(emptyMap())
    val forecasts: StateFlow<Map<LocalDate, DailyForecast>> = _forecasts

    init {
        // Fetch weather on month change, debounced
        @OptIn(kotlinx.coroutines.FlowPreview::class)
        viewModelScope.launch {
            _selectedDate
                .debounce(500)
                .collect { fetchWeather() }
        }
    }

    private suspend fun fetchWeather() {
        val latStr = prefs.weatherLat.first()
        val lonStr = prefs.weatherLon.first()
        val lat = latStr.toDoubleOrNull() ?: return
        val lon = lonStr.toDoubleOrNull() ?: return
        val endpoint = prefs.weatherEndpoint.first().ifBlank { "https://api.open-meteo.com/v1/forecast" }
        try {
            val result = weatherApi.fetchForecast(lat, lon, endpoint)
            _forecasts.value = result.associateBy { it.date }
        } catch (_: Exception) {
            // Silent fail — weather is cosmetic
        }
    }

    private val _personFilter = MutableStateFlow(0L) // 0 = all
    val personFilter: StateFlow<Long> = _personFilter

    val filteredEvents: StateFlow<List<CalendarEvent>> = combine(eventsThisMonth, _personFilter) { events, filterId ->
        if (filterId == 0L) events
        else events.filter { event ->
            event.personIds.split(",")
                .map(String::trim)
                .any { trimmed ->
                    trimmed.isNotEmpty() && trimmed.toLongOrNull() == filterId
                }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Inbox / Pending invitations ────────────────────────────
    /** Events from CalDAV inbox calendars (meeting invitations). */
    val pendingInvitations: StateFlow<List<CalendarEvent>> = eventsThisMonth.map { events ->
        events.filter { it.calendarPath.contains("inbox", ignoreCase = true) && !it.isCancelled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Accept an invitation: copy to personal calendar, remove from inbox. */
    fun acceptInvitation(event: CalendarEvent) {
        viewModelScope.launch {
            // Find first non-inbox account as target
            val accounts = (getApplication<HearthboardApp>()).accountRepository.getAllFlow().first()
            val target = accounts.firstOrNull { a ->
                !event.calendarPath.contains("inbox", ignoreCase = true) || a.id != event.accountId
            } ?: accounts.firstOrNull() ?: return@launch

            // Copy event to personal calendar
            val copy = event.copy(
                id           = 0L,
                accountId    = target.id,
                calendarPath = "",   // will be set by CalDAV push
                etag         = "",
                isLocalOnly  = false
            )
            repo.saveEvent(copy, target.id)

            // Delete from inbox
            repo.deleteEvent(event)
        }
    }

    fun setViewMode(mode: String) { _viewMode.value = mode }
    fun setSelectedDate(date: LocalDate) { _selectedDate.value = date }
    fun setPersonFilter(personId: Long) { _personFilter.value = personId }
    fun showAddEvent() { _showAddEvent.value = true }
    fun hideAddEvent() { _showAddEvent.value = false; _editEvent.value = null }
    fun editEvent(event: CalendarEvent) { _editEvent.value = event; _showAddEvent.value = true }

    fun navigatePrev() {
        _selectedDate.update {
            when (_viewMode.value) {
                "MONTH"  -> it.minusMonths(1)
                "WEEK"   -> it.minusWeeks(1)
                "DAY"    -> it.minusDays(1)
                else     -> it.minusMonths(1)
            }
        }
    }

    fun navigateNext() {
        _selectedDate.update {
            when (_viewMode.value) {
                "MONTH"  -> it.plusMonths(1)
                "WEEK"   -> it.plusWeeks(1)
                "DAY"    -> it.plusDays(1)
                else     -> it.plusMonths(1)
            }
        }
    }

    fun goToday() { _selectedDate.value = LocalDate.now() }

    fun saveEvent(event: CalendarEvent, accountId: Long?) {
        viewModelScope.launch {
            repo.saveEvent(event, accountId)
            hideAddEvent()
        }
    }

    fun deleteEvent(event: CalendarEvent) {
        viewModelScope.launch { repo.deleteEvent(event) }
    }
}

// ─────────────────────────────────────────────────────────────
// Task ViewModel
// ─────────────────────────────────────────────────────────────
class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val repo    = (app as HearthboardApp).taskRepository
    private val personR = (app as HearthboardApp).personRepository

    val activeTasks: StateFlow<List<Task>> = repo.getActiveFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTasks: StateFlow<List<Task>> = repo.getAllTasksFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val people: StateFlow<List<Person>> = personR.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPersonFilter = MutableStateFlow(0L) // 0 = all
    val selectedPersonFilter: StateFlow<Long> = _selectedPersonFilter

    val filteredTasks: StateFlow<List<Task>> = combine(allTasks, _selectedPersonFilter) { tasks, personId ->
        if (personId == 0L) tasks
        else tasks.filter { it.assignedPersonId == personId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setPersonFilter(personId: Long) { _selectedPersonFilter.value = personId }

    fun saveTask(task: Task, accountId: Long? = null) {
        viewModelScope.launch { repo.saveTask(task, accountId) }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch { repo.setCompleted(task.id, !task.isCompleted) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { repo.deleteTask(task) }
    }
}

// ─────────────────────────────────────────────────────────────
// Person ViewModel
// ─────────────────────────────────────────────────────────────
class PersonViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as HearthboardApp).personRepository

    val people: StateFlow<List<Person>> = repo.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun savePerson(person: Person) {
        viewModelScope.launch { repo.save(person) }
    }

    fun updatePerson(person: Person) {
        viewModelScope.launch { repo.update(person) }
    }

    fun deletePerson(person: Person) {
        viewModelScope.launch { repo.delete(person) }
    }
}

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
    val defaultView    = prefs.defaultView.stateIn(viewModelScope, SharingStarted.Eagerly, "MONTH")
    val showWeekends   = prefs.showWeekends.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val weatherLat     = prefs.weatherLat.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val weatherLon     = prefs.weatherLon.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val weatherEndpoint= prefs.weatherEndpoint.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val wallMode       = prefs.wallMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val wallKeepOn     = prefs.wallKeepScreenOn.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val autoArchiveMonths = prefs.autoArchiveMonths.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val mealieUrl      = prefs.mealieUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val mealieToken    = prefs.mealieToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val accounts: StateFlow<List<CalendarAccount>> = accR.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncStatus = MutableStateFlow<String?>(null)
    val syncStatus: StateFlow<String?> = _syncStatus

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

    fun syncNow(context: android.content.Context, accountId: Long = -1L) {
        viewModelScope.launch {
            _syncStatus.value = "Syncing…"
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

// ─────────────────────────────────────────────────────────────
// Rewards ViewModel
// ─────────────────────────────────────────────────────────────
class RewardsViewModel(app: Application) : AndroidViewModel(app) {

    private val rewardRepo = (app as HearthboardApp).rewardRepository
    private val personRepo = (app as HearthboardApp).personRepository

    // Catalog
    val allRewards: StateFlow<List<Reward>> = rewardRepo.allRewardsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val enabledRewards: StateFlow<List<Reward>> = rewardRepo.enabledRewardsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // People (for picking who's redeeming)
    val people: StateFlow<List<Person>> = personRepo.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Redemption history (most recent first)
    val history: StateFlow<List<RedeemedReward>> = rewardRepo.historyFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Live balance for any person; caller collects in UI. */
    fun balanceFlow(personId: Long): Flow<Int> = rewardRepo.balanceFlow(personId)

    // ── Catalog mutations ───────────────────────────────────
    fun saveReward(reward: Reward) = viewModelScope.launch {
        rewardRepo.saveReward(reward)
    }

    fun deleteReward(reward: Reward) = viewModelScope.launch {
        rewardRepo.deleteReward(reward)
    }

    // ── Redemption ──────────────────────────────────────────
    private val _lastRedeemResult = MutableStateFlow<RewardRepository.RedeemResult?>(null)
    val lastRedeemResult: StateFlow<RewardRepository.RedeemResult?> = _lastRedeemResult.asStateFlow()

    fun redeem(rewardId: Long, personId: Long, note: String = "") = viewModelScope.launch {
        _lastRedeemResult.value = rewardRepo.redeem(rewardId, personId, note)
    }

    /** Clear the result after the UI has shown a snackbar/dialog. */
    fun clearRedeemResult() { _lastRedeemResult.value = null }

    /** Parent-side: undo an erroneous redemption (refunds stars). */
    fun undoRedemption(redeemed: RedeemedReward) = viewModelScope.launch {
        rewardRepo.undoRedemption(redeemed)
    }
}
