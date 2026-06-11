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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.openlight.cal.data.contacts.BirthdayImporter
import com.openlight.cal.data.contacts.ContactBirthday
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.CalendarEvent
import com.openlight.cal.data.model.Label
import com.openlight.cal.data.model.Person
import com.openlight.cal.data.model.PersonRole
import com.openlight.cal.ui.components.ColorPickerGrid
import com.openlight.cal.ui.theme.PersonColors
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
    val labels by viewModel.labels.collectAsState()
    var showAdd   by remember { mutableStateOf(false) }
    var editPerson by remember { mutableStateOf<Person?>(null) }
    var profilePerson by remember { mutableStateOf<Person?>(null) }
    var showBirthdayImport by remember { mutableStateOf(false) }
    var showLabelsDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("People") },
                actions = {
                    IconButton(onClick = { showLabelsDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Label, "Labels")
                    }
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
                        person    = person,
                        viewModel = viewModel,
                        onClick   = { profilePerson = person },
                        onEdit    = { editPerson = person },
                        onDelete  = if (!person.isDefault) {
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
                        Icon(Icons.Default.Cake, "Birthday", modifier = Modifier.size(18.dp))
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
            labels    = labels,
            viewModel = viewModel,
            onSave    = { p ->
                if (editPerson != null) viewModel.updatePerson(p)
                else viewModel.savePerson(p)
                showAdd    = false
                editPerson = null
            },
            onDismiss = { showAdd = false; editPerson = null }
        )
    }

    // ── Label management dialog ──────────────────────────────
    if (showLabelsDialog) {
        LabelManagerDialog(
            labels    = labels,
            onSave    = viewModel::saveLabel,
            onDelete  = viewModel::deleteLabel,
            onDismiss = { showLabelsDialog = false }
        )
    }

    // ── Profile sheet (tap person row) ─────────────────────────
    if (profilePerson != null) {
        PersonProfileSheet(
            personId  = profilePerson!!.id,
            viewModel = viewModel,
            onEdit    = { editPerson = profilePerson },
            onDismiss = { profilePerson = null }
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
    viewModel: PersonViewModel,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(person.colorHex))
    }.getOrElse { Color.Gray }

    // Live label list for this person
    val personLabels by viewModel.getLabelsForPersonFlow(person.id)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
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

            // Label chips
            if (personLabels.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    personLabels.take(4).forEach { label ->
                        val chipColor = runCatching {
                            Color(android.graphics.Color.parseColor(label.colorHex))
                        }.getOrElse { Color.Gray }
                        Surface(
                            color = chipColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text     = label.name,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style    = MaterialTheme.typography.labelSmall,
                                color    = chipColor
                            )
                        }
                    }
                    if (personLabels.size > 4) {
                        Text("+${personLabels.size - 4}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
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
    labels: List<Label> = emptyList(),
    viewModel: PersonViewModel? = null,
    onSave: (Person) -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = person == null
    var name      by remember { mutableStateOf(person?.name ?: "") }
    var email     by remember { mutableStateOf(person?.email ?: "") }
    var colorHex  by remember { mutableStateOf(person?.colorHex ?: PersonColors.first()) }
    var role      by remember { mutableStateOf(person?.role ?: PersonRole.PARENT) }
    var caregiverPersonId by remember { mutableStateOf(person?.caregiverPersonId ?: 0L) }
    var nameError by remember { mutableStateOf(false) }

    // Current label assignments for this person (collected from viewModel)
    var assignedLabels by remember { mutableStateOf(emptyList<Label>()) }
    LaunchedEffect(person?.id) {
        if (person != null && viewModel != null) {
            viewModel.getLabelsForPersonFlow(person.id).collect { assignedLabels = it }
        }
    }

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

            // ── Label assignment (only when editing an existing person) ──
            if (person != null && labels.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Labels", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val currentLabels = assignedLabels
                        labels.forEach { label ->
                            val isAssigned = currentLabels.any { it.id == label.id }
                            val chipColor = runCatching {
                                Color(android.graphics.Color.parseColor(label.colorHex))
                            }.getOrElse { Color.Gray }

                            FilterChip(
                                selected = isAssigned,
                                onClick = {
                                    if (isAssigned) {
                                        viewModel?.unassignLabel(person.id, label.id)
                                    } else {
                                        viewModel?.assignLabel(person.id, label.id)
                                    }
                                },
                                label = { Text(label.name) },
                                leadingIcon = if (isAssigned)
                                    { { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) } } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = chipColor.copy(alpha = 0.2f),
                                    selectedLabelColor = chipColor
                                )
                            )
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
// Person Profile Sheet (§12)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonProfileSheet(
    personId: Long,
    viewModel: PersonViewModel,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    // Live person data (reflects edits)
    val people by viewModel.people.collectAsStateWithLifecycle()
    val person = people.firstOrNull { it.id == personId } ?: return

    // Labels for this person
    val labels by viewModel.getLabelsForPersonFlow(personId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Stats
    val openTasksCount by viewModel.getOpenTasksCountFlow(personId)
        .collectAsStateWithLifecycle(initialValue = 0)
    val balance by viewModel.getBalanceFlow(personId)
        .collectAsStateWithLifecycle(initialValue = 0)

    val color = runCatching {
        Color(android.graphics.Color.parseColor(person.colorHex))
    }.getOrElse { Color.Gray }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Close") }
                Text("Profile", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
            }

            Spacer(Modifier.height(8.dp))

            // Large avatar
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = person.initial,
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(16.dp))

            // Name
            Text(person.name, style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(4.dp))

            // Role badge
            Surface(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = person.role.name.lowercase().replaceFirstChar { it.uppercase() },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = color
                )
            }

            // Email
            if (person.email.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(person.email, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Caregiver
            if (person.caregiverPersonId > 0L) {
                Spacer(Modifier.height(4.dp))
                val caregiverName = people.firstOrNull { it.id == person.caregiverPersonId }?.name
                if (caregiverName != null) {
                    Text("Managed by $caregiverName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Labels
            if (labels.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    labels.forEach { label ->
                        val lc = runCatching {
                            Color(android.graphics.Color.parseColor(label.colorHex))
                        }.getOrElse { Color.Gray }
                        Surface(
                            color = lc.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(label.name,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = lc)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = openTasksCount.toString(),
                    label = "Open Tasks",
                    icon = Icons.Default.CheckCircle
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    value = balance.toString(),
                    label = "Star Balance",
                    icon = Icons.Default.Star,
                    valueColor = if (balance >= 0) Color(0xFFFFC107) else MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(24.dp))

            // Edit action
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Edit Person")
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    icon: ImageVector,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, color = valueColor)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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

// ─────────────────────────────────────────────────────────────
// Label Management Dialog (§11)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelManagerDialog(
    labels: List<Label>,
    onSave: (Label) -> Unit,
    onDelete: (Label) -> Unit,
    onDismiss: () -> Unit
) {
    var editingLabel by remember { mutableStateOf<Label?>(null) }
    var isCreating by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Labels") },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                // "Add label" button
                OutlinedButton(
                    onClick = {
                        editingLabel = Label(name = "", colorHex = PersonColors.first())
                        isCreating = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add label")
                }

                Spacer(Modifier.height(12.dp))

                if (labels.isEmpty()) {
                    Text("No labels yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                } else {
                    LazyColumn {
                        items(labels) { label ->
                            val chipColor = runCatching {
                                Color(android.graphics.Color.parseColor(label.colorHex))
                            }.getOrElse { Color.Gray }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Color + name chip
                                Surface(
                                    color = chipColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .background(chipColor, CircleShape)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(label.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = chipColor)
                                    }
                                }

                                IconButton(onClick = {
                                    editingLabel = label
                                    isCreating = false
                                }) {
                                    Icon(Icons.Default.Edit, "Edit",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp))
                                }

                                IconButton(onClick = { onDelete(label) }) {
                                    Icon(Icons.Default.Delete, "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )

    // Create/Edit label sub-dialog
    if (editingLabel != null) {
        var name by remember(editingLabel) {
            mutableStateOf(editingLabel!!.name)
        }
        val previewColor = runCatching {
            Color(android.graphics.Color.parseColor(editingLabel!!.colorHex))
        }.getOrElse { Color(0xFF4CAF50) }

        AlertDialog(
            onDismissRequest = { editingLabel = null },
            title = { Text(if (isCreating) "New Label" else "Edit Label") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Label name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(8.dp))
                    ColorPickerGrid(
                        selectedHex = editingLabel!!.colorHex,
                        onSelect = { editingLabel = editingLabel!!.copy(colorHex = it) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        onSave(editingLabel!!.copy(name = name.trim()))
                        editingLabel = null
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingLabel = null }) { Text("Cancel") }
            }
        )
    }
}
