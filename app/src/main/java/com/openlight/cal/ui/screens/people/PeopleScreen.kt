package com.openlight.cal.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.model.Person
import com.openlight.cal.ui.components.ColorPickerGrid
import com.openlight.cal.ui.viewmodel.PersonViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    viewModel: PersonViewModel,
    modifier: Modifier = Modifier
) {
    val people by viewModel.people.collectAsState()
    var showAdd   by remember { mutableStateOf(false) }
    var editPerson by remember { mutableStateOf<Person?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("People") },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Default.PersonAdd, "Add person")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.PersonAdd, "Add person")
            }
        }
    ) { padding ->
        if (people.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Group, null, modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("No family members added", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showAdd = true }) { Text("Add a person") }
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(people, key = { it.id }) { person ->
                    PersonRow(
                        person   = person,
                        onEdit   = { editPerson = person },
                        onDelete = if (!person.isDefault) {
                            { viewModel.deletePerson(person) }
                        } else null
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showAdd || editPerson != null) {
        PersonEditDialog(
            person    = editPerson,
            onSave    = { p ->
                if (editPerson != null) viewModel.updatePerson(p)
                else viewModel.savePerson(p)
                showAdd    = false
                editPerson = null
            },
            onDismiss = { showAdd = false; editPerson = null }
        )
    }
}

@Composable
private fun PersonRow(
    person: Person,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(person.colorHex))
    }.getOrElse { Color.Gray }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = person.initial,
                color      = Color.White,
                fontSize   = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = person.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (person.isDefault) {
                Text(
                    "Default — shared events",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Color swatch
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(color, CircleShape)
        )

        Spacer(Modifier.width(8.dp))

        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonEditDialog(
    person: Person?,
    onSave: (Person) -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = person == null
    var name      by remember { mutableStateOf(person?.name ?: "") }
    var colorHex  by remember { mutableStateOf(person?.colorHex ?: "#2196F3") }
    var nameError by remember { mutableStateOf(false) }

    val previewColor = runCatching {
        Color(android.graphics.Color.parseColor(colorHex))
    }.getOrElse { Color(0xFF2196F3) }
    val initial = name.take(1).uppercase().ifBlank { "?" }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Text(
                    if (isNew) "Add Person" else "Edit Person",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = {
                    if (name.isBlank()) { nameError = true; return@TextButton }
                    onSave(
                        (person ?: Person(name = "", colorHex = colorHex)).copy(
                            name     = name.trim(),
                            colorHex = colorHex,
                            initial  = name.take(1).uppercase()
                        )
                    )
                }) { Text("Save") }
            }

            Spacer(Modifier.height(24.dp))

            // Preview avatar
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(previewColor, CircleShape)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = initial,
                    color      = Color.White,
                    fontSize   = 36.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(24.dp))

            // Name field
            OutlinedTextField(
                value         = name,
                onValueChange = { name = it; nameError = false },
                label         = { Text("Name") },
                isError       = nameError,
                modifier      = Modifier.fillMaxWidth(),
                leadingIcon   = { Icon(Icons.Default.Person, null) },
                singleLine    = true
            )
            if (nameError) {
                Text("Name is required", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp))
            }

            Spacer(Modifier.height(20.dp))

            Text(
                "Choose a color",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            ColorPickerGrid(
                selectedHex = colorHex,
                onSelect    = { colorHex = it }
            )
        }
    }
}
