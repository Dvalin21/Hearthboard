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
import com.openlight.cal.data.sync.CalDAVSyncWorker
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
            // Include prev/next month buffer for grid display
            val bufStart = start - (7 * 86_400_000L)
            val bufEnd   = end   + (7 * 86_400_000L)
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
        else events.filter { it.personIds.split(",").any { pid -> pid.trim().toLongOrNull() == filterId } }
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
    val themeSeedColor = prefs.themeSeedColor.stateIn(viewModelScope, SharingStarted.Eagerly, "#2196F3")
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
            val result = syncAccountDirect(context, accountId)
            _syncStatus.value = result
        }
    }

    /**
     * Run sync directly (not via WorkManager) so we can capture and show errors.
     */
    private suspend fun syncAccountDirect(context: android.content.Context, accountId: Long): String {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(context)
                val encryptor = EncryptedPassword(context)

                val accounts = if (accountId == -1L) {
                    db.calendarAccountDao().getAll().filter { it.enabled }
                } else {
                    listOfNotNull(db.calendarAccountDao().getById(accountId))
                }

                if (accounts.isEmpty()) {
                    return@withContext "No accounts configured"
                }

                var successCount = 0
                var failCount = 0
                var eventsImported = 0
                var tasksImported = 0
                var lastError = ""

                for (account in accounts) {
                    try {
                        val pass = encryptor.decrypt(account.passwordEncrypted)
                        
                        Log.d("SyncDirect", "=== Starting sync for ${account.displayName} ===")
                        Log.d("SyncDirect", "Server URL: ${account.serverUrl}")
                        Log.d("SyncDirect", "Existing calendarPath: ${account.calendarPath}")

                        // Determine server base URL
                        // If user entered full calendar URL (contains /Calendar/), extract the server base
                        var serverUrlForClient = account.serverUrl
                        val enteredUrl = account.serverUrl.trimEnd('/')
                        
                        if (enteredUrl.contains("/Calendar/") || enteredUrl.contains("/calendar/")) {
                            val sogoIndex = enteredUrl.indexOf("/SOGo/dav/")
                            if (sogoIndex > 0) {
                                serverUrlForClient = enteredUrl.substring(0, sogoIndex)
                            }
                            Log.d("SyncDirect", "SOGo URL detected - base: $serverUrlForClient, calendar URL: $enteredUrl")
                        }
                        
                        // Create client with appropriate base URL
                        val client = CalDAVClient(serverUrlForClient, account.username, pass)
                        
                        // Always run discovery to find ALL calendars (personal + inbox + shared)
                        Log.d("SyncDirect", "Attempting calendar discovery...")
                        val allCalendars = client.discoverCalendars()
                        Log.d("SyncDirect", "Discovery returned ${allCalendars.size} calendars")
                        
                        val calendarsToSync = if (allCalendars.isEmpty()) {
                            // Discovery failed - fall back to existing path or entered URL
                            val fallback = account.calendarPath.ifBlank { enteredUrl }
                            Log.d("SyncDirect", "Discovery empty, using fallback: $fallback")
                            listOf(CalDAVClient.CalendarInfo(fallback, account.displayName, "", account.calendarPath.contains("VTODO")))
                        } else {
                            allCalendars
                        }
                        
                        // Pre-fetch local state
                        val localEvents = db.calendarEventDao().getByAccount(account.id)
                        val localEventPaths = localEvents.associateBy { it.calendarPath }
                        val localTasks = db.taskDao().getByAccount(account.id)
                        val localTaskPaths = localTasks.associateBy { it.calendarPath }

                        // Build email→personId lookup for organizer matching
                        val emailToPersonId = db.personDao().getAll()
                            .filter { it.email.isNotBlank() }
                            .associateBy { it.email.lowercase() }

                        val masterServerHrefs = mutableSetOf<String>()
                        var primaryPath = ""
                        var calendarIndex = 0
                        
                        for (cal in calendarsToSync) {
                            calendarIndex++
                            val path = cal.path
                            if (primaryPath.isBlank()) primaryPath = path
                            
                            Log.d("SyncDirect", "Syncing calendar [$calendarIndex/${calendarsToSync.size}]: ${cal.displayName}")
                            
                            // Get server ETag list for this calendar
                            val serverEtags = client.getETagList(path)
                            masterServerHrefs.addAll(serverEtags.map { it.href })
                            Log.d("SyncDirect", "ETag list returned ${serverEtags.size} items for ${cal.displayName}")
                            
                            if (serverEtags.isEmpty()) continue
                            
                            // Find new/changed items
                            val toFetch = serverEtags.filter { (href, etag) ->
                                val eventMatch = localEventPaths[href]?.etag == etag
                                val taskMatch = localTaskPaths[href]?.etag == etag
                                !eventMatch && !taskMatch
                            }.map { it.href }
                            
                            // Fetch changed items via individual GET
                            for (href in toFetch) {
                                val res = client.fetchIcs(href) ?: continue
                                val parsed = ICalParser.parse(res.ical, account.id, res.href)
                                for (event in parsed.events) {
                                    val existing = db.calendarEventDao().getByUid(event.uid, account.id)
                                    // Match organizer email to a known Person
                                    val personId = if (event.organizerEmail.isNotBlank() && existing?.personIds.isNullOrBlank()) {
                                        emailToPersonId[event.organizerEmail.lowercase()]?.id
                                    } else null
                                    db.calendarEventDao().insert(event.copy(
                                        id           = existing?.id ?: 0,
                                        etag         = res.etag,
                                        calendarPath = res.href,
                                        colorHex     = existing?.colorHex?.takeIf { it.isNotBlank() } ?: account.colorHex,
                                        personIds    = personId?.toString() ?: (existing?.personIds ?: "")
                                    ))
                                    eventsImported++
                                }
                                for (task in parsed.tasks) {
                                    val existing = db.taskDao().getByUid(task.uid, account.id)
                                    db.taskDao().insert(task.copy(id = existing?.id ?: 0, etag = res.etag, calendarPath = res.href))
                                    tasksImported++
                                }
                            }
                        }
                        
                        // Single deletion pass: remove items not present on ANY server calendar
                        for (event in localEvents) {
                            if (event.calendarPath.isNotBlank() && event.calendarPath !in masterServerHrefs) {
                                db.calendarEventDao().delete(event)
                            }
                        }
                        for (task in localTasks) {
                            if (task.calendarPath.isNotBlank() && task.calendarPath !in masterServerHrefs) {
                                db.taskDao().delete(task)
                            }
                        }
                        
                        // Update account with sync timestamp and primary path
                        db.calendarAccountDao().update(
                            account.copy(
                                lastSyncMs = System.currentTimeMillis(),
                                calendarPath = primaryPath
                            )
                        )
                        successCount++
                        Log.i("SyncDirect", "Synced ${account.displayName}: $eventsImported events, $tasksImported tasks across $calendarIndex calendar(s)")

                    } catch (e: Exception) {
                        Log.e("SyncDirect", "Failed for ${account.displayName}: ${e.message}", e)
                        failCount++
                        lastError = e.message ?: e.toString()
                    }
                }

                when {
                    successCount > 0 && failCount == 0 -> {
                        if (eventsImported == 0 && tasksImported == 0) {
                            "Sync complete: connected to $successCount account(s), no new events"
                        } else {
                            "Sync complete: $eventsImported events, $tasksImported tasks imported"
                        }
                    }
                    successCount > 0 && failCount > 0 -> "Partially synced: $successCount ok, $failCount failed"
                    failCount > 0 -> "Sync failed: $lastError"
                    else -> "Sync failed: check credentials and server URL"
                }
            } catch (e: Exception) {
                "Sync error: ${e.message}"
            }
        }
    }

    fun encodePassword(raw: String): String =
        EncryptedPassword(getApplication()).encrypt(raw)

    suspend fun testConnection(url: String, user: String, pass: String) =
        accR.testConnection(url, user, pass)
}
