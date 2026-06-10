package com.openlight.cal.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.model.AccountType
import com.openlight.cal.data.model.CalendarAccount
import com.openlight.cal.data.sync.CalDAVSyncWorker
import com.openlight.cal.ui.theme.PersonColors
import com.openlight.cal.data.model.Person
import com.openlight.cal.data.model.PersonRole
import com.openlight.cal.data.preferences.AppPreferences
import kotlinx.coroutines.launch

/**
 * First-launch setup wizard. Guides the user through:
 *   1. Welcome + add family members
 *   2. Add a CalDAV calendar account
 *   3. Done — mark setup complete and enter the app
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    app: HearthboardApp,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }

    // Person setup state
    var personName by remember { mutableStateOf("") }
    val people = remember { mutableStateListOf<Person>() }

    // Account setup state
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accountLabel by remember { mutableStateOf("") }
    var accountAdded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val steps = listOf("Welcome", "Family", "Calendar", "Done")

    Scaffold(
        topBar = {
            if (step > 0 && step < steps.size - 1) {
                TopAppBar(
                    title = { Text(steps[step]) },
                    navigationIcon = {
                        if (step > 0) {
                            IconButton(onClick = { step = (step - 1).coerceAtLeast(0) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        }
                    },
                    actions = {
                        TextButton(onClick = {
                            scope.launch {
                                app.preferences.set(AppPreferences.KEY_SETUP_DONE, true)
                                onComplete()
                            }
                        }) { Text("Skip") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step indicator
            if (steps.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    steps.forEachIndexed { i, _ ->
                        Box(
                            modifier = Modifier
                                .size(if (i == step) 32.dp else 8.dp, 8.dp)
                                .background(
                                    if (i <= step) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }
            }

            when (step) {
                0 -> WelcomeStep { step = 1 }
                1 -> FamilyStep(
                    people = people,
                    personName = personName,
                    onNameChange = { personName = it },
                    onAddPerson = {
                        if (personName.isNotBlank()) {
                            people.add(
                                Person(
                                    name = personName.trim(),
                                    colorHex = PersonColors[people.size % PersonColors.size],
                                    role = PersonRole.PARENT
                                )
                            )
                            personName = ""
                        }
                    },
                    onRemovePerson = { people.remove(it) },
                    onContinue = { step = 2 }
                )
                2 -> CalendarStep(
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    username = username,
                    onUsernameChange = { username = it },
                    password = password,
                    onPasswordChange = { password = it },
                    accountLabel = accountLabel,
                    onLabelChange = { accountLabel = it },
                    accountAdded = accountAdded,
                    saving = saving,
                    onAddAccount = {
                        scope.launch {
                            saving = true
                            try {
                                val encrypted = app.encryptor.encrypt(password)
                                val account = CalendarAccount(
                                    displayName = accountLabel.ifBlank { "My Calendar" },
                                    accountType = AccountType.CALDAV,
                                    serverUrl = serverUrl.trim(),
                                    username = username.trim(),
                                    passwordEncrypted = encrypted
                                )
                                val accountId = app.accountRepository.save(account)
                                accountAdded = true
                                // Trigger initial sync so events appear immediately
                                CalDAVSyncWorker.syncNow(app, accountId)
                            } catch (_: Exception) { }
                            saving = false
                        }
                    },
                    onSkip = { step = 3 },
                    onContinue = { step = 3 }
                )
                3 -> DoneStep(
                    onComplete = {
                        scope.launch {
                            // Seed people
                            for (p in people) {
                                app.personRepository.save(p)
                            }
                            app.preferences.set(AppPreferences.KEY_SETUP_DONE, true)
                            onComplete()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    Spacer(Modifier.height(48.dp))
    Icon(
        Icons.Default.CalendarMonth,
        contentDescription = null,
        modifier = Modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "Welcome to HearthBoard",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Your privacy-first family calendar and task manager.\n" +
        "Syncs with Google Calendar, Nextcloud, Fastmail, iCloud, " +
        "and any CalDAV server. Zero telemetry. No account required.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(32.dp))
    FilledTonalButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.GroupAdd, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add Family Members")
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text("Skip — I'll set it up later")
    }
}

@Composable
private fun FamilyStep(
    people: List<Person>,
    personName: String,
    onNameChange: (String) -> Unit,
    onAddPerson: () -> Unit,
    onRemovePerson: (Person) -> Unit,
    onContinue: () -> Unit
) {
    Spacer(Modifier.height(16.dp))
    Text(
        "Who's in your family?",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Add family members so you can assign tasks, color-code events, and plan meals.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    // Add person input
    OutlinedTextField(
        value = personName,
        onValueChange = onNameChange,
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            IconButton(onClick = onAddPerson, enabled = personName.isNotBlank()) {
                Icon(Icons.Default.PersonAdd, "Add")
            }
        }
    )
    Spacer(Modifier.height(16.dp))

    // Added people chips
    if (people.isNotEmpty()) {
        Text("Family members:", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        people.forEach { person ->
            ElevatedAssistChip(
                onClick = { },
                label = { Text(person.name) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                androidx.compose.ui.graphics.Color(
                                    android.graphics.Color.parseColor(person.colorHex)
                                ),
                                MaterialTheme.shapes.small
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(person.initial, color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                trailingIcon = {
                    IconButton(onClick = { onRemovePerson(person) },
                        modifier = Modifier.size(18.dp)) {
                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp))
                    }
                },
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
        Text(if (people.isEmpty()) "Skip — add later" else "Continue →")
    }
}

@Composable
private fun CalendarStep(
    serverUrl: String, onServerUrlChange: (String) -> Unit,
    username: String, onUsernameChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    accountLabel: String, onLabelChange: (String) -> Unit,
    accountAdded: Boolean, saving: Boolean,
    onAddAccount: () -> Unit, onSkip: () -> Unit, onContinue: () -> Unit
) {
    Spacer(Modifier.height(16.dp))
    Text(
        "Connect Your Calendar",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "Sync with any CalDAV server: Google Calendar, Nextcloud, Fastmail, iCloud, and more.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))

    // Quick presets
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Google" to "https://apidata.googleusercontent.com/caldav/v2/",
               "Fastmail" to "https://caldav.fastmail.com/",
               "Nextcloud" to "",
               "Mailcow" to "https://your-server.com/SOGo/dav/").forEach { (name, url) ->
            SuggestionChip(
                onClick = { onServerUrlChange(url) },
                label = { Text(name, fontSize = 12.sp) }
            )
        }
    }
    Spacer(Modifier.height(16.dp))

    OutlinedTextField(
        value = serverUrl,
        onValueChange = onServerUrlChange,
        label = { Text("Server URL") },
        placeholder = { Text("https://your-server.com/remote.php/dav/") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        label = { Text("Username / Email") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { Text("Password / App Password") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = accountLabel,
        onValueChange = onLabelChange,
        label = { Text("Label (optional)") },
        placeholder = { Text("My Calendar") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(16.dp))

    if (accountAdded) {
        AssistChip(
            onClick = { },
            label = { Text("Account added ✓") },
            leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) }
        )
        Spacer(Modifier.height(12.dp))
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
            Text("Skip")
        }
        Button(
            onClick = onAddAccount,
            enabled = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() && !saving,
            modifier = Modifier.weight(1f)
        ) {
            if (saving) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Add Account")
            }
        }
    }

    if (accountAdded) {
        Spacer(Modifier.height(12.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue →")
        }
    }

    Spacer(Modifier.height(32.dp))
}

@Composable
private fun DoneStep(onComplete: () -> Unit) {
    Spacer(Modifier.height(64.dp))
    Icon(
        Icons.Default.CheckCircle,
        contentDescription = null,
        modifier = Modifier.size(96.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(24.dp))
    Text(
        "You're all set!",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Your family calendar is ready. Add events, create tasks, " +
        "plan meals, and manage checklists — all without anyone tracking you.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(48.dp))
    Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
        Text("Get Started")
    }
}
