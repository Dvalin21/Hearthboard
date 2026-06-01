@file:Suppress("DEPRECATION")

package com.openlight.cal.ui.screens.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.model.*
import com.openlight.cal.ui.components.*
import com.openlight.cal.ui.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TaskViewModel,
    modifier: Modifier = Modifier
) {
    val tasks     by viewModel.filteredTasks.collectAsState()
    val people    by viewModel.people.collectAsState()
    val personFilter by viewModel.selectedPersonFilter.collectAsState()

    var showAddTask by remember { mutableStateOf(false) }
    var editTask    by remember { mutableStateOf<Task?>(null) }

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

            // Person filter
            if (people.size > 1) {
                PersonFilterRow(
                    people     = people,
                    selectedId = personFilter,
                    onSelect   = viewModel::setPersonFilter,
                    modifier   = Modifier.padding(vertical = 8.dp)
                )
                HorizontalDivider()
            }

            // Chore chart: total stars per person
            val starsByPerson = people.filter { !it.isDefault }.associate { person ->
                person to tasks.filter { it.assignedPersonId == person.id && it.isCompleted }
                    .sumOf { it.starsEarned }
            }
            if (starsByPerson.any { it.value > 0 }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    starsByPerson.forEach { (person, totalStars) ->
                        if (totalStars > 0) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                PersonChip(person = person, selected = false)
                                Spacer(Modifier.height(2.dp))
                                Text("$totalStars ⭐", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                HorizontalDivider()
            }

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
                        TextButton(onClick = { showAddTask = true }) {
                            Text("Add your first task")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier       = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (active.isNotEmpty()) {
                        item { SectionHeader("Active  •  ${active.size}") }
                        items(active, key = { it.id }) { task ->
                            val person = people.find { it.id == task.assignedPersonId }
                            TaskItem(
                                task     = task,
                                person   = person,
                                onToggle = { viewModel.toggleComplete(task) },
                                onDelete = { viewModel.deleteTask(task) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }

                    if (completed.isNotEmpty()) {
                        item { SectionHeader("Completed  •  ${completed.size}") }
                        items(completed, key = { it.id }) { task ->
                            val person = people.find { it.id == task.assignedPersonId }
                            TaskItem(
                                task     = task,
                                person   = person,
                                onToggle = { viewModel.toggleComplete(task) },
                                onDelete = { viewModel.deleteTask(task) }
                            )
                        }
                    }
                }
            }
        }
    }

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
    var title       by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    var priority    by remember { mutableStateOf(task?.priority ?: TaskPriority.NORMAL) }
    var assignedId  by remember { mutableStateOf(task?.assignedPersonId ?: 0L) }
    var starsValue  by remember { mutableStateOf(task?.starsEarned ?: 0) }
    var titleError  by remember { mutableStateOf(false) }

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
                Text(if (task == null) "New Task" else "Edit Task",
                    style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    if (title.isBlank()) { titleError = true; return@TextButton }
                    onSave(
                        (task ?: Task(title = "", assignedPersonId = 0L)).copy(
                            title       = title.trim(),
                            description = description.trim(),
                            priority    = priority,
                            assignedPersonId = assignedId,
                            starsEarned = starsValue
                        )
                    )
                }) { Text("Save") }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value         = title,
                onValueChange = { title = it; titleError = false },
                label         = { Text("Task title") },
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
                leadingIcon = { Icon(Icons.Default.Notes, null) }
            )

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
