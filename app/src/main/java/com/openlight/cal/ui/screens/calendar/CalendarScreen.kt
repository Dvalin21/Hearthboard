package com.openlight.cal.ui.screens.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.model.*
import com.openlight.cal.data.weather.DailyForecast
import com.openlight.cal.ui.components.*
import com.openlight.cal.ui.theme.LocalWallMode
import com.openlight.cal.ui.viewmodel.CalendarViewModel
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

// ─────────────────────────────────────────────────────────────
// Calendar Screen (top-level)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    people: List<Person>,
    onAddEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewMode       by viewModel.viewMode.collectAsState()
    val selectedDate   by viewModel.selectedDate.collectAsState()
    val events         by viewModel.filteredEvents.collectAsState()
    val countdowns     by viewModel.countdowns.collectAsState()
    val pendingInvites by viewModel.pendingInvitations.collectAsState()
    val forecasts      by viewModel.forecasts.collectAsState()
    val personFilterId by viewModel.personFilter.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {

        // ── Top bar ──────────────────────────────────────────
        CalendarTopBar(
            title      = calendarTitle(viewMode, selectedDate),
            viewMode   = viewMode,
            onViewMode = viewModel::setViewMode,
            onPrev     = viewModel::navigatePrev,
            onNext     = viewModel::navigateNext,
            onToday    = viewModel::goToday,
            onAdd      = onAddEvent
        )

        // ── Compact header strips (60dp single row, hidden when empty) ──
        // The original layout used a tall countdown LazyRow (120dp cards) and
        // a vertical Column of full-width invitation rows. On a phone in
        // landscape this could eat 200dp of vertical space before the
        // calendar grid even rendered. We collapse both into a single 60dp
        // chip rail above the calendar.
        if (countdowns.isNotEmpty() || pendingInvites.isNotEmpty()) {
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 60.dp)
            ) {
                items(countdowns, key = { "cd-${it.id}" }) { event ->
                    CountdownChip(event = event, onClick = { viewModel.editEvent(event) })
                }
                items(pendingInvites, key = { "inv-${it.id}" }) { inv ->
                    InvitationChip(
                        title     = inv.title,
                        organizer = inv.organizerEmail.takeIf { it.isNotBlank() }?.let { "from $it" } ?: "",
                        onAccept  = { viewModel.acceptInvitation(inv) },
                        onClick   = { viewModel.editEvent(inv) }
                    )
                }
            }
            HorizontalDivider()
        }

        // ── Person filter ────────────────────────────────────
        if (people.size > 1) {
            PersonFilterRow(
                people     = people,
                selectedId = personFilterId,
                onSelect   = viewModel::setPersonFilter,
                modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // ── Calendar body ────────────────────────────────────
        Box(modifier = Modifier.weight(1f)) {
            when (viewMode) {
                "MONTH"  -> MonthView(selectedDate, events, forecasts, people, onDayClick = viewModel::setSelectedDate, onEventClick = viewModel::editEvent)
                "WEEK"   -> WeekView(selectedDate, events, forecasts, people, onDayClick = viewModel::setSelectedDate, onEventClick = viewModel::editEvent)
                "DAY"    -> DayView(selectedDate, events, people, onEventClick = viewModel::editEvent)
                "AGENDA" -> AgendaView(events, people, viewModel::editEvent)
                else     -> MonthView(selectedDate, events, forecasts, people, onDayClick = viewModel::setSelectedDate, onEventClick = viewModel::editEvent)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    title: String,
    viewMode: String,
    onViewMode: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAdd: () -> Unit
) {
    TopAppBar(
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        },
        actions = {
            // View mode tabs
            val modes = listOf("MONTH", "WEEK", "DAY", "AGENDA")
            val labels = listOf("Mo", "Wk", "Dy", "Ag")
            modes.forEachIndexed { i, mode ->
                TextButton(
                    onClick = { onViewMode(mode) },
                    colors  = ButtonDefaults.textButtonColors(
                        contentColor = if (viewMode == mode)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { Text(labels[i], fontWeight = if (viewMode == mode) FontWeight.Bold else FontWeight.Normal) }
            }
            IconButton(onClick = onToday) {
                Icon(Icons.Default.Today, "Today")
            }
            IconButton(onClick = onPrev) {
                Icon(Icons.Default.ChevronLeft, "Previous")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.ChevronRight, "Next")
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, "Add event")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

private fun calendarTitle(viewMode: String, date: LocalDate): String {
    return when (viewMode) {
        "MONTH"  -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
        "WEEK"   -> {
            val weekStart = date.with(java.time.DayOfWeek.MONDAY)
            val weekEnd   = weekStart.plusDays(6)
            if (weekStart.month == weekEnd.month)
                "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – ${weekEnd.dayOfMonth}, ${weekEnd.year}"
            else
                "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – ${weekEnd.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
        }
        "DAY"    -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
        "AGENDA" -> "Upcoming"
        else     -> date.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
}

// ─────────────────────────────────────────────────────────────
// Month View — 6-week grid
// ─────────────────────────────────────────────────────────────
@Composable
fun MonthView(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    forecasts: Map<LocalDate, DailyForecast>,
    people: List<Person>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val wall  = LocalWallMode.current

    // Cache the grid origin per month (was being recalculated every recompose).
    val gridStart = remember(selectedDate) {
        val firstDay = selectedDate.withDayOfMonth(1)
        firstDay.with(java.time.DayOfWeek.MONDAY).let {
            if (it.isAfter(firstDay)) it.minusWeeks(1) else it
        }
    }

    // Group events by their local date ONCE, instead of re-filtering
    // (and re-converting startMs → LocalDate) for every one of the 42
    // grid cells on every recomposition. On a calendar with ~200 events
    // this drops 8,400 conversions per recompose to 200.
    val eventsByDay: Map<LocalDate, List<CalendarEvent>> = remember(events) {
        val zone = ZoneId.systemDefault()
        events.groupBy { ev ->
            Instant.ofEpochMilli(ev.startMs).atZone(zone).toLocalDate()
        }
    }

    // Today's column index (Mon=0 … Sun=6) for the Skylight-style
    // vertical highlight stripe. Recompute when the month changes since
    // 'today' relative to gridStart shifts.
    val todayCol = remember(today, gridStart) {
        val daysFromStart = java.time.temporal.ChronoUnit.DAYS.between(gridStart, today)
        if (daysFromStart in 0..41) (daysFromStart % 7).toInt() else -1
    }

    val dowHeaders = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dowFontSize  = if (wall.active) 13.sp else 11.sp
    val headerPadV   = if (wall.active) 10.dp else 4.dp

    Column(modifier = modifier.fillMaxSize()) {
        // DOW header row — bolds today's column
        Row(modifier = Modifier.fillMaxWidth()) {
            dowHeaders.forEachIndexed { idx, dow ->
                val isTodayCol = idx == todayCol
                Text(
                    text       = dow,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                    style      = MaterialTheme.typography.labelSmall.copy(fontSize = dowFontSize),
                    fontWeight = if (isTodayCol) FontWeight.Bold else FontWeight.Normal,
                    color      = if (isTodayCol) MaterialTheme.colorScheme.primary
                                 else            MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = headerPadV))

        // 6-week grid
        val weeks = 6
        for (week in 0 until weeks) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                for (dow in 0..6) {
                    val day            = gridStart.plusDays((week * 7 + dow).toLong())
                    val isCurrentMonth = day.month == selectedDate.month
                    val isToday        = day == today
                    val isSelected     = day == selectedDate
                    val isTodayCol     = dow == todayCol
                    val dayEvents      = eventsByDay[day].orEmpty()

                    DayCell(
                        day            = day,
                        isCurrentMonth = isCurrentMonth,
                        isToday        = isToday,
                        isSelected     = isSelected,
                        isTodayColumn  = isTodayCol,
                        events         = dayEvents,
                        forecasts      = forecasts,
                        people         = people,
                        onClick        = { onDayClick(day) },
                        onEventClick   = onEventClick,
                        modifier       = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    isTodayColumn: Boolean,
    events: List<CalendarEvent>,
    forecasts: Map<LocalDate, DailyForecast> = emptyMap(),
    people: List<Person>,
    onClick: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val wf   = forecasts[day]
    val wall = LocalWallMode.current

    // Sizes scale up in wall mode for arm's-length-or-farther viewing.
    val dateBubble   = if (wall.active) 32.dp else 22.dp
    val dateFontSize = if (wall.active) 14.sp else 11.sp
    val chipFontSize = if (wall.active) 12.sp else 9.sp
    val chipPadV     = if (wall.active) 3.dp else 1.dp
    val chipGap      = if (wall.active) 3.dp else 1.dp
    val tempFontSize = if (wall.active) 10.sp else 8.sp
    val maxVisible   = if (wall.active) 4 else 3

    // Today-column background tint runs the full height of the cell —
    // it's a faint vertical stripe the eye lands on first.
    val cellBg = when {
        isTodayColumn && isCurrentMonth -> MaterialTheme.colorScheme.primary.copy(alpha = 0.04f)
        else                            -> Color.Transparent
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(cellBg)
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        // Day number + weather
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(dateBubble)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday    -> MaterialTheme.colorScheme.secondaryContainer
                            else       -> Color.Transparent
                        }
                    )
            ) {
                Text(
                    text       = day.dayOfMonth.toString(),
                    fontSize   = dateFontSize,
                    color = when {
                        isSelected      -> MaterialTheme.colorScheme.onPrimary
                        !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        else            -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
                )
            }
            if (wf != null && isCurrentMonth) {
                Spacer(Modifier.weight(1f))
                Text(
                    text     = "${wf.iconChar}${wf.tempHigh.toInt()}°",
                    fontSize = tempFontSize,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(2.dp))

        // Skylight-style event chips: a 3dp color band on the left
        // (encoding person/event identity), then the title in muted
        // text on a neutral background. Reads cleanly at room distance
        // and doesn't shout at you with saturated fills.
        events.take(maxVisible).forEach { event ->
            val color = eventColor(event, people)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .clickable { onEventClick(event) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left color band
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(color)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text     = event.title,
                    fontSize = chipFontSize,
                    color    = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = chipPadV, horizontal = 2.dp)
                )
            }
            Spacer(Modifier.height(chipGap))
        }
        if (events.size > maxVisible) {
            Text(
                text     = "+${events.size - maxVisible} more",
                fontSize = chipFontSize,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 1.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Week View
// ─────────────────────────────────────────────────────────────
@Composable
fun WeekView(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    forecasts: Map<LocalDate, DailyForecast>,
    people: List<Person>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekStart = selectedDate.with(java.time.DayOfWeek.MONDAY)
        .let { if (it.isAfter(selectedDate)) it.minusWeeks(1) else it }
    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxSize()) {
        // Day headers
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Spacer(Modifier.width(48.dp))  // time gutter
            for (i in 0..6) {
                val day = weekStart.plusDays(i.toLong())
                val isToday = day == today
                val wf = forecasts[day]
                Column(
                    modifier            = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        day.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier         = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primary
                                else Color.Transparent
                            )
                            .clickable { onDayClick(day) }
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            style      = MaterialTheme.typography.bodyMedium,
                            color      = if (isToday) MaterialTheme.colorScheme.onPrimary
                                         else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    if (wf != null) {
                        Text(
                            text  = "${wf.iconChar}${wf.tempHigh.toInt()}°",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        HorizontalDivider()

        // Scrollable hour grid
        val scrollState = rememberScrollState()
        Row(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            // Hour labels
            Column(modifier = Modifier.width(48.dp)) {
                for (hour in 0..23) {
                    Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.TopEnd) {
                        Text(
                            text     = if (hour == 0) "" else "%02d:00".format(hour),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                        )
                    }
                }
            }
            // Day columns
            for (i in 0..6) {
                val day = weekStart.plusDays(i.toLong())
                val dayEvents = events.filter { event ->
                    val d = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
                    d == day && !event.isAllDay
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp * 60)  // 24hrs * 60dp each
                ) {
                    // Hour lines
                    Column {
                        for (hour in 0..23) {
                            HorizontalDivider(
                                modifier  = Modifier.padding(top = 60.dp),
                                thickness = 0.5.dp,
                                color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }
                    }
                    // Events
                    dayEvents.forEach { event ->
                        val startHour = Instant.ofEpochMilli(event.startMs)
                            .atZone(ZoneId.systemDefault()).hour
                        val startMin  = Instant.ofEpochMilli(event.startMs)
                            .atZone(ZoneId.systemDefault()).minute
                        val durationMin = ((event.endMs - event.startMs) / 60_000).coerceAtLeast(30L)
                        val topOffset   = (startHour * 60 + startMin).dp
                        val heightDp    = (durationMin.toInt()).coerceAtMost(120).dp

                        val color = eventColor(event, people)
                        Box(
                            modifier = Modifier
                                .offset(y = topOffset)
                                .height(heightDp)
                                .fillMaxWidth()
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .clickable { onEventClick(event) }
                                .padding(4.dp)
                        ) {
                            Text(
                                text     = event.title,
                                style    = MaterialTheme.typography.labelSmall,
                                color    = Color.White,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Day View
// ─────────────────────────────────────────────────────────────
@Composable
fun DayView(
    date: LocalDate,
    events: List<CalendarEvent>,
    people: List<Person>,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val dayEvents = events.filter { event ->
        val d = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        d == date
    }.sortedBy { it.startMs }

    val allDay  = dayEvents.filter { it.isAllDay }
    val timed   = dayEvents.filter { !it.isAllDay }

    Column(modifier = modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) {
            SectionHeader("All Day")
            allDay.forEach { event ->
                EventCard(event = event, people = people, onClick = { onEventClick(event) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        val scrollState = rememberScrollState()
        Row(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            Column(modifier = Modifier.width(48.dp)) {
                for (hour in 0..23) {
                    Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.TopEnd) {
                        Text(
                            text     = if (hour == 0) "" else "%02d:00".format(hour),
                            style    = MaterialTheme.typography.labelSmall,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp, top = 2.dp)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).height(24.dp * 60)) {
                Column {
                    for (hour in 0..23) {
                        HorizontalDivider(
                            modifier  = Modifier.padding(top = 60.dp),
                            thickness = 0.5.dp,
                            color     = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
                timed.forEach { event ->
                    val startZdt = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault())
                    val topOffset = (startZdt.hour * 60 + startZdt.minute).dp
                    val duration  = (((event.endMs - event.startMs) / 60_000).coerceAtLeast(30L).toInt()).dp
                    val color     = eventColor(event, people)
                    Box(
                        modifier = Modifier
                            .offset(y = topOffset)
                            .height(duration)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                            .clickable { onEventClick(event) }
                            .padding(8.dp)
                    ) {
                        Column {
                            Text(event.title, style = MaterialTheme.typography.bodySmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            if (event.location.isNotBlank()) {
                                Text(event.location, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Agenda View
// ─────────────────────────────────────────────────────────────
@Composable
fun AgendaView(
    events: List<CalendarEvent>,
    people: List<Person>,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = events
        .filter { !it.isCancelled }
        .sortedBy { it.startMs }
        .groupBy {
            Instant.ofEpochMilli(it.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        }

    if (grouped.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No upcoming events", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val today = LocalDate.now()

    LazyColumn(
        modifier       = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        grouped.forEach { (date, dayEvents) ->
            item {
                val label = when (date) {
                    today            -> "Today"
                    today.plusDays(1)-> "Tomorrow"
                    else             -> date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text       = label,
                        style      = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color      = if (date == today) MaterialTheme.colorScheme.primary
                                     else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    if (date == today) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }
                }
            }
            items(dayEvents) { event ->
                EventCard(
                    event    = event,
                    people   = people,
                    onClick  = { onEventClick(event) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Utility
// ─────────────────────────────────────────────────────────────
private fun eventColor(event: CalendarEvent, people: List<Person>): Color {
    // Slate primary — matches Theme.kt LightColorScheme.primary
    val fallback = Color(0xFF4A6178)
    // Event's own color (set at creation, preserved forever) takes precedence
    if (event.colorHex.isNotBlank()) {
        return runCatching { Color(android.graphics.Color.parseColor(event.colorHex)) }.getOrElse { fallback }
    }
    // Fall back to person color if assigned (identity, not override)
    if (event.personIds.isNotBlank()) {
        val firstId = event.personIds.split(",").firstOrNull()?.trim()?.toLongOrNull()
        val person  = people.find { it.id == firstId }
        if (person != null) {
            return runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }.getOrElse { fallback }
        }
    }
    return fallback
}
