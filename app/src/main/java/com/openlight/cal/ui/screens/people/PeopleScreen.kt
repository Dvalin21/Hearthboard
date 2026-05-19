package com.openlight.cal.ui.screens.people

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openlight.cal.data.contacts.BirthdayImporter
import com.openlight.cal.data.contacts.ContactBirthday
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.CalendarEvent
import com.openlight.cal.data.model.Person
import com.openlight.cal.data.model.PersonRole
import com.openlight.cal.ui.components.ColorPickerGrid
import com.openlight.cal.ui.viewmodel.PersonViewModel
import kotlinx.coroutines.launch
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleScreen(
    viewModel: PersonViewModel,
    database: AppDatabase? = null,
    modifier: Modifier = Modifier
) {
    val people by viewModel.people.collectAsState()
    var showAdd   by remember { mutableStateOf(false) }
    var editPerson by remember { mutableStateOf<Person?>(null) }
    var showBirthdayImport by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

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

                // Birthday import button
                item {
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { showBirthdayImport = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Cake, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import Birthdays from Contacts")
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showAdd || editPerson != null) {
        PersonEditDialog(
            person    = editPerson,
            people    = people,
            onSave    = { p ->
                if (editPerson != null) viewModel.updatePerson(p)
                else viewModel.savePerson(p)
                showAdd    = false
                editPerson = null
            },
            onDismiss = { showAdd = false; editPerson = null }
        )
    }

    // ── Birthday import dialog ─────────────────────────────────
    if (showBirthdayImport && database != null) {
        BirthdayImportDialog(
            database   = database,
            onDismiss  = { showBirthdayImport = false },
            onImported = { count ->
                showBirthdayImport = false
                scope.launch {
                    kotlinx.coroutines.delay(100)
                }
            }
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
                Text("Default — shared events",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(person.role.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    people: List<Person> = emptyList(),
    onSave: (Person) -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = person == null
    var name      by remember { mutableStateOf(person?.name ?: "") }
    var email     by remember { mutableStateOf(person?.email ?: "") }
    var colorHex  by remember { mutableStateOf(person?.colorHex ?: "#2196F3") }
    var role      by remember { mutableStateOf(person?.role ?: PersonRole.PARENT) }
    var caregiverPersonId by remember { mutableStateOf(person?.caregiverPersonId ?: 0L) }
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
                            email    = email.trim(),
                            colorHex = colorHex,
                            initial  = name.take(1).uppercase(),
                            role    = role,
                            caregiverPersonId = caregiverPersonId
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

            Spacer(Modifier.height(16.dp))

            // Email field – for matching ORGANIZER from CalDAV
            OutlinedTextField(
                value         = email,
                onValueChange = { email = it },
                label         = { Text("Email") },
                placeholder   = { Text("alice@example.com") },
                modifier      = Modifier.fillMaxWidth(),
                leadingIcon   = { Icon(Icons.Default.Email, null) },
                singleLine    = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Email
                )
            )

            Spacer(Modifier.height(16.dp))

            // Role selector
            Text("Role", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                PersonRole.values().forEach { r ->
                    SegmentedButton(
                        selected = role == r,
                        onClick  = { role = r; if (r == PersonRole.PARENT) caregiverPersonId = 0L },
                        shape    = SegmentedButtonDefaults.itemShape(
                            index = PersonRole.values().indexOf(r),
                            count = PersonRole.values().size
                        )
                    ) { Text(r.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall) }
                }
            }

            // Caregiver selector (for CHILD/DEPENDENT)
            if (role != PersonRole.PARENT) {
                Spacer(Modifier.height(12.dp))
                Text("Managed by", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                val parents = people.filter { it.role == PersonRole.PARENT && it.id != person?.id }
                if (parents.isEmpty()) {
                    Text("Add a parent first", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        parents.forEach { parent ->
                            FilterChip(
                                selected = caregiverPersonId == parent.id,
                                onClick  = { caregiverPersonId = parent.id },
                                label    = { Text(parent.name) },
                                leadingIcon = if (caregiverPersonId == parent.id)
                                    { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null
                            )
                        }
                    }
                }
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

// ─────────────────────────────────────────────────────────────
// Birthday Import Dialog
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BirthdayImportDialog(
    database: AppDatabase,
    onDismiss: () -> Unit,
    onImported: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf<List<ContactBirthday>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var permissionGranted by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) {
            contacts = BirthdayImporter.queryBirthdays(context.contentResolver)
            selected = contacts.map { it.contactId }.toSet()
        }
        loading = false
    }

    LaunchedEffect(Unit) {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED -> {
                permissionGranted = true
                contacts = BirthdayImporter.queryBirthdays(context.contentResolver)
                selected = contacts.map { it.contactId }.toSet()
                loading = false
            }
            else -> permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import Birthdays") },
        text = {
            when {
                loading -> {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                !permissionGranted -> {
                    Text("Contact permission is required to read birthdays.")
                }
                imported -> {
                    Text("Birthdays imported successfully!")
                }
                contacts.isEmpty() -> {
                    Text("No contacts with birthdays found.")
                }
                else -> {
                    Column {
                        Text("Select contacts to import:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(contacts) { contact ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selected = if (contact.contactId in selected)
                                                selected - contact.contactId
                                            else selected + contact.contactId
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Checkbox(
                                        checked = contact.contactId in selected,
                                        onCheckedChange = {
                                            selected = if (contact.contactId in selected)
                                                selected - contact.contactId
                                            else selected + contact.contactId
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(contact.displayName, style = MaterialTheme.typography.bodyMedium)
                                        Text(contact.dateLabel, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                loading -> {}
                !permissionGranted -> {
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.READ_CONTACTS) }) {
                        Text("Grant Permission")
                    }
                }
                imported -> TextButton(onClick = onDismiss) { Text("Done") }
                contacts.isEmpty() -> TextButton(onClick = onDismiss) { Text("Close") }
                else -> {
                    TextButton(onClick = {
                        scope.launch {
                            val dao = database.calendarEventDao()
                            var count = 0
                            for (sid in selected) {
                                val contact = contacts.find { it.contactId == sid } ?: continue
                                val next = contact.nextDate()
                                val startMs = next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                dao.insert(
                                    CalendarEvent(
                                        uid        = "bday_${contact.contactId}",
                                        title      = "${contact.displayName}'s Birthday",
                                        startMs    = startMs,
                                        endMs      = startMs + 86_400_000L, // 24h
                                        isAllDay   = true,
                                        isLocalOnly = true,
                                        recurrenceRule = "FREQ=YEARLY",
                                        colorHex   = "#E91E63"
                                    )
                                )
                                count++
                            }
                            imported = true
                            onImported(count)
                        }
                    }) { Text("Import (${selected.size})") }
                }
            }
        },
        dismissButton = {
            if (!imported && !loading && permissionGranted && contacts.isNotEmpty()) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}
