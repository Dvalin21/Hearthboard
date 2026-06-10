package com.openlight.cal.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.model.*
import com.openlight.cal.ui.theme.LocalWallMode
import com.openlight.cal.ui.viewmodel.CalendarViewModel
import com.openlight.cal.ui.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ─────────────────────────────────────────────────────────────
// HomeScreen — Skylight Calendar dashboard
// Per spec §4: Calendar/Week pane + Tasks pane + Lists pane
// ─────────────────────────────────────────────────────────────
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── Week strip ────────────────────────────────────────
        item {
            WeekStrip(
                weekStart  = weekStart,
                today      = today,
                weekEvents = weekEvents,
                onDayClick = onDayClick,
                wall       = wall.active
            )
        }

        // ── Today's events ────────────────────────────────────
        item {
            SectionHeader(
                text = if (todayEvents.isEmpty()) "Today — No events"
                       else "Today — ${todayEvents.size} event${if (todayEvents.size != 1) "s" else ""}"
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
            items(todayEvents.take(5), key = { it.id }) { event ->
                EventRow(
                    event      = event,
                    people     = people,
                    onClick    = { onEventClick(event) }
                )
            }
            if (todayEvents.size > 5) {
                item {
                    Text(
                        "+${todayEvents.size - 5} more events",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        // ── Active tasks ──────────────────────────────────────
        item {
            SectionHeader(
                text = if (activeTasks.isEmpty()) "Tasks — All done!"
                       else "Tasks — ${activeTasks.size} active"
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
            items(activeTasks.take(8), key = { it.id }) { task ->
                TaskRow(
                    task     = task,
                    people   = people,
                    onToggle = { taskVm.toggleComplete(task) }
                )
            }
            if (activeTasks.size > 8) {
                item {
                    Text(
                        "+${activeTasks.size - 8} more tasks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }

        // ── Lists ─────────────────────────────────────────────
        item {
            SectionHeader(
                text = if (allLists.isEmpty()) "Lists" else "Lists — ${allLists.size}"
            )
        }
        if (allLists.isEmpty()) {
            item {
                EmptyHint(
                    text = "No lists yet",
                    onClick = onAddEvent /* just a hint, not literally adding */ 
                )
            }
        } else {
            items(allLists.take(6), key = { it.id }) { list ->
                ListRow(list = list)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Week strip — compact 7-day row
// ─────────────────────────────────────────────────────────────
@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    today: LocalDate,
    weekEvents: Map<LocalDate, List<CalendarEvent>>,
    onDayClick: (LocalDate) -> Unit,
    wall: Boolean,
    modifier: Modifier = Modifier
) {
    val bubbleSize  = if (wall) 40.dp else 32.dp
    val fontSizeVal  = if (wall) 14.sp else 12.sp
    val dowFontSize  = if (wall) 12.sp else 10.sp
    val dotSize      = if (wall) 6.dp else 4.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (i in 0..6) {
            val day     = weekStart.plusDays(i.toLong())
            val isToday = day == today
            val hasEvents = weekEvents[day]?.isNotEmpty() == true

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onDayClick(day) }
                    .padding(4.dp)
            ) {
                Text(
                    text       = day.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()),
                    fontSize   = dowFontSize,
                    color      = if (isToday) MaterialTheme.colorScheme.primary
                                 else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
                Spacer(Modifier.height(2.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(bubbleSize)
                        .clip(CircleShape)
                        .background(
                            when {
                                isToday -> Color(0xFFE07B39) // orange dot for today
                                else    -> Color.Transparent
                            }
                        )
                ) {
                    Text(
                        text       = day.dayOfMonth.toString(),
                        fontSize   = fontSizeVal,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color      = if (isToday) Color.White
                                     else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (hasEvents) {
                    Spacer(Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Show up to 3 small dots representing events
                        val n = minOf(weekEvents[day]!!.size, 3)
                        repeat(n) {
                            Box(
                                modifier = Modifier
                                    .size(dotSize)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Event row — single event in the today list
// ─────────────────────────────────────────────────────────────
@Composable
private fun EventRow(
    event: CalendarEvent,
    people: List<Person>,
    onClick: () -> Unit
) {
    val color = eventColor(event, people)
    val timeStr = if (!event.isAllDay) {
        Instant.ofEpochMilli(event.startMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    } else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color bar on the left
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = event.title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (timeStr != null) {
                Text(
                    text     = timeStr,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (event.location.isNotBlank()) {
            Text(
                text     = event.location,
                style    = MaterialTheme.typography.labelSmall,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Task row — single task in the active tasks list
// ─────────────────────────────────────────────────────────────
@Composable
private fun TaskRow(
    task: Task,
    people: List<Person>,
    onToggle: () -> Unit
) {
    val person = people.find { it.id == task.assignedPersonId }
    val personColor = person?.let {
        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Completion circle
        Checkbox(
            checked  = task.isCompleted,
            onCheckedChange = { onToggle() },
            colors   = CheckboxDefaults.colors(
                checkedColor = personColor ?: MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text              = task.title,
                style             = MaterialTheme.typography.bodyMedium,
                maxLines          = 1,
                textDecoration    = if (task.isCompleted) TextDecoration.LineThrough else null,
                color             = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant
                                     else MaterialTheme.colorScheme.onSurface
            )
        }
        if (person != null && !person.isDefault) {
            PersonChip(person = person, selected = false)
        }
        if (task.starsEarned > 0) {
            Spacer(Modifier.width(4.dp))
            Text("⭐${task.starsEarned}", fontSize = 12.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// List row — single checklist card
// ─────────────────────────────────────────────────────────────
@Composable
private fun ListRow(list: CheckList) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(list.colorHex))
    }.getOrElse { Color(0xFFFF9800) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.List, null,
                    tint = Color.White, modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text     = list.name,
                style    = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Empty hint — shown when a section has no data
// ─────────────────────────────────────────────────────────────
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Person chip — colored dot + initial
// ─────────────────────────────────────────────────────────────
@Composable
private fun PersonChip(
    person: Person,
    selected: Boolean
) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(person.colorHex))
    }.getOrNull() ?: MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(if (selected) color else color.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text     = person.initial,
            fontSize = 10.sp,
            color    = if (selected) Color.White else color
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Section header
// ─────────────────────────────────────────────────────────────
@Composable
private fun SectionHeader(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurface,
        modifier   = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

// ─────────────────────────────────────────────────────────────
// Event color helper — matches CalendarScreen
// ─────────────────────────────────────────────────────────────
private fun eventColor(event: CalendarEvent, people: List<Person>): Color {
    val fallback = Color(0xFF4A6178)
    if (event.colorHex.isNotBlank()) {
        return runCatching { Color(android.graphics.Color.parseColor(event.colorHex)) }
            .getOrElse { fallback }
    }
    if (event.personIds.isNotBlank()) {
        val firstId = event.personIds.split(",").firstOrNull()?.trim()?.toLongOrNull()
        val person  = people.find { it.id == firstId }
        if (person != null) {
            return runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
                .getOrElse { fallback }
        }
    }
    return fallback
}
