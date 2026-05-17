package com.openlight.cal.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.openlight.cal.OpenLightApp
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.*
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.preferences.EncryptedPassword
import com.openlight.cal.data.repository.*
import com.openlight.cal.data.sync.CalDAVClient
import com.openlight.cal.data.sync.CalDAVSyncWorker
import com.openlight.cal.data.sync.ICalParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*

// ─────────────────────────────────────────────────────────────
// Calendar ViewModel
// ─────────────────────────────────────────────────────────────
class CalendarViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as OpenLightApp).calendarRepository

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

    fun setViewMode(mode: String) { _viewMode.value = mode }
    fun setSelectedDate(date: LocalDate) { _selectedDate.value = date }
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

    private val repo    = (app as OpenLightApp).taskRepository
    private val personR = (app as OpenLightApp).personRepository

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

    private val repo = (app as OpenLightApp).personRepository

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

    private val prefs  = (app as OpenLightApp).preferences
    private val accR   = (app as OpenLightApp).accountRepository

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
    fun setParentalPin(v: String)      = viewModelScope.launch { prefs.set(AppPreferences.KEY_PARENTAL_PIN, v) }
    fun setSyncWifiOnly(v: Boolean)    = viewModelScope.launch { prefs.set(AppPreferences.KEY_SYNC_WIFI_ONLY, v) }
    fun setDefaultView(v: String)      = viewModelScope.launch { prefs.set(AppPreferences.KEY_DEFAULT_VIEW, v) }
    fun setShowWeekends(v: Boolean)    = viewModelScope.launch { prefs.set(AppPreferences.KEY_SHOW_WEEKENDS, v) }

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
        return try {
            val db = AppDatabase.getInstance(context)
            val encryptor = EncryptedPassword(context)

            val accounts = if (accountId == -1L) {
                db.calendarAccountDao().getAll().filter { it.enabled }
            } else {
                listOfNotNull(db.calendarAccountDao().getById(accountId))
            }

            if (accounts.isEmpty()) {
                return "No accounts configured"
            }

            var successCount = 0
            var failCount = 0
            var eventsImported = 0
            var tasksImported = 0
            var lastError = ""

            for (account in accounts) {
                try {
                    val pass = encryptor.decrypt(account.passwordEncrypted)
                    val client = CalDAVClient(account.serverUrl, account.username, pass)

                    // Step 1: Discover calendars - this will fail fast with bad credentials
                    val cals = client.discoverCalendars()
                    if (cals.isEmpty()) {
                        failCount++
                        lastError = "No calendars found - check server URL and credentials"
                        continue
                    }

                    // Step 2: Get the first calendar path (or let user specify)
                    val calendarPath = account.calendarPath.ifBlank { cals.first().path }
                    
                    // Step 3: Get server ETags to find what needs syncing
                    val serverEtags = client.getETagList(calendarPath)
                    if (serverEtags.isEmpty()) {
                        // Calendar exists but is empty - that's fine
                        Log.d("SyncDirect", "Calendar is empty for ${account.displayName}")
                    } else {
                        // Step 4: Fetch new/changed items via multiget
                        val localEvents = db.calendarEventDao().getByAccount(account.id)
                        val localTasks = db.taskDao().getByAccount(account.id)
                        val localEventPaths = localEvents.associateBy { it.calendarPath }
                        val localTaskPaths = localTasks.associateBy { it.calendarPath }

                        // Find items we don't have or that have changed
                        val toFetch = serverEtags.filter { (href, etag) ->
                            val eventMatch = localEventPaths[href]?.etag == etag
                            val taskMatch = localTaskPaths[href]?.etag == etag
                            !eventMatch && !taskMatch
                        }.map { it.href }

                        if (toFetch.isNotEmpty()) {
                            // Fetch in chunks
                            val chunks = toFetch.chunked(50)
                            for (chunk in chunks) {
                                val resources = client.multiGet(calendarPath, chunk)
                                for (res in resources) {
                                    val parsed = ICalParser.parse(res.ical, account.id, res.href)
                                    
                                    // Insert events
                                    for (event in parsed.events) {
                                        val existing = db.calendarEventDao().getByUid(event.uid, account.id)
                                        db.calendarEventDao().insert(
                                            event.copy(id = existing?.id ?: 0, etag = res.etag, calendarPath = res.href)
                                        )
                                        eventsImported++
                                    }
                                    
                                    // Insert tasks
                                    for (task in parsed.tasks) {
                                        val existing = db.taskDao().getByUid(task.uid, account.id)
                                        db.taskDao().insert(
                                            task.copy(id = existing?.id ?: 0, etag = res.etag, calendarPath = res.href)
                                        )
                                        tasksImported++
                                    }
                                }
                            }
                        }
                    }

                    // Step 5: Update account with sync timestamp and discovered path
                    db.calendarAccountDao().update(
                        account.copy(
                            lastSyncMs = System.currentTimeMillis(),
                            calendarPath = calendarPath
                        )
                    )
                    successCount++
                    Log.i("SyncDirect", "Synced ${account.displayName}: $eventsImported events, $tasksImported tasks")

                } catch (e: Exception) {
                    Log.e("SyncDirect", "Failed for ${account.displayName}: ${e.message}")
                    failCount++
                    lastError = e.message ?: "Unknown error"
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

    fun encodePassword(raw: String): String =
        EncryptedPassword(getApplication()).encrypt(raw)

    suspend fun testConnection(url: String, user: String, pass: String) =
        accR.testConnection(url, user, pass)
}
