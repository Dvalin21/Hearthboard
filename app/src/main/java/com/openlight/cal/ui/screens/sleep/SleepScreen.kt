package com.openlight.cal.ui.screens.sleep

import androidx.compose.foundation.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.preferences.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// ─────────────────────────────────────────────────────────────
// SleepRecord — model for one night's sleep
// ─────────────────────────────────────────────────────────────
data class SleepRecord(
    val dateIso: String,            // date of BEDTIME (e.g., "2025-03-15" for Sunday night)
    val bedtimeMinute: Int,         // minutes since midnight (22*60+30 = 22:30)
    val wakeMinute: Int,            // minutes since midnight (7*60 = 07:00)
    val notes: String = ""
) {
    /** Duration in minutes. Handles overnight sleep (wake < bedtime -> next day). */
    val durationMinutes: Int get() {
        val dur = wakeMinute - bedtimeMinute
        return if (dur < 0) dur + 24 * 60 else dur
    }

    val durationHours: String get() {
        val h = durationMinutes / 60
        val m = durationMinutes % 60
        return "${h}h ${m}m"
    }

    val bedtimeDisplay: String get() {
        val h = bedtimeMinute / 60
        val m = bedtimeMinute % 60
        return String.format("%d:%02d", h, m)
    }

    val wakeDisplay: String get() {
        val h = wakeMinute / 60
        val m = wakeMinute % 60
        return String.format("%d:%02d", h, m)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("dateIso", dateIso)
        put("bedtimeMinute", bedtimeMinute)
        put("wakeMinute", wakeMinute)
        put("notes", notes)
    }

    companion object {
        fun fromJson(obj: JSONObject): SleepRecord = SleepRecord(
            dateIso        = obj.getString("dateIso"),
            bedtimeMinute  = obj.getInt("bedtimeMinute"),
            wakeMinute     = obj.getInt("wakeMinute"),
            notes          = obj.optString("notes", "")
        )
    }
}

// ─────────────────────────────────────────────────────────────
// SleepScreen — manual sleep log with weekly summary
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val prefs   = remember { AppPreferences(context) }
    val KEY_SLEEP = stringPreferencesKey("sleep_records")

    // Load sleep records from DataStore
    val sleepRecordsState = remember { mutableStateOf<List<SleepRecord>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        val json = context.dataStore.data.map { it[KEY_SLEEP] ?: "[]" }.first()
        sleepRecordsState.value = parseSleepRecords(json)
    }

    var records by sleepRecordsState
    val today = LocalDate.now()

    // Find or create today's record (today = night that starts today)
    val todayIso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
    val todaysRecordIndex = records.indexOfFirst { it.dateIso == todayIso }

    // Editable state for today's entry
    var bedtimeHour   by remember { mutableStateOf("22") }
    var bedtimeMin    by remember { mutableStateOf("00") }
    var wakeHour      by remember { mutableStateOf("07") }
    var wakeMin       by remember { mutableStateOf("00") }
    var notes         by remember { mutableStateOf("") }
    var showSaveSuccess by remember { mutableStateOf(false) }

    // Initialize fields from existing record
    LaunchedEffect(todaysRecordIndex) {
        if (todaysRecordIndex >= 0) {
            val rec = records[todaysRecordIndex]
            bedtimeHour = String.format("%d", rec.bedtimeMinute / 60)
            bedtimeMin  = String.format("%02d", rec.bedtimeMinute % 60)
            wakeHour    = String.format("%d", rec.wakeMinute / 60)
            wakeMin     = String.format("%02d", rec.wakeMinute % 60)
            notes       = rec.notes
        }
    }

    fun saveRecord() {
        val bh = bedtimeHour.toIntOrNull() ?: return
        val bm = bedtimeMin.toIntOrNull() ?: return
        val wh = wakeHour.toIntOrNull() ?: return
        val wm = wakeMin.toIntOrNull() ?: return
        if (bh !in 0..23 || bm !in 0..59 || wh !in 0..23 || wm !in 0..59) return

        val newRecord = SleepRecord(
            dateIso       = todayIso,
            bedtimeMinute = bh * 60 + bm,
            wakeMinute    = wh * 60 + wm,
            notes         = notes.trim()
        )

        val updated = if (todaysRecordIndex >= 0) {
            records.toMutableList().also { it[todaysRecordIndex] = newRecord }
        } else {
            records + newRecord
        }

        scope.launch {
            context.dataStore.edit { prefs ->
                prefs[KEY_SLEEP] = serializeSleepRecords(updated)
            }
            records = updated
            showSaveSuccess = true
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Sleep") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ── "Last Night" entry card ─────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Tonight's Sleep",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        today.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(16.dp))

                    // Bedtime row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Bedtime",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(70.dp)
                        )
                        TimeField(
                            value = bedtimeHour,
                            onValueChange = { bedtimeHour = it.filter(Char::isDigit).take(2) },
                            placeholder = "HH",
                            modifier = Modifier.width(52.dp)
                        )
                        Text(":", style = MaterialTheme.typography.bodyLarge)
                        TimeField(
                            value = bedtimeMin,
                            onValueChange = { bedtimeMin = it.filter(Char::isDigit).take(2) },
                            placeholder = "MM",
                            modifier = Modifier.width(52.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Wake time row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Wake up",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.width(70.dp)
                        )
                        TimeField(
                            value = wakeHour,
                            onValueChange = { wakeHour = it.filter(Char::isDigit).take(2) },
                            placeholder = "HH",
                            modifier = Modifier.width(52.dp)
                        )
                        Text(":", style = MaterialTheme.typography.bodyLarge)
                        TimeField(
                            value = wakeMin,
                            onValueChange = { wakeMin = it.filter(Char::isDigit).take(2) },
                            placeholder = "MM",
                            modifier = Modifier.width(52.dp)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Notes
                    OutlinedTextField(
                        value         = notes,
                        onValueChange = { notes = it },
                        modifier      = Modifier.fillMaxWidth(),
                        label         = { Text("Notes (optional)") },
                        singleLine    = true,
                        leadingIcon   = { Icon(Icons.AutoMirrored.Filled.Notes, null) }
                    )

                    Spacer(Modifier.height(12.dp))

                    // Save button + duration preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Preview duration
                        val bh = bedtimeHour.toIntOrNull()
                        val bm = bedtimeMin.toIntOrNull()
                        val wh = wakeHour.toIntOrNull()
                        val wm = wakeMin.toIntOrNull()
                        if (bh != null && bm != null && wh != null && wm != null &&
                            bh in 0..23 && bm in 0..59 && wh in 0..23 && wm in 0..59
                        ) {
                            val preview = SleepRecord(todayIso, bh*60+bm, wh*60+wm)
                            Text(
                                "~ ${preview.durationHours}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Button(onClick = { saveRecord() }) {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (todaysRecordIndex >= 0) "Update" else "Log Sleep")
                        }
                    }

                    // Save success toast-like indicator
                    AnimatedVisibility(visible = showSaveSuccess) {
                        Text(
                            "Saved!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Weekly summary ─────────────────────────
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekDays = (0..6).map { weekStart.plusDays(it.toLong()) }

            Text(
                "This Week",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            WeeklySleepChart(
                records   = records,
                weekDays  = weekDays,
                modifier  = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(140.dp)
            )

            Spacer(Modifier.height(16.dp))

            // ── Recent entries list ────────────────────
            val sorted = records.sortedByDescending { it.dateIso }

            if (sorted.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "No sleep records yet.\nLog tonight's sleep above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Text(
                    "Recent Logs",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
                )
                sorted.take(14).forEach { record ->
                    SleepRecordRow(
                        record   = record,
                        onDelete = {
                            val updated = records.filter { it.dateIso != record.dateIso }
                            scope.launch {
                                context.dataStore.edit { prefs ->
                                    prefs[KEY_SLEEP] = serializeSleepRecords(updated)
                                }
                                records = updated
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// TimeField — small text field for hour/minute input
// ─────────────────────────────────────────────────────────────
@Composable
private fun TimeField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        modifier      = modifier,
        placeholder   = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        singleLine    = true,
        textStyle     = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

// ─────────────────────────────────────────────────────────────
// WeeklySleepChart — bar chart showing sleep duration per night
// ─────────────────────────────────────────────────────────────
@Composable
private fun WeeklySleepChart(
    records: List<SleepRecord>,
    weekDays: List<LocalDate>,
    modifier: Modifier = Modifier
) {
    val maxMinutes = 600 // 10 hours = max bar height
    val recordMap = records.associateBy { it.dateIso }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            weekDays.forEach { day ->
                val iso = day.format(DateTimeFormatter.ISO_LOCAL_DATE)
                val record = recordMap[iso]
                val fraction = record?.let {
                    (it.durationMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)
                } ?: 0f
                val isToday = day == LocalDate.now()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Duration label
                    if (record != null) {
                        Text(
                            record.durationHours,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Bar
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height((100 * fraction).dp.coerceAtLeast(if (fraction > 0f) 4.dp else 0.dp))
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(
                                if (record != null)
                                    if (record.durationMinutes in 420..600)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                    )

                    Spacer(Modifier.height(4.dp))

                    // Day label
                    Text(
                        day.format(DateTimeFormatter.ofPattern("EEE")),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SleepRecordRow — single entry in recent logs
// ─────────────────────────────────────────────────────────────
@Composable
private fun SleepRecordRow(
    record: SleepRecord,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val date = runCatching {
        LocalDate.parse(record.dateIso, DateTimeFormatter.ISO_LOCAL_DATE)
    }.getOrNull()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = date?.format(DateTimeFormatter.ofPattern("EEE, MMM d")) ?: record.dateIso,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text  = "${record.bedtimeDisplay} – ${record.wakeDisplay}  •  ${record.durationHours}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (record.notes.isNotBlank()) {
                Text(
                    text  = record.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        // Duration indicator
        val color = when {
            record.durationMinutes < 360 -> MaterialTheme.colorScheme.error
            record.durationMinutes < 420 -> MaterialTheme.colorScheme.tertiary
            record.durationMinutes <= 600 -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.secondary
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${record.durationMinutes / 60}h",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.DeleteOutline,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// JSON serialization helpers
// ─────────────────────────────────────────────────────────────
private fun parseSleepRecords(json: String): List<SleepRecord> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            SleepRecord.fromJson(arr.getJSONObject(i))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun serializeSleepRecords(records: List<SleepRecord>): String {
    val arr = JSONArray()
    records.forEach { arr.put(it.toJson()) }
    return arr.toString()
}
