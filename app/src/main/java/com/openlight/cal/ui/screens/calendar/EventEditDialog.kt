@file:Suppress("DEPRECATION")

package com.openlight.cal.ui.screens.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.openlight.cal.data.model.CalendarAccount
import com.openlight.cal.data.model.CalendarEvent
import com.openlight.cal.data.model.Person
import com.openlight.cal.ui.components.ColorPickerGrid
import com.openlight.cal.ui.components.PersonChip
import com.openlight.cal.data.sync.ICalParser
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditDialog(
    event: CalendarEvent?,
    people: List<Person>,
    accounts: List<CalendarAccount>,
    preselectedDate: LocalDate = LocalDate.now(),
    onSave: (CalendarEvent, Long?) -> Unit,
    onDelete: ((CalendarEvent) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val isNew = event == null

    var title       by remember { mutableStateOf(event?.title ?: "") }
    var location    by remember { mutableStateOf(event?.location ?: "") }
    var description by remember { mutableStateOf(event?.description ?: "") }
    var isAllDay    by remember { mutableStateOf(event?.isAllDay ?: false) }
    var isCountdown by remember { mutableStateOf(event?.isCountdown ?: false) }
    var colorHex    by remember { mutableStateOf(event?.colorHex ?: "") }
    var selectedPersonIds by remember {
        mutableStateOf(
            event?.personIds?.split(",")?.mapNotNull { it.trim().toLongOrNull() }?.toSet() ?: emptySet()
        )
    }
    var selectedAccountId by remember { mutableStateOf(accounts.firstOrNull()?.id) }

    // Date/time state
    val initStart = event?.let {
        Instant.ofEpochMilli(it.startMs).atZone(ZoneId.systemDefault())
    } ?: preselectedDate.atTime(9, 0).atZone(ZoneId.systemDefault())
    val initEnd = event?.let {
        Instant.ofEpochMilli(it.endMs).atZone(ZoneId.systemDefault())
    } ?: preselectedDate.atTime(10, 0).atZone(ZoneId.systemDefault())

    var startDate by remember { mutableStateOf(initStart.toLocalDate()) }
    var startTime by remember { mutableStateOf(initStart.toLocalTime()) }
    var endDate   by remember { mutableStateOf(initEnd.toLocalDate()) }
    var endTime   by remember { mutableStateOf(initEnd.toLocalTime()) }

    var showColorPicker   by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var titleError        by remember { mutableStateOf(false) }

    // ── Interactive date/time picker state ────────────────────
    var activePicker by remember { mutableStateOf<PickerTarget?>(null) }

    val dateFmt = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val timeFmt = DateTimeFormatter.ofPattern("h:mm a")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Text(
                    if (isNew) "New Event" else "Edit Event",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = {
                    if (title.isBlank()) { titleError = true; return@TextButton }
                    val startMs = startDate.atTime(if (isAllDay) LocalTime.MIDNIGHT else startTime)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val endMs = endDate.atTime(if (isAllDay) LocalTime.MIDNIGHT else endTime)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val uid = event?.uid?.ifBlank { ICalParser.generateUid() } ?: ICalParser.generateUid()
                    onSave(
                        (event ?: CalendarEvent(title = "", startMs = 0, endMs = 0)).copy(
                            uid         = uid,
                            title       = title.trim(),
                            location    = location.trim(),
                            description = description.trim(),
                            isAllDay    = isAllDay,
                            isCountdown = isCountdown,
                            colorHex    = colorHex,
                            startMs     = startMs,
                            endMs       = endMs.coerceAtLeast(startMs + 1800_000),
                            personIds   = selectedPersonIds.joinToString(",")
                        ),
                        selectedAccountId
                    )
                }) { Text("Save", style = MaterialTheme.typography.labelLarge) }
            }

            Spacer(Modifier.height(8.dp))

            // Title
            OutlinedTextField(
                value         = title,
                onValueChange = { title = it; titleError = false },
                label         = { Text("Title") },
                isError       = titleError,
                modifier      = Modifier.fillMaxWidth(),
                leadingIcon   = { Icon(Icons.Default.Title, null) },
                singleLine    = true
            )
            if (titleError) {
                Text("Title is required", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 16.dp))
            }

            Spacer(Modifier.height(12.dp))

            // All Day toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("All Day", style = MaterialTheme.typography.bodyMedium)
                }
                Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
            }

            Spacer(Modifier.height(8.dp))

            // Start date/time (interactive pickers)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    onClick  = { activePicker = PickerTarget.START_DATE }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Start", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(startDate.format(dateFmt), style = MaterialTheme.typography.bodyMedium)
                        if (!isAllDay) {
                            OutlinedCard(
                                onClick = { activePicker = PickerTarget.START_TIME },
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(startTime.format(timeFmt), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
                OutlinedCard(
                    modifier = Modifier.weight(1f),
                    onClick  = { activePicker = PickerTarget.END_DATE }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("End", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(endDate.format(dateFmt), style = MaterialTheme.typography.bodyMedium)
                        if (!isAllDay) {
                            OutlinedCard(
                                onClick = { activePicker = PickerTarget.END_TIME },
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(endTime.format(timeFmt), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Location
            OutlinedTextField(
                value         = location,
                onValueChange = { location = it },
                label         = { Text("Location (optional)") },
                modifier      = Modifier.fillMaxWidth(),
                leadingIcon   = { Icon(Icons.Default.LocationOn, null) },
                singleLine    = true
            )

            Spacer(Modifier.height(12.dp))

            // Description / Notes
            OutlinedTextField(
                value         = description,
                onValueChange = { description = it },
                label         = { Text("Notes (optional)") },
                modifier      = Modifier.fillMaxWidth().height(100.dp),
                leadingIcon = { Icon(Icons.Default.Notes, null) }
            )

            Spacer(Modifier.height(12.dp))

            // Assign to people
            if (people.isNotEmpty()) {
                Text("Assign to", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        PersonChip(
                            person   = person,
                            selected = person.id in selectedPersonIds,
                            onClick  = {
                                selectedPersonIds = if (person.id in selectedPersonIds)
                                    selectedPersonIds - person.id
                                else selectedPersonIds + person.id
                            }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Color override
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("Event Color", style = MaterialTheme.typography.bodyMedium)
                }
                if (colorHex.isNotBlank()) {
                    val c = runCatching {
                        androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(colorHex))
                    }.getOrNull()
                    if (c != null) {
                        Box(modifier = Modifier
                            .size(24.dp)
                            .background(c, CircleShape)
                        )
                    }
                }
                TextButton(onClick = { showColorPicker = !showColorPicker }) {
                    Text(if (colorHex.isBlank()) "Set color" else "Change")
                }
            }
            if (showColorPicker) {
                ColorPickerGrid(selectedHex = colorHex, onSelect = { colorHex = it })
                Spacer(Modifier.height(8.dp))
            }

            // Countdown toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Show Countdown", style = MaterialTheme.typography.bodyMedium)
                        Text("Display days-remaining widget", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(checked = isCountdown, onCheckedChange = { isCountdown = it })
            }

            // Account selector
            if (accounts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Save to", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                accounts.forEach { account ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected  = selectedAccountId == account.id,
                            onClick   = { selectedAccountId = account.id }
                        )
                        Text(account.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Delete button (edit mode only)
            if (!isNew && onDelete != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Event")
                }
            }
        }
    }

    // ── Date/time picker dialogs ──────────────────────────────
    when (activePicker) {
        PickerTarget.START_DATE -> DatePickerAlert(
            currentDate  = startDate,
            onDateSelected = { startDate = it },
            onDismiss    = { activePicker = null }
        )
        PickerTarget.END_DATE -> DatePickerAlert(
            currentDate  = endDate,
            onDateSelected = { endDate = it },
            onDismiss    = { activePicker = null }
        )
        PickerTarget.START_TIME -> TimePickerAlert(
            currentTime   = startTime,
            onTimeSelected = { startTime = it },
            onDismiss     = { activePicker = null }
        )
        PickerTarget.END_TIME -> TimePickerAlert(
            currentTime   = endTime,
            onTimeSelected = { endTime = it },
            onDismiss     = { activePicker = null }
        )
        null -> { /* no picker visible */ }
    }

    if (showDeleteConfirm && event != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title            = { Text("Delete Event") },
            text             = { Text("Are you sure you want to delete \"${event.title}\"?") },
            confirmButton    = {
                TextButton(onClick = { onDelete?.invoke(event); onDismiss() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Picker targets
// ─────────────────────────────────────────────────────────────
private enum class PickerTarget { START_DATE, START_TIME, END_DATE, END_TIME }

// ─────────────────────────────────────────────────────────────
// Date Picker Dialog wrapper
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerAlert(
    currentDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMillis = currentDate
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onDateSelected(Instant.ofEpochMilli(millis)
                        .atZone(ZoneId.systemDefault()).toLocalDate())
                }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = state)
    }
}

// ─────────────────────────────────────────────────────────────
// Time Picker Dialog wrapper
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerAlert(
    currentTime: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour   = currentTime.hour,
        initialMinute = currentTime.minute,
        is24Hour      = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title    = { Text("Select time") },
        text     = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                onTimeSelected(LocalTime.of(state.hour, state.minute))
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Shim to let EventEditDialog use ICalParser.generateUid without full import path collision
private object ICalParser {
    fun generateUid() = com.openlight.cal.data.sync.ICalParser.generateUid()
}
