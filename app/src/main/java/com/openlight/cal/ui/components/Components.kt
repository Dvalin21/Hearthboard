package com.openlight.cal.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.model.*
import com.openlight.cal.ui.theme.PersonColors
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ─────────────────────────────────────────────────────────────
// PersonChip — colored avatar with initial, no emoji
// ─────────────────────────────────────────────────────────────
@Composable
fun PersonChip(
    person: Person,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val color = remember(person.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }.getOrElse { Color.Gray }
    }
    val content: @Composable RowScope.() -> Unit = {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .background(color, CircleShape)
        ) {
            Text(
                text     = person.initial,
                color    = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(person.name, style = MaterialTheme.typography.labelMedium)
    }

    if (onClick != null) {
        FilterChip(
            selected = selected,
            onClick  = onClick,
            label    = { Row(verticalAlignment = Alignment.CenterVertically, content = content) },
            modifier = modifier
        )
    } else {
        AssistChip(
            onClick  = {},
            label    = { Row(verticalAlignment = Alignment.CenterVertically, content = content) },
            modifier = modifier
        )
    }
}

// ─────────────────────────────────────────────────────────────
// PersonRow — scrollable person selector
// ─────────────────────────────────────────────────────────────
@Composable
fun PersonFilterRow(
    people: List<Person>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier             = modifier,
        horizontalArrangement= Arrangement.spacedBy(0.dp)
    ) {
        // "All" button as round icon
        val allSelected = selectedId == 0L
        Box(
            modifier = Modifier
                .padding(end = 4.dp)
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (allSelected) Color(0xFFF3E8FF) else Color.Transparent)
                .clickable { onSelect(0L) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text     = "All",
                fontSize = 11.sp,
                fontWeight = if (allSelected) FontWeight.SemiBold else FontWeight.Normal,
                color    = if (allSelected) Color(0xFF7C4DFF) else Color(0xFF6B7280)
            )
        }

        people.filter { !it.isDefault }.forEach { person ->
            val sel = selectedId == person.id
            val col = runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
                .getOrElse { Color(0xFF7C4DFF) }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (sel) col.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onSelect(person.id) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(col),
                    contentAlignment = Alignment.Center
                ) {
                    Text(person.initial, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text     = person.name,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color    = if (sel) Color(0xFF1F2937) else Color(0xFF6B7280)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ColorPicker — 16-color grid, no emojis
// ─────────────────────────────────────────────────────────────
@Composable
fun ColorPickerGrid(
    selectedHex: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val cols = 8
    Column(modifier = modifier) {
        PersonColors.chunked(cols).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { hex ->
                    val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrElse { Color.Gray }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width  = if (hex == selectedHex) 3.dp else 0.dp,
                                color  = MaterialTheme.colorScheme.onSurface,
                                shape  = CircleShape
                            )
                            .clickable { onSelect(hex) }
                            .semantics {
                                this[SemanticsProperties.Role] = Role.Button
                                contentDescription = "Color option ${hex.substringAfterLast('#')}"
                            }
                    ) {
                        if (hex == selectedHex) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint     = Color.White,
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// EventCard — compact event display with person color stripe
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventCard(
    event: CalendarEvent,
    people: List<Person>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = remember(event.colorHex, event.personIds) {
        when {
            event.colorHex.isNotBlank() ->
                runCatching { Color(android.graphics.Color.parseColor(event.colorHex)) }.getOrElse { Color(0xFF2196F3) }
            event.personIds.isNotBlank() -> {
                val firstId = event.personIds.split(",").firstOrNull()?.trim()?.toLongOrNull()
                val person  = people.find { it.id == firstId }
                person?.let { runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrElse { Color(0xFF2196F3) } }
                    ?: Color(0xFF2196F3)
            }
            else -> Color(0xFF2196F3)
        }
    }

    val startTime = remember(event.startMs) {
        Instant.ofEpochMilli(event.startMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    Card(
        onClick    = onClick,
        modifier   = modifier
            .fillMaxWidth()
            .semantics {
                this[SemanticsProperties.Role] = Role.Button
                val timeStr = if (!event.isAllDay) {
                    Instant.ofEpochMilli(event.startMs)
                        .atZone(ZoneId.systemDefault())
                        .toLocalTime()
                        .format(DateTimeFormatter.ofPattern("h:mm a"))
                } else "all day"
                val loc = if (event.location.isNotBlank()) ", at ${event.location}" else ""
                contentDescription = "Event: ${event.title}, ${timeStr}$loc"
            },
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Color stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(color)
            )
            Column(
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .weight(1f)
            ) {
                Text(
                    text     = event.title,
                    style    = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!event.isAllDay) {
                    Text(
                        text  = startTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (event.location.isNotBlank()) {
                    Text(
                        text  = event.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (event.isAllDay) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .align(Alignment.CenterVertically)
                ) {
                    Text(
                        "All day",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// TaskItem — checkbox row with person color dot
// ─────────────────────────────────────────────────────────────
@Composable
fun TaskItem(
    task: Task,
    person: Person?,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val personColor = person?.let {
        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }.getOrElse { Color.Gray }
    }

    var showDelete by remember { mutableStateOf(false) }

    @OptIn(ExperimentalFoundationApi::class)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick      = { onToggle() },
                onLongClick  = { showDelete = !showDelete }
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked  = task.isCompleted,
            onCheckedChange = { onToggle() }
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text           = task.title,
                style          = MaterialTheme.typography.bodyMedium,
                textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                color          = if (task.isCompleted)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurface
            )
            task.dueMs?.let { dueMs ->
                val due = Instant.ofEpochMilli(dueMs).atZone(ZoneId.systemDefault()).toLocalDate()
                val today = LocalDate.now()
                val isOverdue = due.isBefore(today) && !task.isCompleted
                Text(
                    text  = due.format(DateTimeFormatter.ofPattern("MMM d")),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Priority indicator
        if (task.priority == TaskPriority.HIGH) {
            Icon(
                Icons.Default.PriorityHigh,
                contentDescription = "High priority",
                tint    = MaterialTheme.colorScheme.error,
                modifier= Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }

        // Stars (chore reward)
        if (task.starsEarned > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "⭐".repeat(task.starsEarned),
                fontSize = 12.sp
            )
        }

        // Person color dot
        if (personColor != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(personColor, CircleShape)
            )
        }

        // Delete button
        AnimatedVisibility(visible = showDelete) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// CountdownCard — days remaining to an event
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownCard(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eventDate = remember(event.startMs) {
        Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val daysLeft = remember(eventDate) {
        ChronoUnit.DAYS.between(LocalDate.now(), eventDate)
    }

    Card(
        onClick   = onClick,
        modifier  = modifier.width(140.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = if (daysLeft == 0L) "TODAY" else "$daysLeft",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (daysLeft != 0L) {
                Text(
                    text  = "days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text     = event.title,
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// CountdownChip — compact horizontal-rail version (≤ 60dp tall)
// Title + days inline. Designed for header strips above the calendar
// where vertical space is at a premium.
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownChip(
    event: CalendarEvent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val eventDate = remember(event.startMs) {
        Instant.ofEpochMilli(event.startMs).atZone(ZoneId.systemDefault()).toLocalDate()
    }
    val daysLeft = remember(eventDate) {
        ChronoUnit.DAYS.between(LocalDate.now(), eventDate)
    }
    val daysText = when {
        daysLeft <  0L -> "${-daysLeft}d ago"
        daysLeft == 0L -> "TODAY"
        daysLeft == 1L -> "tomorrow"
        else           -> "${daysLeft}d"
    }
    AssistChip(
        onClick = onClick,
        label   = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = event.title,
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.widthIn(max = 140.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = daysText,
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.primary
                )
            }
        },
        colors  = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        modifier = modifier
            .semantics {
                this[SemanticsProperties.Role] = Role.Button
                contentDescription = "Countdown: ${event.title}, ${daysText}"
            }
    )
}

// ─────────────────────────────────────────────────────────────
// InvitationChip — compact pending-invite tile for header strip
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvitationChip(
    title: String,
    organizer: String,
    onAccept: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f))
            .clickable(onClick = onClick)
            .semantics {
                this[SemanticsProperties.Role] = Role.Button
                val org = if (organizer.isNotBlank()) " from $organizer" else ""
                contentDescription = "Invitation: $title$org. Double tap to view, or use Accept button."
            }
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = Icons.Default.Email,
            contentDescription = "Pending invitation",
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.widthIn(max = 160.dp)) {
            Text(
                text       = title,
                style      = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            if (organizer.isNotBlank()) {
                Text(
                    text     = organizer,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            onClick        = onAccept,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            modifier       = Modifier.heightIn(min = 32.dp)
        ) {
            Text("Accept", style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SectionHeader
// ─────────────────────────────────────────────────────────────
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.titleSmall,
        color    = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
