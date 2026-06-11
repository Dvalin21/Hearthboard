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

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Spec header: family name + time ──────────────────
            CalendarHeader(
                date        = selectedDate,
                temperature = todayForecast?.let { "${it.iconChar}${it.tempHigh.toInt()}°" }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

            // ── Calendar controls: view mode + nav ──────────────
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

        // ── Spec FAB: purple circle, white +, bottom-right ──────
        FloatingActionButton(
            onClick      = onAddEvent,
            containerColor = Color(0xFF7C4DFF),
            contentColor   = Color.White,
            shape        = CircleShape,
            modifier     = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add event")
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
// Month View — 6-week grid (Skylight-style: generous cells, big chips)
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

    // Cache the grid origin per month.
    val gridStart = remember(selectedDate) {
        val firstDay = selectedDate.withDayOfMonth(1)
        firstDay.with(java.time.DayOfWeek.MONDAY).let {
            if (it.isAfter(firstDay)) it.minusWeeks(1) else it
        }
    }

    val zone = ZoneId.systemDefault()
    val singleDayEvents = remember(events) {
        events.filter { ev ->
            val startDate = Instant.ofEpochMilli(ev.startMs).atZone(zone).toLocalDate()
            val endDate   = Instant.ofEpochMilli(ev.endMs).atZone(zone).toLocalDate()
            java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) < 1
        }
    }

    val eventsByDay: Map<LocalDate, List<CalendarEvent>> = remember(singleDayEvents) {
        singleDayEvents.groupBy { ev ->
            Instant.ofEpochMilli(ev.startMs).atZone(zone).toLocalDate()
        }
    }

    val multiDaySpans = remember(events, gridStart) {
        buildMultiDaySpans(gridStart, events, people)
    }

    val todayCol = remember(today, gridStart) {
        val daysFromStart = java.time.temporal.ChronoUnit.DAYS.between(gridStart, today)
        if (daysFromStart in 0..41) (daysFromStart % 7).toInt() else -1
    }

    val dowHeaders = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dowFontSize  = if (wall.active) 16.sp else 13.sp
    val headerPadV   = if (wall.active) 12.dp else 8.dp

    Column(modifier = modifier.fillMaxSize()) {
        // DOW header row — stronger, more readable
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            dowHeaders.forEachIndexed { idx, dow ->
                val isTodayCol = idx == todayCol
                Text(
                    text       = dow,
                    modifier   = Modifier.weight(1f),
                    textAlign  = TextAlign.Center,
                    style      = MaterialTheme.typography.labelMedium.copy(fontSize = dowFontSize),
                    fontWeight = if (isTodayCol) FontWeight.Bold else FontWeight.Medium,
                    color      = if (isTodayCol) MaterialTheme.colorScheme.primary
                                 else            MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = headerPadV),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        // 6-week grid — each row takes equal height
        val weeks = 6
        for (week in 0 until weeks) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
            ) {
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
                        modifier       = Modifier.weight(1f, fill = true)
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
    // Skylight: generous touch targets, readable at 3-6 ft
    val dateBubble   = if (wall.active) 40.dp else 28.dp
    val dateFontSize = if (wall.active) 16.sp else 13.sp
    val chipFontSize = if (wall.active) 14.sp else 11.sp
    val chipPadV     = if (wall.active) 6.dp else 4.dp
    val chipPadH     = if (wall.active) 8.dp else 6.dp
    val chipGap      = if (wall.active) 6.dp else 4.dp
    val tempFontSize = if (wall.active) 12.sp else 10.sp
    val maxVisible   = if (wall.active) 5 else 4

    // Today-column background tint — stronger, paper-like
    val cellBg = when {
        isTodayColumn && isCurrentMonth -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else                            -> Color.Transparent
    }

    // Selected day gets a subtle ring
    val selectedRing = isSelected && !isToday

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(cellBg)
            .clickable(onClick = onClick)
            .padding(if (wall.active) 4.dp else 3.dp)
    ) {
        // Day number + weather
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier          = Modifier.fillMaxWidth().padding(top = 2.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier         = Modifier
                    .size(dateBubble)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday    -> Color(0xFFE07B39) // Skylight orange
                            else       -> Color.Transparent
                        }
                    )
            ) {
                Text(
                    text       = day.dayOfMonth.toString(),
                    fontSize   = dateFontSize,
                    color = when {
                        isSelected      -> MaterialTheme.colorScheme.onPrimary
                        !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        else            -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
            if (selectedRing) {
                // Subtle ring for selected (non-today)
            }
            if (wf != null && isCurrentMonth) {
                Spacer(Modifier.weight(1f))
                Text(
                    text     = "${wf.iconChar}${wf.tempHigh.toInt()}°",
                    fontSize = tempFontSize,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(Modifier.height(if (wall.active) 6.dp else 4.dp))

        // ── Multi-day event bars ──────────────────
        multiDaySpans.forEach { span ->
            val barLabel = when {
                span.isStart && span.isEnd -> span.event.title
                span.isStart               -> span.event.title + " ›"
                span.isEnd                 -> "◂ " + span.event.title
                else                       -> ""
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chipFontSize.value.dp + 8.dp)
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(span.color)
                    .clickable { onEventClick(span.event) }
                    .semantics {
                        this[SemanticsProperties.Role] = Role.Button
                        contentDescription = "Multi-day: ${span.event.title}"
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (barLabel.isNotBlank()) {
                    Text(
                        text      = barLabel,
                        fontSize  = chipFontSize,
                        color     = Color.White,
                        maxLines  = 1,
                        overflow  = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Single-day event chips ────────────────
        events.take(maxVisible).forEach { event ->
            val chipColor = eventColor(event, people)
            val timeStr = if (!event.isAllDay) {
                Instant.ofEpochMilli(event.startMs)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .format(DateTimeFormatter.ofPattern("h:mm a"))
            } else null
            val luma = 0.299 * chipColor.red + 0.587 * chipColor.green + 0.114 * chipColor.blue
            val chipText = if (luma > 0.5) Color(0xFF1C2228) else Color.White

            // Person initial for this event (first assigned person)
            val personInitial = event.personIds.split(",").firstOrNull()?.toLongOrNull()
                ?.let { id -> people.find { it.id == id }?.initial }
            val showInitial = personInitial != null && personInitial.isNotBlank()

            val chipLabel = buildString {
                if (timeStr != null) append("$timeStr ")
                append(event.title)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(chipColor)
                    .clickable { onEventClick(event) }
                    .semantics {
                        this[SemanticsProperties.Role] = Role.Button
                        contentDescription = "Event: ${event.title}, ${timeStr ?: "all day"}"
                    }
                    .padding(horizontal = chipPadH, vertical = chipPadV),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showInitial) {
                    Box(
                        modifier = Modifier.size(if (wall.active) 20.dp else 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = personInitial!!,
                            fontSize   = if (wall.active) 11.sp else 9.sp,
                            color      = chipText,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(if (wall.active) 6.dp else 4.dp))
                }
                Text(
                    text      = chipLabel,
                    fontSize  = chipFontSize,
                    color     = chipText,
                    maxLines  = 1,
                    overflow  = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    modifier  = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(chipGap))
        }
        if (events.size > maxVisible) {
            Text(
                text     = "+${events.size - maxVisible} more",
                fontSize = chipFontSize,
                color    = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
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
                    // Events (spec style: pastel bg, person badge, title+time)
                    dayEvents.forEach { event ->
                        val startHour = Instant.ofEpochMilli(event.startMs)
                            .atZone(ZoneId.systemDefault()).hour
                        val startMin  = Instant.ofEpochMilli(event.startMs)
                            .atZone(ZoneId.systemDefault()).minute
                        val durationMin = ((event.endMs - event.startMs) / 60_000).coerceAtLeast(30L)
                        val topOffset   = (startHour * 60 + startMin).dp
                        val heightDp    = (durationMin.toInt()).coerceAtMost(120).dp

                        val strong = eventColor(event, people)
                        val pastel = strong.copy(alpha = 0.18f)

                        // Find person for badge
                        val pid = event.personIds.split(",").firstOrNull { it.isNotBlank() }?.trim()
                        val evPerson = pid?.let { id -> people.firstOrNull { it.id.toString() == id } }
                        val badgeColor = evPerson?.let {
                            runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }
                                .getOrNull()?.copy(alpha = 0.85f) ?: strong
                        } ?: strong
                        val badgeInitial = evPerson?.initial ?: ""

                        val timeStr = Instant.ofEpochMilli(event.startMs)
                            .atZone(ZoneId.systemDefault()).toLocalTime()
                            .format(DateTimeFormatter.ofPattern("h:mm a"))
                        Box(
                            modifier = Modifier
                                .offset(y = topOffset)
                                .height(heightDp.coerceAtLeast(28.dp))
                                .fillMaxWidth()
                                .padding(horizontal = 1.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(pastel)
                                .clickable { onEventClick(event) }
                                .semantics {
                                    this[SemanticsProperties.Role] = Role.Button
                                    val loc = if (event.location.isNotBlank()) ", at ${event.location}" else ""
                                    contentDescription = "Event: ${event.title}, ${timeStr}$loc"
                                }
                                .padding(4.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Person initial badge (top-right)
                                if (badgeInitial.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .size(14.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            badgeInitial,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                // Time
                                Text(
                                    text     = timeStr.lowercase().replace(" ", ""),
                                    fontSize = 10.sp,
                                    color    = Color(0xFF6B7280),
                                    maxLines = 1
                                )
                                // Title
                                Text(
                                    text     = event.title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color    = Color(0xFF1F2937),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
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

                    val strong = eventColor(event, people)
                    val pastel = strong.copy(alpha = 0.18f)

                    // Find person for badge
                    val pid = event.personIds.split(",").firstOrNull { it.isNotBlank() }?.trim()
                    val evPerson = pid?.let { id -> people.firstOrNull { it.id.toString() == id } }
                    val badgeColor = evPerson?.let {
                        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }
                            .getOrNull()?.copy(alpha = 0.85f) ?: strong
                    } ?: strong
                    val badgeInitial = evPerson?.initial ?: ""

                    val timeStr = startZdt.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a"))
                    Box(
                        modifier = Modifier
                            .offset(y = topOffset)
                            .height(duration.coerceAtLeast(28.dp))
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(pastel)
                            .clickable { onEventClick(event) }
                            .semantics {
                                this[SemanticsProperties.Role] = Role.Button
                                val loc = if (event.location.isNotBlank()) ", at ${event.location}" else ""
                                contentDescription = "Event: ${event.title}, ${timeStr}$loc"
                            }
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Person initial badge (top-right)
                            if (badgeInitial.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .size(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        badgeInitial,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            // Time
                            Text(
                                text     = timeStr.lowercase().replace(" ", ""),
                                fontSize = 10.sp,
                                color    = Color(0xFF6B7280),
                                maxLines = 1
                            )
                            // Title
                            Text(
                                text     = event.title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color    = Color(0xFF1F2937),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
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
// CalendarHeader — spec: "Miller Family" serif + time + temp
// ─────────────────────────────────────────────────────────────
@Composable
private fun CalendarHeader(
    date: LocalDate,
    temperature: String?
) {
    val timeNow = remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            timeNow.value = now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
            val msToNextMinute = 60_000L - now.second * 1000L - now.nano / 1_000_000
            delay(msToNextMinute)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text    = "Miller Family",
            // fontFamily = FontFamily.Serif, — if serif is desired
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color   = Color(0xFF1F2937)
        )
        Spacer(Modifier.weight(1f))
        if (temperature != null) {
            Text(
                text    = temperature,
                fontSize = 14.sp,
                color   = Color(0xFF6B7280)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text    = timeNow.value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color   = Color(0xFF374151)
        )
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
