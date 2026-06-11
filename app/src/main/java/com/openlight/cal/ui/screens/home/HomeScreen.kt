package com.openlight.cal.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.model.*
import com.openlight.cal.ui.components.PersonFilterRow
import com.openlight.cal.ui.theme.LocalWallMode
import com.openlight.cal.ui.viewmodel.CalendarViewModel
import com.openlight.cal.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ═══════════════════════════════════════════════════════════════
// HomeScreen — Skylight Calendar dashboard
// Calendar preview + person row + today's events + tasks + lists
// ═══════════════════════════════════════════════════════════════
@Composable
fun HomeScreen(
    app: HearthboardApp,
    calVm: CalendarViewModel,
    taskVm: TaskViewModel,
    people: List<Person>,
    onDayClick: (LocalDate) -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onAddEvent: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today        = LocalDate.now()
    val weekStart    = remember { today.with(java.time.DayOfWeek.MONDAY) }
    val events       by calVm.filteredEvents.collectAsState()
    val tasks        by taskVm.filteredTasks.collectAsState()
    val activeTasks  = remember(tasks) { tasks.filter { !it.isCompleted } }

    // Lists from database
    val dao       = remember { app.database.checkListDao() }
    val allLists  by dao.getAllFlow().collectAsState(initial = emptyList())

    val wall = LocalWallMode.current

    // Group this week's events
    val weekEvents = remember(events, weekStart) {
        val zone = ZoneId.systemDefault()
        events.filter { ev ->
            val d = Instant.ofEpochMilli(ev.startMs).atZone(zone).toLocalDate()
            val days = ChronoUnit.DAYS.between(weekStart, d)
            days in 0..6
        }.groupBy { ev ->
            Instant.ofEpochMilli(ev.startMs).atZone(zone).toLocalDate()
        }
    }

    // Today's events
    val todayEvents = remember(events, today) {
        val zone = ZoneId.systemDefault()
        events.filter { ev ->
            val d = Instant.ofEpochMilli(ev.startMs).atZone(zone).toLocalDate()
            d == today
        }.sortedBy { it.startMs }
    }

    val personFilterId by calVm.personFilter.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = if (wall.active) 16.dp else 12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(if (wall.active) 20.dp else 16.dp)
    ) {
        // ── Profile strip — large, prominent avatars (Skylight style) ──────
        if (people.isNotEmpty()) {
            item {
                PersonFilterRow(
                    people     = people,
                    selectedId = personFilterId,
                    onSelect   = calVm::setPersonFilter,
                    modifier   = Modifier.padding(vertical = 4.dp),
                    largeAvatars = true
                )
            }
        }

        // ── Calendar preview — mini month grid (3 weeks visible) ──────────
        item {
            HomeCalendarPreview(
                weekStart    = weekStart,
                today        = today,
                weekEvents   = weekEvents,
                people       = people,
                onDayClick   = onDayClick,
                onAddEvent   = onAddEvent,
                wall         = wall.active
            )
        }

        // ── Today's events ───────────────────────────────────────────────
        item {
            SectionHeader(
                text = if (todayEvents.isEmpty()) "Today — No events"
                       else "Today — ${todayEvents.size} event${if (todayEvents.size != 1) "s" else ""}",
                action = if (todayEvents.isNotEmpty()) {
                    { onAddEvent() }
                } else null,
                actionLabel = "Add"
            )
        }
        if (todayEvents.isEmpty()) {
            item {
                EmptyHint(
                    text = "Tap + to add an event",
                    onClick = onAddEvent
                )
            }
        } else {
            items(todayEvents.take(if (wall.active) 6 else 5), key = { it.id }) { event ->
                EventRow(
                    event      = event,
                    people     = people,
                    onClick    = { onEventClick(event) },
                    wall       = wall.active
                )
            }
            if (todayEvents.size > (if (wall.active) 6 else 5)) {
                item {
                    Text(
                        "+${todayEvents.size - (if (wall.active) 6 else 5)} more events",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }

        // ── Active tasks ──────────────────────────────────────────────────
        item {
            SectionHeader(
                text = if (activeTasks.isEmpty()) "Tasks — All done!"
                       else "Tasks — ${activeTasks.size} active",
                action = { /* navigate to Tasks tab */ },
                actionLabel = "View All"
            )
        }
        if (activeTasks.isEmpty()) {
            item {
                EmptyHint(
                    text = "No active tasks",
                    onClick = { /* navigate to Tasks tab */ }
                )
            }
        } else {
            items(activeTasks.take(if (wall.active) 10 else 8), key = { it.id }) { task ->
                TaskRow(
                    task     = task,
                    people   = people,
                    onToggle = { taskVm.toggleComplete(task) },
                    wall     = wall.active
                )
            }
            if (activeTasks.size > (if (wall.active) 10 else 8)) {
                item {
                    Text(
                        "+${activeTasks.size - (if (wall.active) 10 else 8)} more tasks",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        }

        // ── Lists ─────────────────────────────────────────────────────────
        item {
            SectionHeader(
                text = if (allLists.isEmpty()) "Lists" else "Lists — ${allLists.size}",
                action = if (allLists.isNotEmpty()) ({ Unit }) else null,
                actionLabel = "View All"
            )
        }
        if (allLists.isEmpty()) {
            item {
                EmptyHint(
                    text = "No lists yet",
                    onClick = { /* navigate to Lists */ }
                )
            }
        } else {
            items(allLists.take(if (wall.active) 4 else 3), key = { it.id }) { list ->
                ListRow(list = list, wall = wall.active)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Calendar preview — mini month grid (3 weeks)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun HomeCalendarPreview(
    weekStart: LocalDate,
    today: LocalDate,
    weekEvents: Map<LocalDate, List<CalendarEvent>>,
    people: List<Person>,
    onDayClick: (LocalDate) -> Unit,
    onAddEvent: () -> Unit,
    wall: Boolean
) {
    val gridStart = remember(weekStart) {
        weekStart.with(java.time.DayOfWeek.MONDAY)
            .let { if (it.isAfter(weekStart)) it.minusWeeks(1) else it }
    }

    val zone = ZoneId.systemDefault()
    val eventsByDay = remember(weekEvents) {
        weekEvents.mapValues { (_, evs) -> evs }
    }

    val cellSize = if (wall) 48.dp else 36.dp
    val dateFontSize = if (wall) 14.sp else 11.sp
    val dotSize = if (wall) 6.dp else 4.dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row with month + add button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = weekStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                FilledTonalIconButton(
                    onClick = onAddEvent,
                    modifier = Modifier.size(if (wall) 40.dp else 32.dp)
                ) {
                    Icon(Icons.Default.Add, "Add event", modifier = Modifier.size(if (wall) 20.dp else 16.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // DOW headers
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { idx, dow ->
                    Text(
                        text      = dow,
                        modifier  = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style     = MaterialTheme.typography.labelSmall.copy(fontSize = if (wall) 11.sp else 9.sp),
                        color     = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 3 weeks
            for (week in 0..2) {
                Row(modifier = Modifier.fillMaxWidth().height(cellSize)) {
                    for (dow in 0..6) {
                        val day = gridStart.plusDays((week * 7 + dow).toLong())
                        val isCurrentMonth = day.month == weekStart.month
                        val isToday = day == today
                        val dayEvents = eventsByDay[day].orEmpty()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onDayClick(day) }
                                .padding(2.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            // Date bubble
                            Box(
                                modifier = Modifier
                                    .size(if (wall) 24.dp else 18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isToday -> Color(0xFFE07B39)
                                            else -> Color.Transparent
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text       = day.dayOfMonth.toString(),
                                    fontSize   = dateFontSize,
                                    color = when {
                                        !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        isToday       -> Color.White
                                        else          -> MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }

                            // Event dots
                            if (dayEvents.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    val n = minOf(dayEvents.size, 3)
                                    repeat(n) { i ->
                                        val evt = dayEvents[i]
                                        val color = eventColor(evt, people)
                                        Box(
                                            modifier = Modifier
                                                .size(dotSize)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                    }
                                    if (dayEvents.size > 3) {
                                        Box(
                                            modifier = Modifier
                                                .size(dotSize)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Event row — single event in the today list (larger, with person initial)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun EventRow(
    event: CalendarEvent,
    people: List<Person>,
    onClick: () -> Unit,
    wall: Boolean
) {
    val color = eventColor(event, people)
    val timeStr = if (!event.isAllDay) {
        Instant.ofEpochMilli(event.startMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    } else null

    val luma = 0.299 * color.red + 0.587 * color.green + 0.114 * color.blue
    val textColor = if (luma > 0.5) Color(0xFF1C2228) else Color.White

    // First assigned person initial
    val personInitial = event.personIds.split(",").firstOrNull()?.toLongOrNull()
        ?.let { id -> people.find { it.id == id }?.initial }

    val pad = if (wall) 16.dp else 12.dp
    val radius = if (wall) 12.dp else 8.dp
    val fontSize = if (wall) 16.sp else 14.sp
    val timeFontSize = if (wall) 13.sp else 11.sp
    val avatarSize = if (wall) 24.dp else 20.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = if (wall) 8.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color bar on the left
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(if (wall) 44.dp else 36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(12.dp))

        // Person initial avatar
        if (personInitial != null && personInitial.isNotBlank()) {
            Box(
                modifier = Modifier.size(avatarSize),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = personInitial,
                    fontSize   = if (wall) 12.sp else 10.sp,
                    color      = textColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = event.title,
                style      = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize),
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onSurface
            )
            if (timeStr != null) {
                Text(
                    text     = timeStr,
                    style    = MaterialTheme.typography.labelSmall.copy(fontSize = timeFontSize),
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (event.location.isNotBlank()) {
            Text(
                text     = event.location,
                style    = MaterialTheme.typography.labelSmall.copy(fontSize = timeFontSize),
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Task row — single task in the active tasks list
// ═══════════════════════════════════════════════════════════════
@Composable
private fun TaskRow(
    task: Task,
    people: List<Person>,
    onToggle: () -> Unit,
    wall: Boolean
) {
    val person = people.find { it.id == task.assignedPersonId }
    val personColor = person?.let {
        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull()
    }

    val checkSize = if (wall) 28.dp else 24.dp
    val fontSize = if (wall) 16.sp else 14.sp
    val chipSize = if (wall) 24.dp else 20.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = if (wall) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Completion circle
        Checkbox(
            checked  = task.isCompleted,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(checkSize),
            colors   = CheckboxDefaults.colors(
                checkedColor = personColor ?: MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text              = task.title,
                style             = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSize),
                maxLines          = 1,
                textDecoration    = if (task.isCompleted) TextDecoration.LineThrough else null,
                color             = if (task.isCompleted)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface
            )
            if (person != null && !person.isDefault) {
                PersonChip(person = person, selected = false, wall = wall)
            }
        }
        if (task.starsEarned > 0) {
            Spacer(Modifier.width(8.dp))
            Text("⭐${task.starsEarned}", fontSize = if (wall) 14.sp else 12.sp)
        }
        if (task.priority == TaskPriority.HIGH) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.PriorityHigh, "High priority",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(if (wall) 20.dp else 16.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// List row — single checklist card (larger, more prominent)
// ═══════════════════════════════════════════════════════════════
@Composable
private fun ListRow(list: CheckList, wall: Boolean) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(list.colorHex))
    }.getOrElse { Color(0xFFFF9800) }

    val avatarSize = if (wall) 48.dp else 40.dp
    val iconSize = if (wall) 24.dp else 20.dp
    val fontSize = if (wall) 18.sp else 16.sp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(avatarSize)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List, null,
                    tint = Color.White, modifier = Modifier.size(iconSize)
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text     = list.name,
                style    = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Empty hint — shown when a section has no data
// ═══════════════════════════════════════════════════════════════
@Composable
private fun EmptyHint(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text  = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ═══════════════════════════════════════════════════════════════
// Person chip — colored dot + initial
// ═══════════════════════════════════════════════════════════════
@Composable
private fun PersonChip(
    person: Person,
    selected: Boolean,
    wall: Boolean
) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(person.colorHex))
    }.getOrNull() ?: MaterialTheme.colorScheme.primary

    val size = if (wall) 24.dp else 20.dp
    val fontSize = if (wall) 11.sp else 9.sp

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (selected) color else color.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text     = person.initial,
            fontSize = fontSize,
            color    = if (selected) Color.White else color,
            fontWeight = FontWeight.Bold
        )
    }
}

// ════════════════════════════════════════════════════════════════
// Utility
// ═══════════════════════════════════════════════════════════════
private fun eventColor(event: CalendarEvent, people: List<Person>): Color {
    val fallback = Color(0xFF4A6178)
    if (event.colorHex.isNotBlank()) {
        return runCatching { Color(android.graphics.Color.parseColor(event.colorHex)) }.getOrElse { fallback }
    }
    if (event.personIds.isNotBlank()) {
        val firstId = event.personIds.split(",").firstOrNull()?.trim()?.toLongOrNull()
        val person  = people.find { it.id == firstId }
        if (person != null) {
            return runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }.getOrElse { fallback }
        }
    }
    return fallback
}

// ════════════════════════════════════════════════════════════════
// Section header with optional action
// ═══════════════════════════════════════════════════════════════
@Composable
private fun SectionHeader(
    text: String,
    action: (() -> Unit)? = null,
    actionLabel: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        action?.let {
            TextButton(onClick = it) {
                Text(actionLabel, fontWeight = FontWeight.Medium)
            }
        }
    }
}