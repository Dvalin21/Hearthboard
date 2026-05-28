package com.openlight.cal.ui.screens.chores

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.Person
import com.openlight.cal.data.model.Task
import com.openlight.cal.data.model.TaskPriority
import com.openlight.cal.ui.screens.tasks.TaskEditDialog
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Kid-friendly Chores screen.
 * Shows only uncompleted chores (isChore=true) in big, colorful cards grouped by person.
 * No CalDAV sync — local only. Includes star rewards display.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChoresScreen(
    database: AppDatabase,
    people: List<Person>,
    onComplete: (Task) -> Unit,
    onSaveChore: (Task) -> Unit,
    onDeleteChore: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val chores by database.taskDao().getActiveChoresFlow()
        .collectAsState(initial = emptyList())

    var showAddChore by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("⭐ Chores") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddChore = true }) {
                Icon(Icons.Default.Add, "Add chore")
            }
        }
    ) { padding ->
        if (chores.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircleOutline, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("All chores done! 🎉",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Great job, everyone!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Group by person
                val grouped = people.filter { !it.isDefault }.map { person ->
                    person to chores.filter { it.assignedPersonId == person.id || it.assignedPersonId == 0L }
                }.filter { it.second.isNotEmpty() }

                // Unassigned chores
                val unassigned = chores.filter { it.assignedPersonId != 0L && people.none { p -> p.id == it.assignedPersonId } }
                if (unassigned.isNotEmpty()) {
                    item { ChoreGroupHeader("Everyone", Color(0xFF2196F3), unassigned.size) }
                    items(unassigned, key = { it.id }) { task ->
                        ChoreCard(task = task, personColor = Color(0xFF2196F3), onComplete = { onComplete(task) })
                    }
                }

                grouped.forEach { (person, personTasks) ->
                    val color = runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
                        .getOrElse { Color.Gray }
                    item { ChoreGroupHeader(person.name, color, personTasks.size) }
                    items(personTasks, key = { it.id }) { task ->
                        ChoreCard(task = task, personColor = color, onComplete = { onComplete(task) })
                    }
                }
            }
        }
    }

    if (showAddChore) {
        TaskEditDialog(
            task      = null,
            people    = people,
            onSave    = { chore ->
                onSaveChore(chore)
                showAddChore = false
            },
            onDismiss = { showAddChore = false },
            isChoreMode = true
        )
    }
}

@Composable
private fun ChoreGroupHeader(name: String, color: Color, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text("$name's Chores", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("$count", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChoreCard(
    task: Task,
    personColor: Color,
    onComplete: () -> Unit
) {
    var celebrate by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (celebrate) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 200f),
        label = "choreCelebrate"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = personColor.copy(alpha = 0.08f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, personColor.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color side accent
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(personColor)
            )
            Spacer(Modifier.width(14.dp))

            // Task info
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                task.dueMs?.let { dueMs ->
                    val dueDate = Instant.ofEpochMilli(dueMs).atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    Text(dueDate.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (task.starsEarned > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text("⭐".repeat(task.starsEarned), fontSize = 16.sp)
                }
            }

            // Big done button
            FilledTonalButton(
                onClick = {
                    celebrate = true
                    onComplete()
                },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = personColor,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Check, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(4.dp))
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }

    LaunchedEffect(celebrate) {
        if (celebrate) {
            delay(400)
        }
    }
}