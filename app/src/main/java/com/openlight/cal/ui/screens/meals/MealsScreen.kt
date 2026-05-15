package com.openlight.cal.ui.screens.meals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.MealPlan
import com.openlight.cal.data.model.MealSlot
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealsScreen(
    database: AppDatabase,
    modifier: Modifier = Modifier
) {
    val dao   = remember { database.mealPlanDao() }
    val scope = rememberCoroutineScope()

    var weekStart by remember {
        mutableStateOf(
            LocalDate.now().with(DayOfWeek.MONDAY)
                .let { if (it.isAfter(LocalDate.now())) it.minusWeeks(1) else it }
        )
    }
    val weekEnd = weekStart.plusDays(6)

    val meals by dao.getWeekFlow(
        weekStart.toString(),
        weekEnd.toString()
    ).collectAsState(initial = emptyList())

    val mealMap = meals.associateBy { "${it.dateIso}_${it.slot}" }

    var editTarget by remember { mutableStateOf<Pair<LocalDate, MealSlot>?>(null) }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with week nav
        TopAppBar(
            title = {
                Text(
                    text = weekLabel(weekStart),
                    style = MaterialTheme.typography.titleMedium
                )
            },
            navigationIcon = {
                IconButton(onClick = { weekStart = weekStart.minusWeeks(1) }) {
                    Icon(Icons.Default.ChevronLeft, "Previous week")
                }
            },
            actions = {
                TextButton(onClick = {
                    weekStart = LocalDate.now()
                        .with(DayOfWeek.MONDAY)
                        .let { if (it.isAfter(LocalDate.now())) it.minusWeeks(1) else it }
                }) { Text("This week") }
                IconButton(onClick = { weekStart = weekStart.plusWeeks(1) }) {
                    Icon(Icons.Default.ChevronRight, "Next week")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        HorizontalDivider()

        // Slot header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(vertical = 6.dp)
        ) {
            Spacer(Modifier.width(64.dp))
            MealSlot.values().forEach { slot ->
                Text(
                    text      = slot.name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier  = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Days grid
        val scrollState = rememberScrollState()
        Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
            val today = LocalDate.now()
            for (dayOffset in 0..6L) {
                val day = weekStart.plusDays(dayOffset)
                val isToday = day == today

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Day label
                    Column(
                        modifier = Modifier.width(64.dp).padding(start = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                        Text(
                            text = day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Meal slots
                    MealSlot.values().forEach { slot ->
                        val key  = "${day}_$slot"
                        val meal = mealMap[key]
                        MealCell(
                            meal     = meal,
                            onClick  = { editTarget = day to slot },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(2.dp)
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }

    // Edit meal dialog
    editTarget?.let { (day, slot) ->
        val existing = mealMap["${day}_${slot}"]
        MealEditDialog(
            day       = day,
            slot      = slot,
            existing  = existing,
            onSave    = { title, notes ->
                scope.launch {
                    dao.upsert(MealPlan(dateIso = day.toString(), slot = slot,
                        title = title, notes = notes))
                }
                editTarget = null
            },
            onDelete  = if (existing != null) {
                { scope.launch { dao.delete(existing) }; editTarget = null }
            } else null,
            onDismiss = { editTarget = null }
        )
    }
}

@Composable
private fun MealCell(
    meal: MealPlan?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (meal != null && meal.title.isNotBlank()) {
            Text(
                text      = meal.title,
                style     = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines  = 3,
                textAlign = TextAlign.Center,
                color     = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add meal",
                tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealEditDialog(
    day: LocalDate,
    slot: MealSlot,
    existing: MealPlan?,
    onSave: (String, String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    val fmt = DateTimeFormatter.ofPattern("EEEE, MMMM d")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(slot.name.lowercase().replaceFirstChar { it.uppercase() })
                Text(
                    text  = day.format(fmt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value         = title,
                    onValueChange = { title = it },
                    label         = { Text("What's for ${slot.name.lowercase()}?") },
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true,
                    leadingIcon   = { Icon(Icons.Default.Restaurant, null) }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value         = notes,
                    onValueChange = { notes = it },
                    label         = { Text("Notes / recipe (optional)") },
                    modifier      = Modifier.fillMaxWidth().height(80.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onSave(title.trim(), notes.trim())
                else onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private fun weekLabel(weekStart: LocalDate): String {
    val weekEnd = weekStart.plusDays(6)
    return if (weekStart.month == weekEnd.month)
        "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – ${weekEnd.dayOfMonth}, ${weekEnd.year}"
    else
        "${weekStart.format(DateTimeFormatter.ofPattern("MMM d"))} – ${weekEnd.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
}
