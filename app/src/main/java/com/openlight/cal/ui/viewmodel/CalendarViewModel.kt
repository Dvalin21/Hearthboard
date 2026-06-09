package com.openlight.cal.ui.viewmodel

import android.app.Application
import androidx.lifecycle.*
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.model.*
import com.openlight.cal.data.sync.CalDAVClient
import com.openlight.cal.data.sync.CalDAVSyncEngine
import com.openlight.cal.data.sync.ICalParser
import com.openlight.cal.data.weather.DailyForecast
import com.openlight.cal.data.weather.WeatherApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
    val pendingInvitations: StateFlow<List<CalendarEvent>> = eventsThisMonth.map { events ->
        events.filter { it.calendarPath.contains("inbox", ignoreCase = true) && !it.isCancelled }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun acceptInvitation(event: CalendarEvent) {
        viewModelScope.launch {
            val accounts = (getApplication<HearthboardApp>()).accountRepository.getAllFlow().first()
            val target = accounts.firstOrNull { a ->
                !event.calendarPath.contains("inbox", ignoreCase = true) || a.id != event.accountId
            } ?: accounts.firstOrNull() ?: return@launch

            val copy = event.copy(
                id           = 0L,
                accountId    = target.id,
                calendarPath = "",
                etag         = "",
                isLocalOnly  = false
            )
            repo.saveEvent(copy, target.id)
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
