package com.openlight.cal.ui.screens.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
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
// FilterItem for Person Filter Row (top-level to avoid inference issues)
// ─────────────────────────────────────────────────────────────
private data class FilterItem(
    val id: Long,
    val label: String,
    val dotColor: Color?,
    val isVacation: Boolean = false
)

// ─────────────────────────────────────────────────────────────
// Skylight Calendar 2 Color Palette (for light mode)
// These are overridden by theme colors in dark mode via colorScheme
// ─────────────────────────────────────────────────────────────
private val SkylightPurple700 = Color(0xFF7B1FA2)
private val SkylightPurple50  = Color(0xFFF3E5F5)
private val SkylightPurple100 = Color(0xFFE1BEE7)

// Event Pastels (by category/person)
private val PastelPink100     = Color(0xFFF8BBD0)
private val PastelPink900     = Color(0xFF880E4F)
private val PastelPink200     = Color(0xFFFFCDD2)

private val PastelTeal100     = Color(0xFFB2DFDB)
private val PastelTeal900     = Color(0xFF00695C)

private val PastelPurple100   = Color(0xFFE1BEE7)
private val PastelPurple900   = Color(0xFF4A148C)

private val PastelOrange100   = Color(0xFFFFE0B2)
private val PastelOrange900   = Color(0xFFE65100)

private val PastelDeepOrange100 = Color(0xFFFFCCBC)
private val PastelDeepOrange900 = Color(0xFFBF360C)

// Time/weather orange
private val SkylightOrange     = Color(0xFFFF5722)  // Today dot

// FAB Blue
private val SkylightBlue500    = Color(0xFF2196F3)
private val SkylightBlue700    = Color(0xFF1976D2)

// ─────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────
// Calendar Screen (top-level)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    people: List<Person>,
    onAddEvent: () -> Unit,
    familyName: String = "Family",
    tempUnit: String = "F",
    modifier: Modifier = Modifier
) {
    val viewMode       by viewModel.viewMode.collectAsState()
    val selectedDate   by viewModel.selectedDate.collectAsState()
    val events         by viewModel.filteredEvents.collectAsState()
    val forecasts      by viewModel.forecasts.collectAsState()
    val personFilterId by viewModel.personFilter.collectAsState()
    val wall           = LocalWallMode.current

    // Today's forecast
    val todayForecast = forecasts[LocalDate.now()]

    // Live clock state
    var currentTime by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val now = LocalDateTime.now()
            currentTime = now.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
            val msToNextMinute = 60_000L - now.second * 1000L - now.nano / 1_000_000
            delay(msToNextMinute.coerceAtLeast(1000))
        }
    }

    // Filter sheet state
    var showFilterSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Skylight Header: 80dp, family name (serif) + time + temp + Schedule + filter ──
            SkylightHeader(
                date            = selectedDate,
                time            = currentTime,
                forecast        = todayForecast,
                tempUnit        = tempUnit,
                familyName      = familyName,
                onScheduleClick = { /* navigate to agenda */ },
                onFilterClick   = { showFilterSheet = true },
                onPrevClick     = viewModel::navigatePrev,
                onNextClick     = viewModel::navigateNext,
                onTodayClick    = viewModel::goToday
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.fillMaxWidth()
            )

            // ── Person filter row: horizontal scrollable chips ──
            if (showFilterSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showFilterSheet = false },
                    sheetState = rememberModalBottomSheetState(false) // false = hidden
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                            .padding(bottom = 32.dp) // Handle navigation bar
                    ) {
                        Text("View", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        Column {
                            listOf("MONTH" to "Month", "WEEK" to "Week", "DAY" to "Day", "AGENDA" to "Agenda").forEach { (mode, label) ->
                                val selected = viewMode == mode
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, RoundedCornerShape(12.dp))
                                        .clickable {
                                            viewModel.setViewMode(mode)
                                            showFilterSheet = false
                                        }
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                    if (selected) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                        Text("Filter by Person", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(16.dp))
                        SkylightPersonFilterRow(
                            people       = people,
                            selectedId   = personFilterId,
                            onSelect     = { id ->
                                viewModel.setPersonFilter(id)
                                showFilterSheet = false
                            },
                            taskChoreCounts = viewModel.taskChoreCounts.value,
                            vacationCountdown = viewModel.nextVacationCountdown.value
                        )
                    }
                }
            }

            // ── Person filter row: horizontal scrollable chips ──
            if (people.size > 1) {
                SkylightPersonFilterRow(
                    people       = people,
                    selectedId   = personFilterId,
                    onSelect     = viewModel::setPersonFilter,
                    taskChoreCounts = viewModel.taskChoreCounts.value,
                    vacationCountdown = viewModel.nextVacationCountdown.value
                )
            }

            // ── Calendar body ────────────────────────────────────
            Box(modifier = Modifier.weight(1f)) {
                when (viewMode) {
                    "MONTH"  -> SkylightMonthView(
                        selectedDate = selectedDate,
                        events       = events,
                        forecasts    = forecasts,
                        people       = people,
                        onDayClick   = viewModel::setSelectedDate,
                        onEventClick = viewModel::editEvent,
                        wall         = wall
                    )
                    "WEEK"   -> SkylightWeekView(
                        selectedDate = selectedDate,
                        events       = events,
                        forecasts    = forecasts,
                        people       = people,
                        onDayClick   = viewModel::setSelectedDate,
                        onEventClick = viewModel::editEvent,
                        wall         = wall
                    )
                    "DAY"    -> SkylightDayView(
                        date         = selectedDate,
                        events       = events,
                        people       = people,
                        onEventClick = viewModel::editEvent,
                        wall         = wall
                    )
                    "AGENDA" -> AgendaView(events, people, viewModel::editEvent)
                    else     -> SkylightMonthView(
                        selectedDate = selectedDate,
                        events       = events,
                        forecasts    = forecasts,
                        people       = people,
                        onDayClick   = viewModel::setSelectedDate,
                        onEventClick = viewModel::editEvent,
                        wall         = wall
                    )
                }
            }
        }

        // ── Skylight FAB: Blue #2196F3, 56dp, bottom-end 16dp ──
        FloatingActionButton(
            onClick      = onAddEvent,
            containerColor = MaterialTheme.colorScheme.primary, // Uses theme primary (blue in light, adjusted in dark)
            contentColor   = MaterialTheme.colorScheme.onPrimary,
            shape        = CircleShape,
            elevation    = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            modifier     = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add event", modifier = Modifier.size(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Skylight Header — 80dp, family name (serif) + time + temp + Schedule + Filter + nav controls
// ─────────────────────────────────────────────────────────────
@Composable
private fun SkylightHeader(
    date: LocalDate,
    time: String,
    forecast: DailyForecast?,
    tempUnit: String,
    familyName: String,
    onScheduleClick: () -> Unit,
    onFilterClick: () -> Unit,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onTodayClick: () -> Unit
) {
    // Convert temperature based on unit
    val (tempStr, weatherIcon) = remember(forecast, tempUnit) {
        if (forecast != null) {
            val tempHigh = if (tempUnit == "C") {
                // Convert F to C: (F - 32) * 5/9
                ((forecast.tempHigh - 32) * 5 / 9).toInt()
            } else {
                forecast.tempHigh.toInt()
            }
            val icon = when (forecast.conditionLabel.lowercase()) {
                "clear" -> Icons.Filled.WbSunny
                "cloudy" -> Icons.Filled.Cloud
                "fog" -> Icons.Filled.CloudQueue
                "drizzle" -> Icons.Filled.Grain
                "freezing drizzle" -> Icons.Filled.AcUnit
                "rain" -> Icons.Filled.CloudQueue
                "freezing rain" -> Icons.Filled.CloudQueue
                "snow" -> Icons.Filled.AcUnit
                "rain showers" -> Icons.Filled.CloudQueue
                else -> Icons.Filled.WbSunny
            }
            "${tempHigh}°" to icon
        } else {
            "" to Icons.Filled.WbSunny
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Family name (serif) + Time + Weather inline - 3 font sizes larger
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Family name serif
            Text(
                text       = familyName,
                fontFamily = FontFamily.Serif,
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onSurface
            )
            // Time + Weather on same line - BOLD BLACK, LARGER
            if (forecast != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text     = time,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color    = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = weatherIcon,
                        contentDescription = forecast.conditionLabel,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text     = tempStr,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color    = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Text(
                    text     = time,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color    = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Right: Schedule + Filter + nav controls - ALL IN ROUNDED RECTANGULAR BOXES
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Schedule button in rounded box
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .clickable { onScheduleClick() }
            ) {
                Text(
                    text     = "Schedule",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color    = MaterialTheme.colorScheme.onPrimary
                )
            }
            // Filter button in rounded box
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                    .clickable { onFilterClick() }
            ) {
                Text(
                    text     = "Filter",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color    = MaterialTheme.colorScheme.onPrimary
                )
            }
            // Nav controls: Prev / Today / Next - each in rounded boxes
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(20.dp))
                        .clickable { onPrevClick() }
                ) {
                    Icon(Icons.Default.ChevronLeft, "Previous", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
                        .clickable { onTodayClick() }
                ) {
                    Text("Today", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(20.dp))
                        .clickable { onNextClick() }
                ) {
                    Icon(Icons.Default.ChevronRight, "Next", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Calendar Controls — nav, today, schedule, filter (no view mode tabs, no divider)
// ─────────────────────────────────────────────────────────────
@Composable
private fun CalendarControls(
    viewMode: String,
    onViewMode: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onAdd: () -> Unit,
    onScheduleClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Today + Prev/Next
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onToday) {
                Text("Today", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onPrev) { Icon(Icons.Default.ChevronLeft, "Previous", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
            IconButton(onClick = onNext) { Icon(Icons.Default.ChevronRight, "Next", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface) }
        }

        // Right: Schedule button + Filter text button (view mode in filter sheet)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onScheduleClick) {
                Text("Schedule", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onFilterClick) {
                Text("Filter", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Skylight Person Filter Row — horizontal scrollable chips
// Chip: 32dp tall, 12dp horizontal padding, colored dot + name + count (only if > 0)
// ─────────────────────────────────────────────────────────────
@Composable
private fun SkylightPersonFilterRow(
    people: List<Person>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    taskChoreCounts: Map<Long, Int> = emptyMap(), // personId -> active count
    vacationCountdown: String? = null,
) {
    val filterItems = remember(people, taskChoreCounts, vacationCountdown) {
        buildList<FilterItem> {
            if (vacationCountdown != null) {
                add(FilterItem(id = -1L, label = vacationCountdown, dotColor = null, isVacation = true))
            }
            add(FilterItem(id = 0L, label = "All", dotColor = null, isVacation = true))
            people.filter { !it.isDefault }.forEach { person ->
                val total = taskChoreCounts[person.id] ?: 0
                // Only show count if > 0, otherwise just show name
                val label = if (total > 0) "${person.name} $total" else person.name
                val color = runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
                    .getOrElse { Color.Gray }
                add(FilterItem(id = person.id, label = label, dotColor = color))
            }
        }
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(filterItems) { item: FilterItem ->
            val selected = selectedId == item.id
            val bgColor  = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
            val textColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

            Row(
                modifier = Modifier
                    .height(32.dp)
                    .padding(horizontal = 12.dp)
                    .background(bgColor, RoundedCornerShape(16.dp))
                    .clickable { onSelect(item.id) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Colored dot (8dp) for non-vacation items
                if (item.dotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(item.dotColor!!)
                    )
                }
                Text(
                    text       = item.label,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color      = textColor
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Skylight Month View — 6-week grid, Mon-Sun 3-letter headers, orange today dot
// ─────────────────────────────────────────────────────────────
@Composable
private fun SkylightMonthView(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    forecasts: Map<LocalDate, DailyForecast>,
    people: List<Person>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    wall: WallModeState
) {
    val today = LocalDate.now()
    val gridStart = remember(selectedDate) {
        val firstDay = selectedDate.withDayOfMonth(1)
        firstDay.with(java.time.DayOfWeek.MONDAY).let {
            if (it.isAfter(firstDay)) it.minusWeeks(1) else it
        }
    }

    val zone = ZoneId.systemDefault()
    val eventsByDay = remember(events) {
        events.groupBy { ev ->
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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        // Day headers: Mon, Tue, Wed, Thu, Fri, Sat, Sun (3-letter abbreviations) - BOLD BLACK
        Row(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { idx, dow ->
                Text(
                    text       = dow,
                    modifier   = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    textAlign  = TextAlign.Center,
                    style      = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface  // BLACK/BOLD for all
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 4.dp))

        // 6-week grid - bigger cells with more spacing
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(42) { index: Int ->
                val day = gridStart.plusDays(index.toLong())
                val isCurrentMonth = day.month == selectedDate.month
                val isToday = day == today
                val isSelected = day == selectedDate
                val isTodayCol = (index % 7) == todayCol
                val dayEvents = eventsByDay[day].orEmpty()
                val cellMultiDay = multiDaySpans[index].orEmpty()

                SkylightDayCell(
                    day            = day,
                    isCurrentMonth = isCurrentMonth,
                    isToday        = isToday,
                    isSelected     = isSelected,
                    isTodayColumn  = isTodayCol,
                    events         = dayEvents,
                    multiDaySpans  = cellMultiDay,
                    forecasts      = forecasts,
                    people         = people,
                    wall           = wall,
                    onClick        = { onDayClick(day) },
                    onEventClick   = onEventClick
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Day Cell — date number, orange today dot, event blocks, multi-day bars
// ─────────────────────────────────────────────────────────────
@Composable
private fun SkylightDayCell(
    day: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    isTodayColumn: Boolean,
    events: List<CalendarEvent>,
    multiDaySpans: List<MultiDaySpan>,
    forecasts: Map<LocalDate, DailyForecast>,
    people: List<Person>,
    wall: WallModeState,
    onClick: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()
    val wf = forecasts[day]
    val cellHeight = if (wall.active) 120.dp else 100.dp
    val dateFontSize = if (wall.active) 16.sp else 14.sp
    val maxVisible = if (wall.active) 5 else 4

    val cellBg = when {
        isTodayColumn && isCurrentMonth -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)
        isSelected && !isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Column(
        modifier = modifier
            .height(cellHeight)
            .background(cellBg, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .border(
                if (isToday) BorderStroke(2.dp, SkylightOrange)
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(top = 4.dp, start = 6.dp, end = 6.dp, bottom = 4.dp)
    ) {
        // Date number + today dot
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.End
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (wall.active) 28.dp else 24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday    -> SkylightOrange
                            else       -> Color.Transparent
                        }
                    )
            ) {
                Text(
                    text       = day.dayOfMonth.toString(),
                    fontSize   = dateFontSize,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = when {
                        isSelected      -> MaterialTheme.colorScheme.onPrimary
                        !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        isToday         -> MaterialTheme.colorScheme.onPrimary
                        else            -> MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Weather for this day (month view)
        if (wf != null && isCurrentMonth) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(wf.iconChar, fontSize = 10.sp)
                Text("${wf.tempHigh.toInt()}°",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
        }

        // Multi-day event bars
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
                    .height(24.dp)
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(span.color)
                    .clickable { onEventClick(span.event) }
                    .semantics {
                        this[SemanticsProperties.Role] = Role.Button
                        contentDescription = "Multi-day: ${span.event.title}"
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                if (barLabel.isNotBlank()) {
                    Text(
                        text       = barLabel,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color      = if (span.color == PastelDeepOrange100) PastelDeepOrange900 else Color.White,
                        maxLines   = 2,  // Allow more lines
                        overflow   = TextOverflow.Visible,
                        modifier   = Modifier.padding(start = 8.dp, end = 8.dp)
                    )
                }
            }
        }

        // Single-day event blocks
        events.take(maxVisible).forEach { event ->
            val (bgColor, textColor) = eventPastelColors(event, people)
            val timeStr = if (!event.isAllDay) {
                val start = Instant.ofEpochMilli(event.startMs).atZone(zone).toLocalTime()
                val end   = Instant.ofEpochMilli(event.endMs).atZone(zone).toLocalTime()
                val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
                "${start.format(fmt)} - ${end.format(fmt)}"
            } else "All day"

            // Person badge
            val personInitial = event.personIds.split(",").firstOrNull()?.toLongOrNull()
                ?.let { id -> people.find { it.id == id }?.initial ?: event.title.first().toString() }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(bgColor)
                    .clickable { onEventClick(event) }
                    .semantics {
                        this[SemanticsProperties.Role] = Role.Button
                        contentDescription = "Event: ${event.title}, $timeStr"
                    }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = timeStr,
                        fontSize   = 10.sp,
                        color      = textColor.copy(alpha = 0.8f),
                        maxLines   = 1
                    )
                    Text(
                        text       = event.title,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color      = textColor,
                        maxLines   = 3,  // Allow more lines so name never cut off
                        overflow   = TextOverflow.Visible  // No ellipsis, show full text
                    )
                }
                // Person badge (16dp circle)
                if (personInitial != null) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(textColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text       = personInitial.uppercase(),
                            fontSize   = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color      = textColor
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }

        if (events.size > maxVisible) {
            Text(
                text     = "+${events.size - maxVisible} more",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Skylight Week View — 5 days visible, current day first, swipe to navigate, black time line
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SkylightWeekView(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    forecasts: Map<LocalDate, DailyForecast>,
    people: List<Person>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    wall: WallModeState
) {
    val today = LocalDate.now()
    // Generate 14 days: 7 before + today + 6 after (allows swipe both directions)
    val allDays = remember { (-7..6).map { today.plusDays(it.toLong()) } }
    val startIndex = allDays.indexOfFirst { it == selectedDate }.coerceAtLeast(0)

    val hourRange = if (wall.active) 6..23 else 0..23
    val hourCount = hourRange.count()

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now()
        }
    }

    // Auto-scroll to current day
    val scrollState = rememberLazyListState()
    LaunchedEffect(key1 = Unit) {
        scrollState.scrollToItem(startIndex)
    }

    // Horizontal list of days with swipe navigation
    LazyRow(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        items(allDays.size) { pageIndex ->
            val currentDay = allDays[pageIndex]
            val isToday = currentDay == today
            val wf = forecasts[currentDay]

            Column(modifier = Modifier
                .fillMaxWidth()
                .width(400.dp) // Fixed day width
                .height(hourCount.dp * 60 + 120.dp)
            ) {
                // Day header - day name + date + weather
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        currentDay.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()).uppercase(),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 20.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onDayClick(currentDay) }
                    ) {
                        Text(
                            currentDay.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (wf != null) {
                        Spacer(Modifier.width(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(wf.iconChar, fontSize = 20.sp)
                            Text("${wf.tempHigh.toInt()}°",
                                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                HorizontalDivider()

                // All-day events
                val zone = ZoneId.systemDefault()
                val allDayEvents = events.filter { it.isAllDay }.filter { event ->
                    val startDate = Instant.ofEpochMilli(event.startMs).atZone(zone).toLocalDate()
                    val endDate = Instant.ofEpochMilli(event.endMs).atZone(zone).toLocalDate()
                    !currentDay.isBefore(startDate) && currentDay.isBefore(endDate)
                }
                if (allDayEvents.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        allDayEvents.take(2).forEach { event ->
                            val (bgColor, textColor) = eventPastelColors(event, people)
                            Text(event.title,
                                fontSize = 11.sp, color = textColor, maxLines = 1, overflow = TextOverflow.Visible,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(bgColor)
                                    .clickable { onEventClick(event) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Hour grid with 12hr labels and half-hour lines
                val hourScrollState = rememberScrollState()
                Row(modifier = Modifier
                    .weight(1f)
                    .verticalScroll(hourScrollState)
                    .padding(bottom = 80.dp)
                ) {
                    // Hour labels column - moved away from left edge
                    Box(modifier = Modifier.width(64.dp).height(hourCount.dp * 60)) {
                        Column {
                            for (hour in hourRange) {
                                // Hour line
                                Box(modifier = Modifier.height(60.dp)) {
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    )
                                }
                                // 12hr time label
                                if (hour != 0) {
                                    val displayHour = if (hour > 12) hour - 12 else hour
                                    val amPm = if (hour >= 12) "PM" else "AM"
                                    Text("$displayHour $amPm",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(end = 12.dp, top = 2.dp)
                                            .wrapContentSize(Alignment.CenterEnd))
                                }
                            }
                        }
                        // Black current time line
                        if (now.hour in hourRange) {
                            BlackTimeLine(now = now, fullWidth = false)
                        }
                    }

                    // Single day column (each item = 1 day)
                    val dayEvents = events.filter { event ->
                        val d = Instant.ofEpochMilli(event.startMs).atZone(zone).toLocalDate()
                        d == currentDay && !event.isAllDay
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(hourCount.dp * 60)) {
                        // Hour grid lines
                        Column {
                            for (hour in hourRange) {
                                Box(modifier = Modifier.height(60.dp)) {
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                                        thickness = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                                    )
                                    // Half-hour line
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(),
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                                    )
                                }
                            }
                        }
                        // Events
                        dayEvents.forEach { event ->
                            val startZdt = Instant.ofEpochMilli(event.startMs).atZone(zone)
                            val startHour = startZdt.hour
                            val startMin = startZdt.minute
                            val durationMin = ((event.endMs - event.startMs) / 60_000L).coerceAtLeast(30L)
                            val topOffset = (startHour * 60 + startMin).dp
                            val heightDp = (durationMin.toInt()).coerceAtMost(120).dp.coerceAtLeast(28.dp)

                            val (bgColor, textColor) = eventPastelColors(event, people)
                            val badgeColor = textColor

                            val timeStr = startZdt.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
                            val endTime = Instant.ofEpochMilli(event.endMs).atZone(zone).toLocalTime()
                                .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
                            val timeRange = "$timeStr - $endTime"

                            val personInitial = event.personIds.split(",").firstOrNull()?.toLongOrNull()
                                ?.let { id -> people.find { it.id == id }?.initial ?: event.title.first().toString() }

                            Box(
                                modifier = Modifier
                                    .offset(y = topOffset)
                                    .height(heightDp)
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp, vertical = 1.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor)
                                    .clickable { onEventClick(event) }
                                    .semantics {
                                        this[SemanticsProperties.Role] = Role.Button
                                        contentDescription = "Event: ${event.title}, $timeRange"
                                    }
                                    .padding(8.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (personInitial != null) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            Box(
                                                modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(badgeColor),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(personInitial.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(timeRange.replace(" ", ""), fontSize = 11.sp, color = textColor.copy(alpha = 0.7f), maxLines = 1)
                                    Text(event.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 3, overflow = TextOverflow.Visible)
                                }
                            }
                        }
                        // Black current time line
                        if (now.hour in hourRange) {
                            BlackTimeLine(now = now, fullWidth = true)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Skylight Day View — single day, vertical time, pastel event blocks
// ─────────────────────────────────────────────────────────────
@Composable
private fun SkylightDayView(
    date: LocalDate,
    events: List<CalendarEvent>,
    people: List<Person>,
    onEventClick: (CalendarEvent) -> Unit,
    wall: WallModeState
) {
    val dayEvents = events.filter { event ->
        val d = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
        d == date
    }.sortedBy { it.startMs }

    val allDay  = dayEvents.filter { it.isAllDay }
    val timed   = dayEvents.filter { !it.isAllDay }

    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalTime.now() } }

    val hourRange = if (wall.active) 6..23 else 0..23
    val hourCount = hourRange.count()

    Column(modifier = Modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) {
            SectionHeader("All Day")
            allDay.forEach { event ->
                val (bgColor, textColor) = eventPastelColors(event, people)
                EventCard(event = event, people = people, onClick = { onEventClick(event) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        .background(bgColor, RoundedCornerShape(8.dp))
                        .padding(8.dp))
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        }

        val scrollState = rememberScrollState()
        Row(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            // Hour labels - 12hr format with half-hour grid lines
            Box(modifier = Modifier.width(64.dp).height(hourCount.dp * 60)) {
                Column {
                    for (hour in hourRange) {
                        // Hour line
                        Box(modifier = Modifier.height(60.dp)) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        }
                        // 12hr time label
                        if (hour != 0) {
                            val displayHour = if (hour > 12) hour - 12 else hour
                            val amPm = if (hour >= 12) "PM" else "AM"
                            Text("$displayHour $amPm",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 12.dp, top = 2.dp)
                                    .wrapContentSize(Alignment.CenterEnd))
                        }
                    }
                }
                if (now.hour in hourRange) {
                    BlackTimeLine(now = now, fullWidth = false)
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(hourCount.dp * 60)) {
                // Hour grid lines with half-hour markers
                Column {
                    for (hour in hourRange) {
                        Box(modifier = Modifier.height(60.dp)) {
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                            // Half-hour line
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
                timed.forEach { event ->
                    val startZdt = Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault())
                    val topOffset = (startZdt.hour * 60 + startZdt.minute).dp
                    val duration = (((event.endMs - event.startMs) / 60_000).coerceAtLeast(30L).toInt()).dp

                    val (bgColor, textColor) = eventPastelColors(event, people)
                    val badgeColor = textColor

                    val timeStr = startZdt.toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
                    val endTime = Instant.ofEpochMilli(event.endMs).atZone(ZoneId.systemDefault()).toLocalTime()
                        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
                    val timeRange = "$timeStr - $endTime"

                    val personInitial = event.personIds.split(",").firstOrNull()?.toLongOrNull()
                        ?.let { id -> people.find { it.id == id }?.initial ?: event.title.first().toString() }

                    Box(
                        modifier = Modifier
                            .offset(y = topOffset)
                            .height(duration.coerceAtLeast(28.dp))
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgColor)
                            .clickable { onEventClick(event) }
                            .semantics {
                                this[SemanticsProperties.Role] = Role.Button
                                contentDescription = "Event: ${event.title}, $timeRange"
                            }
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (personInitial != null) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Box(
                                        modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(badgeColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(personInitial.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(timeRange.replace(" ", ""), fontSize = 11.sp, color = textColor.copy(alpha = 0.7f), maxLines = 1)
                            Text(event.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textColor, maxLines = 3, overflow = TextOverflow.Visible)
                            if (event.location.isNotBlank()) {
                                Text(event.location, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.8f), maxLines = 1)
                            }
                        }
                    }
                }
                if (now.hour in hourRange) {
                    BlackTimeLine(now = now, fullWidth = true)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Black Time Line (current time indicator) — replaces OrangeTimeLine
// ─────────────────────────────────────────────────────────────
@Composable
private fun BlackTimeLine(now: LocalTime, fullWidth: Boolean) {
    val offset = (now.hour * 60 + now.minute).dp
    Box(
        modifier = Modifier
            .offset(y = offset)
            .width(if (fullWidth) 9999.dp else 64.dp)
            .height(2.dp)
            .background(MaterialTheme.colorScheme.onSurface)  // BLACK
    )
}

// ─────────────────────────────────────────────────────────────
// Multi-day event span for MonthView
// ─────────────────────────────────────────────────────────────
private data class MultiDaySpan(
    val event: CalendarEvent,
    val color: Color,
    val isStart: Boolean,
    val isEnd: Boolean
)

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

        if (dayCount < 1) return@forEach

        val gridStartOffset = java.time.temporal.ChronoUnit.DAYS.between(gridStart, startDate).toInt()
        val gridEndOffset   = java.time.temporal.ChronoUnit.DAYS.between(gridStart, endDate).toInt()

        val firstCell = gridStartOffset.coerceIn(0, gridDays - 1)
        val lastCell  = gridEndOffset.coerceIn(0, gridDays - 1)

        val (bgColor,) = eventPastelColors(event, people)

        for (cell in firstCell..lastCell) {
            val isStart = cell == firstCell
            val isEnd   = cell == lastCell
            spans.getOrPut(cell) { mutableListOf() }
                .add(MultiDaySpan(event = event, color = bgColor, isStart = isStart, isEnd = isEnd))
        }
    }
    return spans
}

// ─────────────────────────────────────────────────────────────
// Event Pastel Color Resolution (by person/category)
// Returns (backgroundColor, textColor) pair
// ─────────────────────────────────────────────────────────────
private fun eventPastelColors(event: CalendarEvent, people: List<Person>): Pair<Color, Color> {
    // Try to get person color first
    if (event.personIds.isNotBlank()) {
        val firstId = event.personIds.split(",").firstOrNull()?.trim()?.toLongOrNull()
        val person = people.find { it.id == firstId }
        if (person != null) {
            val personColor = runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }.getOrNull()
            if (personColor != null) {
                return when (personColor) {
                    Color(0xFFE91E63), Color(0xFFEC407A), Color(0xFFF06292) -> PastelPink100 to PastelPink900
                    Color(0xFF4CAF50), Color(0xFF66BB6A), Color(0xFF81C784) -> PastelTeal100 to PastelTeal900
                    Color(0xFF2196F3), Color(0xFF42A5F5), Color(0xFF64B5F6) -> PastelTeal100 to PastelTeal900
                    Color(0xFF9C27B0), Color(0xFFBA68C8), Color(0xFFCE93D8) -> PastelPurple100 to PastelPurple900
                    Color(0xFFFF9800), Color(0xFFFFA726), Color(0xFFFFB74D) -> PastelOrange100 to PastelOrange900
                    else -> PastelPurple100 to PastelPurple900
                }
            }
        }
    }

    // Fallback: event's own color
    if (event.colorHex.isNotBlank()) {
        val eventColor = runCatching { Color(android.graphics.Color.parseColor(event.colorHex)) }.getOrNull()
        if (eventColor != null) {
            return eventColor to eventColor.copy(alpha = 0.9f)
        }
    }

    // Default fallback
    return PastelPurple100 to PastelPurple900
}

// ─────────────────────────────────────────────────────────────
// Utility: original eventColor for other uses
// ─────────────────────────────────────────────────────────────
private fun eventColor(event: CalendarEvent, people: List<Person>): Color {
    return eventPastelColors(event, people).first
}

// ─────────────────────────────────────────────────────────────
// Agenda View (unchanged)
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
                        color      = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    if (date == today) {
                        Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
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