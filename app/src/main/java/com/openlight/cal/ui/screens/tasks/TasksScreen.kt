package com.openlight.cal.ui.screens.tasks

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.model.*
import com.openlight.cal.ui.components.*
import com.openlight.cal.ui.viewmodel.TaskTypeFilter
import com.openlight.cal.ui.viewmodel.TaskViewModel
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

// ─────────────────────────────────────────────────────────────
// View mode enum
// ─────────────────────────────────────────────────────────────
private enum class TaskViewMode { LIST, DAY, WEEK }

// ─────────────────────────────────────────────────────────────
// TasksScreen — List / Day / Week timeline views
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val tasks       by viewModel.filteredTasks.collectAsState()
    val people      by viewModel.people.collectAsState()
    val personFilter by viewModel.selectedPersonFilter.collectAsState()
    val typeFilter   by viewModel.selectedTypeFilter.collectAsState()

    var showAddTask by remember { mutableStateOf(false) }
    var editTask    by remember { mutableStateOf<Task?>(null) }
    var viewMode    by remember { mutableStateOf(TaskViewMode.LIST) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
                actions = {
                    IconButton(onClick = { showAddTask = true }) {
                        Icon(Icons.Default.Add, "Add task")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddTask = true }) {
                Icon(Icons.Default.Add, "Add task")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── View mode toggle ────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TaskViewMode.values().forEach { mode ->
                    FilterChip(
                        selected = viewMode == mode,
                        onClick  = { viewMode = mode },
                        label = {
                            Text(
                                when (mode) {
                                    TaskViewMode.LIST -> "List"
                                    TaskViewMode.DAY  -> "Day"
                                    TaskViewMode.WEEK -> "Week"
                                }
                            )
                        },
                        leadingIcon = {
                            Icon(
                                when (mode) {
                                    TaskViewMode.LIST -> Icons.AutoMirrored.Filled.List
                                    TaskViewMode.DAY  -> Icons.Default.CalendarToday
                                    TaskViewMode.WEEK -> Icons.Default.DateRange
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    )
                }
            }

            // ── Type filter row (All / Tasks / Chores) ───
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TaskTypeFilter.entries.forEach { type ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick  = { viewModel.setTypeFilter(type) },
                        label = { Text(type.label, style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }

            // Person filter row (all modes)
            if (people.size > 1) {
                PersonFilterRow(
                    people     = people,
                    selectedId = personFilter,
                    onSelect   = viewModel::setPersonFilter,
                    modifier   = Modifier.padding(vertical = 4.dp)
                )
                HorizontalDivider()
            }

            // ── Content by view mode ────────────────────
            when (viewMode) {
                TaskViewMode.LIST -> ListView(tasks, people, viewModel, showAddTask, editTask,
                    onAdd = { showAddTask = true },
                    onEdit = { editTask = it },
                    onAddDismiss = { showAddTask = false; editTask = null })

                TaskViewMode.DAY  -> ProfileDayTimeline(tasks, people, selectedDate, personFilter,
                    onPrevDay = { selectedDate = selectedDate.minusDays(1) },
                    onNextDay = { selectedDate = selectedDate.plusDays(1) },
                    onToday   = { selectedDate = LocalDate.now() },
                    onEdit    = { editTask = it })

                TaskViewMode.WEEK -> WeekTimeline(tasks, people, selectedDate,
                    onPrevWeek = { selectedDate = selectedDate.minusWeeks(1) },
                    onNextWeek = { selectedDate = selectedDate.plusWeeks(1) },
                    onToday    = { selectedDate = LocalDate.now() },
                    onEdit     = { editTask = it })
            }
        }
    }

    // ── Add / Edit dialog ──────────────────────────────
    if (showAddTask || editTask != null) {
        TaskEditDialog(
            task      = editTask,
            people    = people,
            onSave    = { task ->
                viewModel.saveTask(task)
                showAddTask = false
                editTask    = null
            },
            onDelete  = editTask?.let { t -> { viewModel.deleteTask(t) } },
            onDismiss = { showAddTask = false; editTask = null }
        )
    }
}

// ═════════════════════════════════════════════════════════════
// Time-of-day categories (§4)
// ═════════════════════════════════════════════════════════════
private enum class TimeCategory { MORNING, AFTERNOON, EVENING, ANYTIME }

private val TimeCategory.label: String get() = when (this) {
    TimeCategory.MORNING   -> "Morning"
    TimeCategory.AFTERNOON -> "Afternoon"
    TimeCategory.EVENING   -> "Evening"
    TimeCategory.ANYTIME   -> "Any Time"
}

/** Infer time-of-day from dueMs or startMs. */
private fun Task.timeCategory(): TimeCategory {
    val ms = dueMs ?: startMs ?: return TimeCategory.ANYTIME
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> TimeCategory.MORNING
        hour < 17 -> TimeCategory.AFTERNOON
        else      -> TimeCategory.EVENING
    }
}

// ═════════════════════════════════════════════════════════════
// View: LIST  — per-profile Task Box (§4)
// ═════════════════════════════════════════════════════════════
@Composable
private fun ListView(
    tasks: List<Task>,
    people: List<Person>,
    viewModel: TaskViewModel,
    showAddTask: Boolean,
    editTask: Task?,
    onAdd: () -> Unit,
    onEdit: (Task) -> Unit,
    onAddDismiss: () -> Unit
) {
    val active    = tasks.filter { !it.isCompleted }
    val completed = tasks.filter { it.isCompleted }

    if (tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircleOutline, null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text("No tasks", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAdd) { Text("Add your first task") }
            }
        }
    } else {
        LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // ── 1. Up for Grabs (unassigned active tasks) ─────
            val upForGrabs = active.filter { it.assignedPersonId == 0L }
            if (upForGrabs.isNotEmpty()) {
                item(key = "up_for_grabs_header") {
                    TaskBoxHeader(person = null, taskCount = upForGrabs.size)
                }
                items(upForGrabs, key = { "ug_${it.id}" }) { task ->
                    TaskBoxCard(
                        task       = task,
                        person     = null,
                        onToggle   = { viewModel.toggleComplete(task) },
                        onEdit     = { onEdit(task) },
                        onSnooze   = { viewModel.snoozeTask(task) },
                        onPostpone = { viewModel.postponeTask(task) },
                        onDelete   = { viewModel.deleteTask(task) }
                    )
                }
            }

            // ── 2. Per-profile Task Box sections ──────────────
            val profilePeople = people.filter { !it.isDefault }
            profilePeople.forEach { person ->
                val ptasks = active.filter { it.assignedPersonId == person.id }
                if (ptasks.isEmpty()) return@forEach

                item(key = "hdr_${person.id}") {
                    TaskBoxHeader(person = person, taskCount = ptasks.size)
                }

                // Morning
                val morning = ptasks.filter { it.timeCategory() == TimeCategory.MORNING }
                if (morning.isNotEmpty()) {
                    item(key = "am_${person.id}") { TimeCategoryLabel(TimeCategory.MORNING) }
                    items(morning, key = { "t_${it.id}" }) { task ->
                        TaskBoxCard(task, person,
                            onToggle   = { viewModel.toggleComplete(task) },
                            onEdit     = { onEdit(task) },
                            onSnooze   = { viewModel.snoozeTask(task) },
                            onPostpone = { viewModel.postponeTask(task) },
                            onDelete   = { viewModel.deleteTask(task) }
                        )
                    }
                }

                // Afternoon
                val afternoon = ptasks.filter { it.timeCategory() == TimeCategory.AFTERNOON }
                if (afternoon.isNotEmpty()) {
                    item(key = "pm_${person.id}") { TimeCategoryLabel(TimeCategory.AFTERNOON) }
                    items(afternoon, key = { "t_${it.id}" }) { task ->
                        TaskBoxCard(task, person,
                            onToggle   = { viewModel.toggleComplete(task) },
                            onEdit     = { onEdit(task) },
                            onSnooze   = { viewModel.snoozeTask(task) },
                            onPostpone = { viewModel.postponeTask(task) },
                            onDelete   = { viewModel.deleteTask(task) }
                        )
                    }
                }

                // Evening
                val evening = ptasks.filter { it.timeCategory() == TimeCategory.EVENING }
                if (evening.isNotEmpty()) {
                    item(key = "eve_${person.id}") { TimeCategoryLabel(TimeCategory.EVENING) }
                    items(evening, key = { "t_${it.id}" }) { task ->
                        TaskBoxCard(task, person,
                            onToggle   = { viewModel.toggleComplete(task) },
                            onEdit     = { onEdit(task) },
                            onSnooze   = { viewModel.snoozeTask(task) },
                            onPostpone = { viewModel.postponeTask(task) },
                            onDelete   = { viewModel.deleteTask(task) }
                        )
                    }
                }

                // Any time (no dueMs or startMs)
                val anytime = ptasks.filter { it.timeCategory() == TimeCategory.ANYTIME }
                if (anytime.isNotEmpty()) {
                    item(key = "any_${person.id}") { TimeCategoryLabel(TimeCategory.ANYTIME) }
                    items(anytime, key = { "t_${it.id}" }) { task ->
                        TaskBoxCard(task, person,
                            onToggle   = { viewModel.toggleComplete(task) },
                            onEdit     = { onEdit(task) },
                            onSnooze   = { viewModel.snoozeTask(task) },
                            onPostpone = { viewModel.postponeTask(task) },
                            onDelete   = { viewModel.deleteTask(task) }
                        )
                    }
                }
            }

            // ── 3. Completed section ──────────────────────────
            if (completed.isNotEmpty()) {
                item(key = "completed_header") {
                    SectionHeader("Completed  •  ${completed.size}")
                }
                items(completed, key = { "c_${it.id}" }) { task ->
                    val person = people.find { it.id == task.assignedPersonId }
                    TaskBoxCard(
                        task       = task,
                        person     = person,
                        onToggle   = { viewModel.toggleComplete(task) },
                        onEdit     = { onEdit(task) },
                        onSnooze   = { viewModel.snoozeTask(task) },
                        onPostpone = { viewModel.postponeTask(task) },
                        onDelete   = { viewModel.deleteTask(task) }
                    )
                }
            }
        }
    }
}

// ── Task Box section header ──────────────────────────────────
@Composable
private fun TaskBoxHeader(person: Person?, taskCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (person != null) {
            val color = runCatching {
                Color(android.graphics.Color.parseColor(person.colorHex))
            }.getOrElse { Color.Gray }
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(person.initial, color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text("Task Box", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text("•", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(person.name, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
        } else {
            Icon(Icons.Default.Group, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text("Up for Grabs", style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("$taskCount",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Time-of-day category label ───────────────────────────────
@Composable
private fun TimeCategoryLabel(category: TimeCategory) {
    Text(
        text  = category.label,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ── Task card for Task Box (with context menu) ───────────────
@Composable
private fun TaskBoxCard(
    task: Task,
    person: Person?,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onSnooze: () -> Unit,
    onPostpone: () -> Unit,
    onDelete: () -> Unit
) {
    val personColor = person?.let {
        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }
            .getOrElse { Color.Gray }
    }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color accent stripe
            if (personColor != null) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .background(personColor, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
            }

            Checkbox(
                checked  = task.isCompleted,
                onCheckedChange = { onToggle() }
            )
            Spacer(Modifier.width(4.dp))

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Due date
                    task.dueMs?.let { dueMs ->
                        val due = Instant.ofEpochMilli(dueMs)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        val today = LocalDate.now()
                        val isOverdue = due.isBefore(today) && !task.isCompleted
                        Text(
                            text  = due.format(DateTimeFormatter.ofPattern("MMM d")),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOverdue) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // High priority
                    if (task.priority == TaskPriority.HIGH) {
                        if (task.dueMs != null) Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.PriorityHigh, "High priority",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp))
                    }

                    // Stars
                    if (task.starsEarned > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text("⭐".repeat(task.starsEarned), fontSize = 11.sp)
                    }
                }
            }

            // Context menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Task options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = { showMenu = false; onEdit() },
                        leadingIcon = { Icon(Icons.Default.Edit, null, Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Snooze 1h") },
                        onClick = { showMenu = false; onSnooze() },
                        leadingIcon = { Icon(Icons.Default.Snooze, null, Modifier.size(18.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Postpone 1d") },
                        onClick = { showMenu = false; onPostpone() },
                        leadingIcon = { Icon(Icons.Default.Schedule, null, Modifier.size(18.dp)) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                        leadingIcon = {
                            Icon(Icons.Default.DeleteOutline, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                        }
                    )
                }
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// View: DAY  — profile-columns timeline
// Shows each person's tasks in their own column, positioned by
// scheduled start time. Hour labels on the left, horizontal
// scroll for person columns.
// ═════════════════════════════════════════════════════════════

private val HOUR_LABEL_WIDTH = 48.dp
private val PERSON_COL_WIDTH = 120.dp
private val HOUR_HEIGHT      = 56.dp
private val COL_HEADER_HEIGHT = 40.dp

private data class TimedTask(val task: Task, val hour: Int, val minute: Int, val timeMs: Long)

@Composable
private fun ProfileDayTimeline(
    tasks: List<Task>,
    people: List<Person>,
    selectedDate: LocalDate,
    personFilter: Long,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onEdit: (Task) -> Unit
) {
    // Determine which people to show as columns
    val activePeople = remember(tasks, selectedDate, personFilter, people) {
        if (personFilter > 0L) {
            // Show only the filtered person (they exist in the list)
            people.filter { it.id == personFilter }
        } else {
            // People with tasks on this day (by startMs or dueMs date)
            val personIds = tasks.filter { task ->
                val date = task.startMs?.let { ms ->
                    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                } ?: task.dueMs?.let { ms ->
                    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                } ?: return@filter false
                date == selectedDate && !task.isCompleted
            }.map { it.assignedPersonId }.distinct().toSet()
            people.filter { it.id in personIds || it.id == 0L }
        }
    }

    // Collect tasks for this day with their effective time
    val timedTasks = remember(tasks, selectedDate) {
        tasks.filter { !it.isCompleted }.mapNotNull { task ->
            val ms = task.startMs ?: task.dueMs ?: return@mapNotNull null
            val date = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
            if (date != selectedDate) return@mapNotNull null
            val time = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime()
            TimedTask(task, time.hour, time.minute, ms)
        }.sortedBy { it.timeMs }
    }

    val timedByHour = remember(timedTasks) {
        timedTasks.groupBy { it.hour }.toSortedMap()
    }

    // Unscheduled tasks (no startMs, no dueMs)
    val unscheduled = remember(tasks) { tasks.filter { it.dueMs == null && it.startMs == null && !it.isCompleted } }

    // Determine which hours to show
    val hours = remember(timedByHour) {
        if (timedByHour.isEmpty()) listOf(8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18) // default day hours
        else {
            val minH = (timedByHour.keys.min() - 1).coerceAtLeast(0)
            val maxH = (timedByHour.keys.max() + 1).coerceAtMost(23)
            (minH..maxH).toList()
        }
    }

    val sharedHScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(modifier = Modifier.fillMaxSize()) {
        DateNavigator(
            selectedDate = selectedDate,
            onPrev = onPrevDay,
            onNext = onNextDay,
            onToday = onToday
        )

        if (activePeople.isEmpty() && unscheduled.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tasks for this day",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // ── Profile columns timeline ──────────────
            Row(modifier = Modifier.fillMaxSize()) {
                // Fixed hour label column (scrolls vertically with body)
                Column(
                    modifier = Modifier
                        .width(HOUR_LABEL_WIDTH)
                        .verticalScroll(vScroll)
                ) {
                    Spacer(Modifier.height(COL_HEADER_HEIGHT))
                    hours.forEach { hour ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(HOUR_HEIGHT),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                text = if (hour == 0) "12a" else if (hour < 12) "${hour}a"
                                       else if (hour == 12) "12p" else "${hour - 12}p",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Thin divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                // Person columns + timeline body (shared horizontal scroll)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(vScroll)
                ) {
                    // ── Column headers ────────────────
                    if (activePeople.isNotEmpty()) {
                        Row(modifier = Modifier.horizontalScroll(sharedHScroll)) {
                            activePeople.forEach { person ->
                                PersonColumnHeader(
                                    person = person,
                                    modifier = Modifier.width(PERSON_COL_WIDTH).height(COL_HEADER_HEIGHT)
                                )
                            }
                        }
                        HorizontalDivider()
                    }

                    // ── Hour rows with person slots ───
                    Row(modifier = Modifier.horizontalScroll(sharedHScroll)) {
                        Column {
                            hours.forEach { hour ->
                                Row(modifier = Modifier.height(HOUR_HEIGHT)) {
                                    if (activePeople.isEmpty()) {
                                        // No active people — show all timed tasks in a single column
                                        Box(modifier = Modifier.width(PERSON_COL_WIDTH)) {
                                            timedByHour[hour]?.forEach { tt ->
                                                TimelineTaskChip(
                                                    task = tt.task,
                                                    people = people,
                                                    onClick = { onEdit(tt.task) },
                                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        activePeople.forEach { person ->
                                            PersonHourSlot(
                                                person = person,
                                                hour = hour,
                                                timedTasks = timedByHour[hour] ?: emptyList(),
                                                onClick = onEdit,
                                                modifier = Modifier.width(PERSON_COL_WIDTH)
                                            )
                                        }
                                    }
                                }
                            }

                            // ── Unscheduled tasks ──
                            if (unscheduled.isNotEmpty()) {
                                HorizontalDivider()
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Unscheduled  •  ${unscheduled.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                unscheduled.forEach { task ->
                                    val person = people.find { it.id == task.assignedPersonId }
                                    TimelineTaskChip(
                                        task = task,
                                        people = people,
                                        onClick = { onEdit(task) },
                                        modifier = Modifier
                                            .width(PERSON_COL_WIDTH * 2)
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
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

// ─────────────────────────────────────────────────────────────
// PersonColumnHeader — name + color dot at top of profile column
// ─────────────────────────────────────────────────────────────
@Composable
private fun PersonColumnHeader(
    person: Person,
    modifier: Modifier = Modifier
) {
    val color = remember(person.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
            .getOrElse { Color.Gray }
    }
    Row(
        modifier = modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────
// PersonHourSlot — one person's cell for one hour
// Shows tasks assigned to this person whose startMs falls in
// this hour, or whose dueMs falls in this hour as fallback.
// ─────────────────────────────────────────────────────────────
@Composable
private fun PersonHourSlot(
    person: Person,
    hour: Int,
    timedTasks: List<TimedTask>,
    onClick: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val personTasks = remember(person.id, timedTasks) {
        timedTasks.filter { it.task.assignedPersonId == person.id || it.task.assignedPersonId == 0L }
    }

    Box(modifier = modifier.padding(1.dp)) {
        // Subtle hour gridline
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 0.dp)
                .drawBehind {
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.15f),
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 0.5f
                    )
                }
        )

        if (personTasks.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth().padding(2.dp)) {
                personTasks.forEach { tt ->
                    TimelineTaskChip(
                        task = tt.task,
                        people = listOf(person),
                        onClick = { onClick(tt.task) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// TimelineTaskChip — compact task card for timeline slots
// ─────────────────────────────────────────────────────────────
@Composable
private fun TimelineTaskChip(
    task: Task,
    people: List<Person>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val person = people.find { it.id == task.assignedPersonId }
    val personColor = person?.let {
        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }
            .getOrElse { Color.Gray }
    }

    val timeStr = remember(task.startMs, task.dueMs) {
        val ms = task.startMs ?: task.dueMs
        ms?.let {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(DateTimeFormatter.ofPattern("h:mm"))
        } ?: ""
    }

    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (personColor != null)
                personColor.copy(alpha = 0.15f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp)) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (timeStr.isNotEmpty()) {
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 9.sp
                )
            }
            if (task.starsEarned > 0) {
                Text("⭐".repeat(task.starsEarned), fontSize = 8.sp)
            }
        }
    }
}

// ═════════════════════════════════════════════════════════════
// View: WEEK  — 7-day columns with person grouping
// ═════════════════════════════════════════════════════════════
@Composable
private fun WeekTimeline(
    tasks: List<Task>,
    people: List<Person>,
    selectedDate: LocalDate,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onToday: () -> Unit,
    onEdit: (Task) -> Unit
) {
    val weekStart = remember(selectedDate) {
        selectedDate.with(java.time.DayOfWeek.MONDAY)
    }

    val weekDays = remember(weekStart) {
        (0..6).map { weekStart.plusDays(it.toLong()) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DateNavigator(
            selectedDate = weekStart,
            onPrev = onPrevWeek,
            onNext = onNextWeek,
            onToday = onToday,
            isWeek = true
        )

        val hasAnyTasks = remember(tasks, weekDays) {
            tasks.any { task ->
                val date = task.startMs?.let { ms ->
                    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                } ?: task.dueMs?.let { ms ->
                    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                } ?: return@any false
                date in weekDays && !task.isCompleted
            }
        }

        if (!hasAnyTasks) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No tasks this week",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyRow(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
            ) {
                weekDays.forEach { day ->
                    item(key = "day_${day.toEpochDay()}") {
                        val dayTasks = remember(tasks, day) {
                            tasks.filter { task ->
                                val date = task.startMs?.let { ms ->
                                    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                                } ?: task.dueMs?.let { ms ->
                                    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                                } ?: return@filter false
                                date == day && !task.isCompleted
                            }
                        }
                        ProfileDayColumn(
                            day      = day,
                            tasks    = dayTasks,
                            people   = people,
                            onEdit   = onEdit,
                            isToday  = day == LocalDate.now()
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ProfileDayColumn — day column with person-grouped task chips
// ─────────────────────────────────────────────────────────────
@Composable
private fun ProfileDayColumn(
    day: LocalDate,
    tasks: List<Task>,
    people: List<Person>,
    onEdit: (Task) -> Unit,
    isToday: Boolean
) {
    val dayName = remember(day) { day.format(DateTimeFormatter.ofPattern("EEE")) }
    val dayNum  = day.dayOfMonth.toString()

    // Group tasks by person ID
    val tasksByPerson = remember(tasks, people) {
        tasks.groupBy { it.assignedPersonId }
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .fillMaxHeight()
            .padding(horizontal = 2.dp)
    ) {
        // Day header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isToday) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text  = dayName,
                style = MaterialTheme.typography.labelMedium,
                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text  = dayNum,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
            )
        }

        if (tasks.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "—",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Sort people — show "Everyone" tasks first, then by person
                val sortedPersonIds = tasksByPerson.keys.sortedBy { id ->
                    if (id == 0L) -1 else id
                }
                sortedPersonIds.forEach { personId ->
                    val personTasks = tasksByPerson[personId] ?: return@forEach
                    val person = people.find { it.id == personId }

                    // Person section header
                    val personColor = run {
                        if (person != null) {
                            runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
                                .getOrElse { Color.Gray }
                        } else Color.Gray
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(personColor)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = person?.name ?: "Everyone",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Tasks for this person
                    personTasks.forEach { task ->
                        WeekTaskChip(
                            task     = task,
                            person   = person,
                            onClick  = { onEdit(task) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// WeekTaskChip — compact task card for week columns
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekTaskChip(
    task: Task,
    person: Person?,
    onClick: () -> Unit
) {
    val personColor = person?.let {
        runCatching { Color(android.graphics.Color.parseColor(it.colorHex)) }
            .getOrElse { Color.Gray }
    }

    val timeStr = remember(task.startMs, task.dueMs) {
        val ms = task.startMs ?: task.dueMs
        ms?.let {
            Instant.ofEpochMilli(it)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
                .format(DateTimeFormatter.ofPattern("h:mm"))
        } ?: ""
    }

    Card(
        onClick   = onClick,
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (personColor != null)
                personColor.copy(alpha = 0.1f)
            else
                MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Text(
                text       = task.title,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis
            )
            if (timeStr.isNotEmpty()) {
                Text(
                    text  = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (task.starsEarned > 0) {
                Text("⭐".repeat(task.starsEarned), fontSize = 9.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// DateNavigator — prev/next/today bar
// ─────────────────────────────────────────────────────────────
@Composable
private fun DateNavigator(
    selectedDate: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    isWeek: Boolean = false
) {
    val dateText = remember(selectedDate) {
        if (isWeek) {
            val end = selectedDate.plusDays(6)
            "${selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))} – ${
                end.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}"
        } else {
            selectedDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy"))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Default.ChevronLeft, "Previous")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = dateText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            if (!isWeek) {
                TextButton(
                    onClick = onToday,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Today", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, "Next")
        }
    }
    HorizontalDivider()
}

// ═════════════════════════════════════════════════════════════
// TaskEditDialog — create / edit a task or chore
// ═════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditDialog(
    task: Task?,
    people: List<Person>,
    onSave: (Task) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    /** When true, the dialog is being used by ChoresScreen to add a chore.
     *  Caller (ChoresScreen) sets isChore=true + isLocalOnly=true on the
     *  Task it produces from onSave; the dialog itself doesn't need to
     *  differ much, but keeping the param lets the UI add chore-specific
     *  affordances later (e.g. hide priority, surface a chore-emoji
     *  picker) without changing the call sites. */
    isChoreMode: Boolean = false
) {
    var title         by remember { mutableStateOf(task?.title ?: "") }
    var description   by remember { mutableStateOf(task?.description ?: "") }
    var priority      by remember { mutableStateOf(task?.priority ?: TaskPriority.NORMAL) }
    var assignedId    by remember { mutableStateOf(task?.assignedPersonId ?: 0L) }
    var starsValue    by remember { mutableStateOf(task?.starsEarned ?: 0) }
    var dueDateMs     by remember { mutableStateOf(task?.dueMs ?: 0L) }
    var hasDueDate    by remember { mutableStateOf(task?.dueMs != null) }
    var hasSchedule   by remember { mutableStateOf(task?.startMs != null || task?.endMs != null) }
    var scheduleStartMs by remember { mutableStateOf(task?.startMs ?:
        (task?.dueMs ?: System.currentTimeMillis())) }
    var scheduleEndMs   by remember { mutableStateOf(task?.endMs ?:
        (task?.dueMs?.let { it + 3_600_000L } ?: System.currentTimeMillis() + 3_600_000L)) }
    var titleError    by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showScheduleStartPicker by remember { mutableStateOf(false) }
    var showScheduleEndPicker   by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Text(
                    text = when {
                        isChoreMode && task == null -> "New Chore"
                        isChoreMode                 -> "Edit Chore"
                        task == null                -> "New Task"
                        else                        -> "Edit Task"
                    },
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = {
                    if (title.isBlank()) { titleError = true; return@TextButton }
                    onSave(
                        (task ?: Task(title = "", assignedPersonId = 0L)).copy(
                            title       = title.trim(),
                            description = description.trim(),
                            priority    = priority,
                            assignedPersonId = assignedId,
                            starsEarned = starsValue,
                            dueMs       = if (hasDueDate) dueDateMs else null,
                            startMs     = if (hasSchedule) scheduleStartMs else null,
                            endMs       = if (hasSchedule) scheduleEndMs else null
                        )
                    )
                }) { Text("Save") }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value         = title,
                onValueChange = { title = it; titleError = false },
                label         = { Text(if (isChoreMode) "Chore name" else "Task title") },
                isError       = titleError,
                modifier      = Modifier.fillMaxWidth(),
                leadingIcon   = { Icon(Icons.Default.CheckBox, null) },
                singleLine    = true
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value         = description,
                onValueChange = { description = it },
                label         = { Text("Notes (optional)") },
                modifier      = Modifier.fillMaxWidth().height(80.dp),
                leadingIcon   = { Icon(Icons.AutoMirrored.Filled.Notes, null) }
            )

            Spacer(Modifier.height(12.dp))

            // ── Due date ─────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { hasDueDate = !hasDueDate }) {
                    Checkbox(
                        checked  = hasDueDate,
                        onCheckedChange = { hasDueDate = it }
                    )
                    Text("Due date", style = MaterialTheme.typography.labelMedium)
                }
                if (hasDueDate) {
                    Spacer(Modifier.weight(1f))
                    val dueDate = remember(dueDateMs) {
                        if (dueDateMs > 0) {
                            Instant.ofEpochMilli(dueDateMs)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                        } else LocalDate.now()
                    }
                    OutlinedButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            dueDate.format(DateTimeFormatter.ofPattern("MMM d, yyyy")),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            // ── Time picker (if due date is set) ─────
            if (hasDueDate) {
                val currentTime = remember(dueDateMs) {
                    if (dueDateMs > 0) {
                        Instant.ofEpochMilli(dueDateMs)
                            .atZone(ZoneId.systemDefault()).toLocalTime()
                    } else LocalTime.NOON
                }
                var hourStr   by remember { mutableStateOf(
                    String.format("%02d", currentTime.hour)) }
                var minuteStr by remember { mutableStateOf(
                    String.format("%02d", currentTime.minute)) }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Time:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    OutlinedTextField(
                        value         = hourStr,
                        onValueChange = { hourStr = it.filter(Char::isDigit).take(2) },
                        modifier      = Modifier.width(56.dp),
                        placeholder   = { Text("HH") },
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodySmall
                    )
                    Text(":", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value         = minuteStr,
                        onValueChange = { minuteStr = it.filter(Char::isDigit).take(2) },
                        modifier      = Modifier.width(56.dp),
                        placeholder   = { Text("MM") },
                        singleLine    = true,
                        textStyle     = MaterialTheme.typography.bodySmall
                    )
                }
                // Hidden: update dueDateMs when time changes
                LaunchedEffect(hourStr, minuteStr, dueDateMs) {
                    val h = hourStr.toIntOrNull() ?: return@LaunchedEffect
                    val m = minuteStr.toIntOrNull() ?: return@LaunchedEffect
                    if (h in 0..23 && m in 0..59) {
                        val baseDate = if (dueDateMs > 0) {
                            Instant.ofEpochMilli(dueDateMs)
                                .atZone(ZoneId.systemDefault()).toLocalDate()
                        } else LocalDate.now()
                        val newDateTime = LocalDateTime.of(baseDate, LocalTime.of(h, m))
                        dueDateMs = newDateTime.atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    }
                }
            }

            // ── Simple DatePickerDialog ──────────────
            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = if (dueDateMs > 0) dueDateMs else
                        System.currentTimeMillis()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { ms ->
                                // Preserve time, update date
                                val oldTime = if (dueDateMs > 0) {
                                    Instant.ofEpochMilli(dueDateMs)
                                        .atZone(ZoneId.systemDefault()).toLocalTime()
                                } else LocalTime.NOON
                                val newDate = Instant.ofEpochMilli(ms)
                                    .atZone(ZoneId.systemDefault()).toLocalDate()
                                val newDateTime = LocalDateTime.of(newDate, oldTime)
                                dueDateMs = newDateTime
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // ── Schedule (start/end time block) ────────
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { hasSchedule = !hasSchedule }) {
                    Checkbox(
                        checked  = hasSchedule,
                        onCheckedChange = { hasSchedule = it }
                    )
                    Text("Schedule", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (hasSchedule) {
                Spacer(Modifier.height(8.dp))

                // Start time
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Start:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp))
                    OutlinedButton(onClick = { showScheduleStartPicker = true }) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            remember(scheduleStartMs) {
                                Instant.ofEpochMilli(scheduleStartMs)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                                    .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // End time
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("End:", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp))
                    OutlinedButton(onClick = { showScheduleEndPicker = true }) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            remember(scheduleEndMs) {
                                Instant.ofEpochMilli(scheduleEndMs)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                                    .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                // Duration preview
                val durationMinutes = remember(scheduleStartMs, scheduleEndMs) {
                    ((scheduleEndMs - scheduleStartMs) / 60_000L).coerceAtLeast(0)
                }
                if (durationMinutes > 0) {
                    Spacer(Modifier.height(4.dp))
                    val durText = when {
                        durationMinutes >= 60 -> "${durationMinutes / 60}h ${durationMinutes % 60}m"
                        else                  -> "${durationMinutes}m"
                    }
                    Text(
                        text  = "Duration: $durText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }
            }

            // ── Schedule start DatePickerDialog ─────────
            if (showScheduleStartPicker) {
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = scheduleStartMs)
                DatePickerDialog(
                    onDismissRequest = { showScheduleStartPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { ms ->
                                // Preserve time from current startMs, update date
                                val oldTime = Instant.ofEpochMilli(scheduleStartMs)
                                    .atZone(ZoneId.systemDefault()).toLocalTime()
                                val newDate = Instant.ofEpochMilli(ms)
                                    .atZone(ZoneId.systemDefault()).toLocalDate()
                                scheduleStartMs = LocalDateTime.of(newDate, oldTime)
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                // Keep end >= start
                                if (scheduleEndMs < scheduleStartMs) {
                                    scheduleEndMs = scheduleStartMs + 3_600_000L
                                }
                            }
                            showScheduleStartPicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showScheduleStartPicker = false }) { Text("Cancel") }
                    }
                ) { DatePicker(state = pickerState) }
            }

            // ── Schedule end DatePickerDialog ───────────
            if (showScheduleEndPicker) {
                val pickerState = rememberDatePickerState(
                    initialSelectedDateMillis = scheduleEndMs)
                DatePickerDialog(
                    onDismissRequest = { showScheduleEndPicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            pickerState.selectedDateMillis?.let { ms ->
                                val oldTime = Instant.ofEpochMilli(scheduleEndMs)
                                    .atZone(ZoneId.systemDefault()).toLocalTime()
                                val newDate = Instant.ofEpochMilli(ms)
                                    .atZone(ZoneId.systemDefault()).toLocalDate()
                                scheduleEndMs = LocalDateTime.of(newDate, oldTime)
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                // Clamp: end >= start
                                if (scheduleEndMs < scheduleStartMs) {
                                    scheduleEndMs = scheduleStartMs + 3_600_000L
                                }
                            }
                            showScheduleEndPicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showScheduleEndPicker = false }) { Text("Cancel") }
                    }
                ) { DatePicker(state = pickerState) }
            }

            Spacer(Modifier.height(12.dp))

            // Priority
            Text("Priority", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.values().forEach { p ->
                    FilterChip(
                        selected = priority == p,
                        onClick  = { priority = p },
                        label    = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = if (p == TaskPriority.HIGH) {
                            { Icon(Icons.Default.PriorityHigh, null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            // Assign to person
            if (people.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Assign to", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = assignedId == 0L, onClick = { assignedId = 0L },
                        label = { Text("Everyone") })
                    people.filter { !it.isDefault }.forEach { person ->
                        PersonChip(
                            person   = person,
                            selected = assignedId == person.id,
                            onClick  = { assignedId = person.id }
                        )
                    }
                }
            }

            // Stars (chore reward value)
            Spacer(Modifier.height(12.dp))
            Text("⭐ Stars earned on completion", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0..5) {
                    IconButton(onClick = { starsValue = if (starsValue == i) 0 else i },
                        modifier = Modifier.size(36.dp)) {
                        Text(
                            if (i <= starsValue) "⭐" else "☆",
                            fontSize = 20.sp
                        )
                    }
                }
            }

            if (onDelete != null) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick  = { onDelete(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.DeleteOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Task")
                }
            }
        }
    }
}
