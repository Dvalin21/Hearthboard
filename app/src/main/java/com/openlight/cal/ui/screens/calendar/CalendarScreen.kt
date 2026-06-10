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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.model.*
import com.openlight.cal.data.weather.DailyForecast
import com.openlight.cal.ui.components.*
import com.openlight.cal.ui.theme.LocalWallMode
import com.openlight.cal.ui.theme.WallModeState
import com.openlight.cal.ui.viewmodel.CalendarViewModel
import kotlinx.coroutines.delay
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
    val forecasts      by viewModel.forecasts.collectAsState()
    val personFilterId by viewModel.personFilter.collectAsState()
    val wall           = LocalWallMode.current

    // Pick the active person for the avatar (first non-default, fallback to first)
    val activePerson = remember(people) {
        people.firstOrNull { !it.isDefault } ?: people.firstOrNull()
    }
    val personInitial = activePerson?.initial
    val personColor   = activePerson?.let {
        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull()
    }

    // Today's forecast for the top bar
    val todayForecast = forecasts[LocalDate.now()]

    Column(modifier = modifier.fillMaxSize()) {

        // ── Skylight-style AppHeader: avatar on left, date, weather ──
        AppHeader(
            date           = selectedDate,
            temperature    = todayForecast?.let { "${it.iconChar}${it.tempHigh.toInt()}°" },
            personInitial  = personInitial,
            personColor    = personColor
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

        // ── Calendar controls: view mode + nav + add ──────────
        CalendarControls(
            viewMode   = viewMode,
            onViewMode = viewModel::setViewMode,
            onPrev     = viewModel::navigatePrev,
            onNext     = viewModel::navigateNext,
            onToday    = viewModel::goToday,
            onAdd      = onAddEvent
        )

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
                "WEEK"   -> WeekView(selectedDate, events, forecasts, people, onDayClick = viewModel::setSelectedDate, onEventClick = viewModel::editEvent, wall = wall)
                "DAY"    -> DayView(selectedDate, events, people, onEventClick = viewModel::editEvent, wall = wall)
                "AGENDA" -> AgendaView(events, people, viewModel::editEvent)
                else     -> MonthView(selectedDate, events, forecasts, people, onDayClick = viewModel::setSelectedDate, onEventClick = viewModel::editEvent)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// CalendarControls — view mode tabs, navigation, add
// ─────────────────────────────────────────────────────────────
@Composable
private fun CalendarControls(
    viewMode: String,
    onViewMode: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Navigation: Today + Prev + Next
        TextButton(
            onClick   = onToday,
            colors    = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            ),
            modifier  = Modifier.height(30.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text("Today", fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        IconButton(onClick = onPrev, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.ChevronLeft, "Previous", modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onNext, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.ChevronRight, "Next", modifier = Modifier.size(18.dp))
        }

        Spacer(Modifier.width(2.dp))

        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Spacer(Modifier.width(2.dp))

        // View mode tabs
        val modes  = listOf("MONTH", "WEEK", "DAY", "AGENDA")
        val labels = listOf("Mo", "Wk", "Dy", "Ag")
        modes.forEachIndexed { i, mode ->
            TextButton(
                onClick = { onViewMode(mode) },
                colors  = ButtonDefaults.textButtonColors(
                    contentColor = if (viewMode == mode)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier       = Modifier.height(30.dp)
            ) {
                Text(
                    labels[i],
                    fontWeight = if (viewMode == mode) FontWeight.Bold else FontWeight.Normal,
                    fontSize   = 12.sp
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Add event button (icon only, like Skylight's +)
        FilledTonalIconButton(
            onClick  = onAdd,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add event", modifier = Modifier.size(16.dp))
        }
    }

    HorizontalDivider(modifier = Modifier.padding(horizontal = 4.dp))
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

    // Split events into single-day (event chips) and multi-day (span bars).
    // Multi-day events span 2+ days and render as continuous colored bars.
    val zone = ZoneId.systemDefault()
    val singleDayEvents = remember(events) {
        events.filter { ev ->
            val startDate = Instant.ofEpochMilli(ev.startMs).atZone(zone).toLocalDate()
            val endDate   = Instant.ofEpochMilli(ev.endMs).atZone(zone).toLocalDate()
            java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) < 1
        }
    }

    // Group single-day events by their local date ONCE, instead of
    // re-filtering (and re-converting startMs → LocalDate) for every
    // one of the 42 grid cells on every recomposition. On a calendar
    // with ~200 events this drops 8,400 conversions per recompose to 200.
    val eventsByDay: Map<LocalDate, List<CalendarEvent>> = remember(singleDayEvents) {
        singleDayEvents.groupBy { ev ->
            Instant.ofEpochMilli(ev.startMs).atZone(zone).toLocalDate()
        }
    }

    // Multi-day event spans for continuous bars
    val multiDaySpans = remember(events, gridStart) {
        buildMultiDaySpans(gridStart, events, people)
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
                    val cellOffset     = week * 7 + dow
                    val cellMultiDay   = multiDaySpans[cellOffset].orEmpty()

                    DayCell(
                        day            = day,
                        isCurrentMonth = isCurrentMonth,
                        isToday        = isToday,
                        isSelected     = isSelected,
                        isTodayColumn  = isTodayCol,
                        events         = dayEvents,
                        multiDaySpans  = cellMultiDay,
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
    multiDaySpans: List<MultiDaySpan> = emptyList(),
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
                            // Orange dot for today — matches Skylight
                            isToday    -> Color(0xFFE07B39)
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

        // ── Multi-day event bars (§5.2.3) ──────────────────
        // Colored bars spanning their range at the top of each
        // spanned day cell. First-day shows title, middle shows
        // continuation fill, last shows subtle end indicator.
        multiDaySpans.forEach { span ->
            val barLabel = when {
                span.isStart && span.isEnd -> span.event.title // single-day (shouldn't happen but handle)
                span.isStart               -> span.event.title + " ›"
                span.isEnd                 -> "◂ " + span.event.title
                else                       -> "" // middle — just the color bar
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chipFontSize.value.dp + 4.dp)
                    .padding(vertical = 1.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(span.color)
                    .clickable { onEventClick(span.event) }
                    .semantics {
                        this[SemanticsProperties.Role] = Role.Button
                        contentDescription = "Multi-day: ${span.event.title}"
                    }
                    .padding(horizontal = 3.dp, vertical = 1.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (barLabel.isNotBlank()) {
                    Text(
                        text      = barLabel,
                        fontSize  = chipFontSize,
                        color     = Color.White,
                        maxLines  = 1,
                        overflow  = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // ── Single-day event chips ─────────────────────────
        events.take(maxVisible).forEach { event ->
            val chipColor = eventColor(event, people)
            val timeStr = if (!event.isAllDay) {
                Instant.ofEpochMilli(event.startMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(DateTimeFormatter.ofPattern("h:mm a"))
            } else null
            val loc = if (event.location.isNotBlank()) ", at ${event.location}" else event.location
            // Pick white or dark text depending on chip luminance
            val luma = 0.299 * chipColor.red + 0.587 * chipColor.green + 0.114 * chipColor.blue
            val chipText = if (luma > 0.5) Color(0xFF1F2A36) else Color.White
            val chipLabel = buildString {
                if (timeStr != null) append("$timeStr ")
                append(event.title)
            }
            Text(
                text      = chipLabel,
                fontSize  = chipFontSize,
                color     = chipText,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                modifier  = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(chipColor)
                    .clickable { onEventClick(event) }
                    .semantics {
                        this[SemanticsProperties.Role] = Role.Button
                        contentDescription = "Event: ${event.title}, ${timeStr}$loc"
                    }
                    .padding(horizontal = 4.dp, vertical = chipPadV)
            )
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
    wall: WallModeState = WallModeState(),
    modifier: Modifier = Modifier
) {
    val weekStart = selectedDate.with(java.time.DayOfWeek.MONDAY)
        .let { if (it.isAfter(selectedDate)) it.minusWeeks(1) else it }
    val today = LocalDate.now()

    // Wall mode: trim hour gutter to 06:00–23:00 to reduce vertical chrome
    val hourRange = if (wall.active) 6..23 else 0..23
    val hourCount = hourRange.count()

    // ── Orange time bar: current time, updates every 30s ──────
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }

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

        // ── All-day event strip ─────────────────────────────
        val zone = ZoneId.systemDefault()
        val allDayByDay = remember(events, weekStart) {
            val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }
            val result = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
            events.filter { it.isAllDay }.forEach { event ->
                val startDate = Instant.ofEpochMilli(event.startMs).atZone(zone).toLocalDate()
                val endDate   = Instant.ofEpochMilli(event.endMs).atZone(zone).toLocalDate()
                for (day in weekDays) {
                    if (!day.isBefore(startDate) && day.isBefore(endDate)) {
                        result.getOrPut(day) { mutableListOf() }.add(event)
                    }
                }
            }
            result
        }
        if (allDayByDay.isNotEmpty()) {
            val maxPerDay = 2
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Spacer(Modifier.width(48.dp))  // time gutter alignment
                for (i in 0..6) {
                    val day = weekStart.plusDays(i.toLong())
                    Column(modifier = Modifier.weight(1f)) {
                        val dayAllDay = allDayByDay[day].orEmpty()
                        dayAllDay.take(maxPerDay).forEach { event ->
                            val color = eventColor(event, people)
                            Text(
                                text      = event.title,
                                fontSize  = 8.sp,
                                color     = Color.White,
                                maxLines  = 1,
                                overflow  = TextOverflow.Ellipsis,
                                modifier  = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 0.5.dp, vertical = 0.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(color)
                                    .clickable { onEventClick(event) }
                                    .padding(horizontal = 2.dp, vertical = 1.dp)
                            )
                        }
                        if (dayAllDay.size > maxPerDay) {
                            Text(
                                "+${dayAllDay.size - maxPerDay}",
                                fontSize  = 7.sp,
                                color     = MaterialTheme.colorScheme.primary,
                                modifier  = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp)
        }

        // Scrollable hour grid
        val scrollState = rememberScrollState()
        Row(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            // Hour labels + orange time dot
            Box(modifier = Modifier.width(48.dp).height(hourCount.dp * 60)) {
                Column {
                    for (hour in hourRange) {
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
                // Orange dot at current time
                if (now.hour in hourRange) {
                    OrangeTimeDot(now = now)
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
                        .height(hourCount.dp * 60)
                ) {
                    // Hour lines
                    Column {
                        for (hour in hourRange) {
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
                        val timeStr = Instant.ofEpochMilli(event.startMs)
                            .atZone(ZoneId.systemDefault()).toLocalTime()
                            .format(DateTimeFormatter.ofPattern("h:mm a"))
                        Box(
                            modifier = Modifier
                                .offset(y = topOffset)
                                .height(heightDp)
                                .fillMaxWidth()
                                .padding(1.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color)
                                .clickable { onEventClick(event) }
                                .semantics {
                                    this[SemanticsProperties.Role] = Role.Button
                                    val loc = if (event.location.isNotBlank()) ", at ${event.location}" else ""
                                    contentDescription = "Event: ${event.title}, ${timeStr}$loc"
                                }
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

                    // Orange time line
                    if (now.hour in hourRange) {
                        OrangeTimeLine(now = now)
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
    wall: WallModeState = WallModeState(),
    modifier: Modifier = Modifier
) {
    val dayEvents = events.filter { event ->
        val d = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        d == date
    }.sortedBy { it.startMs }

    val allDay  = dayEvents.filter { it.isAllDay }
    val timed   = dayEvents.filter { !it.isAllDay }

    // ── Orange time bar: current time, updates every 30s ──────
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }

    // Wall mode: trim hour gutter to 06:00–23:00 to reduce vertical chrome
    val hourRange = if (wall.active) 6..23 else 0..23
    val hourCount = hourRange.count()

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
            // Hour labels + orange dot
            Box(modifier = Modifier.width(48.dp).height(hourCount.dp * 60)) {
                Column {
                    for (hour in hourRange) {
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
                if (now.hour in hourRange) {
                    OrangeTimeDot(now = now)
                }
            }

            Box(modifier = Modifier.weight(1f).height(hourCount.dp * 60)) {
                Column {
                    for (hour in hourRange) {
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
                    val timeStr = startZdt.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))
                    Box(
                        modifier = Modifier
                            .offset(y = topOffset)
                            .height(duration)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                            .clickable { onEventClick(event) }
                            .semantics {
                                this[SemanticsProperties.Role] = Role.Button
                                val loc = if (event.location.isNotBlank()) ", at ${event.location}" else ""
                                contentDescription = "Event: ${event.title}, ${timeStr}$loc"
                            }
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
                // Orange time line
                if (now.hour in hourRange) {
                    OrangeTimeLine(now = now)
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
// Orange Time Bar — Skylight's current-time indicator
// ─────────────────────────────────────────────────────────────
@Composable
private fun OrangeTimeDot(now: LocalTime) {
    val offset = (now.hour * 60 + now.minute).dp
    Box(
        modifier = Modifier
            .offset(y = offset)
            .offset(x = (-4).dp)
            .size(10.dp)
            .clip(CircleShape)
            .background(Color(0xFFE07B39))
            .zIndex(10f)
    )
}

@Composable
private fun OrangeTimeLine(now: LocalTime) {
    val offset = (now.hour * 60 + now.minute).dp
    Box(
        modifier = Modifier
            .offset(y = offset)
            .fillMaxWidth()
            .height(2.dp)
            .background(Color(0xFFE07B39))
            .zIndex(10f)
    )
}

// ─────────────────────────────────────────────────────────────
// Multi-day event span — used by MonthView for §5.2.3 bars
// ─────────────────────────────────────────────────────────────
private data class MultiDaySpan(
    val event: CalendarEvent,
    val color: Color,
    /** True if this cell is the start of the span */
    val isStart: Boolean,
    /** True if this cell is the end of the span */
    val isEnd: Boolean
)

/**
 * Build a map of day offset → list of MultiDaySpan for every multi-day
 * event visible in the month grid. Events that span 0 days (same-day)
 * are excluded — they render as normal chips.
 *
 * @param gridStart Monday of the week containing the 1st of the month
 * @param events    All events visible in the month
 * @param people    People list for color resolution
 * @param gridDays  Total grid cells (42 for 6-week grid)
 */
private fun buildMultiDaySpans(
    gridStart: LocalDate,
    events: List<CalendarEvent>,
    people: List<Person>,
    gridDays: Int = 42
): Map<Int, List<MultiDaySpan>> {
    val zone = ZoneId.systemDefault()
    val spans = mutableMapOf<Int, MutableList<MultiDaySpan>>()

    events.forEach { event ->
        val startDate = Instant.ofEpochMilli(event.startMs).atZone(zone).toLocalDate()
        val endDate   = Instant.ofEpochMilli(event.endMs).atZone(zone).toLocalDate()
        val dayCount  = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate)

        // Only multi-day events
        if (dayCount < 1) return@forEach

        val gridStartOffset = java.time.temporal.ChronoUnit.DAYS.between(gridStart, startDate).toInt()
        val gridEndOffset   = java.time.temporal.ChronoUnit.DAYS.between(gridStart, endDate).toInt()

        // Clamp to visible grid
        val firstCell = gridStartOffset.coerceIn(0, gridDays - 1)
        val lastCell  = gridEndOffset.coerceIn(0, gridDays - 1)

        val color = eventColor(event, people)

        for (cell in firstCell..lastCell) {
            val isStart = cell == firstCell
            val isEnd   = cell == lastCell
            spans.getOrPut(cell) { mutableListOf() }
                .add(MultiDaySpan(event = event, color = color, isStart = isStart, isEnd = isEnd))
        }
    }

    return spans
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
