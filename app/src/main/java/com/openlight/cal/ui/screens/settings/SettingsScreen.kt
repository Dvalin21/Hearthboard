package com.openlight.cal.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openlight.cal.data.model.AccountType
import com.openlight.cal.data.model.CalendarAccount
import com.openlight.cal.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val darkMode     by viewModel.darkMode.collectAsState()
    val themeSeed    by viewModel.themeSeedColor.collectAsState()
    val fontSize     by viewModel.fontSize.collectAsState()
    val firstDayMon  by viewModel.firstDayMon.collectAsState()
    val use24Hr      by viewModel.use24Hr.collectAsState()
    val kioskMode    by viewModel.kioskMode.collectAsState()
    val syncWifiOnly by viewModel.syncWifiOnly.collectAsState()
    val showWeekends by viewModel.showWeekends.collectAsState()
    val accounts     by viewModel.accounts.collectAsState()
    val syncStatus   by viewModel.syncStatus.collectAsState()

    var showAddAccount by remember { mutableStateOf(false) }
    var editAccount    by remember { mutableStateOf<CalendarAccount?>(null) }
    var showPinDialog  by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(
            title = { Text("Settings") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        // ── ACCOUNTS ─────────────────────────────────────────
        SettingsSectionHeader("Calendar Accounts", Icons.Default.AccountCircle)

        accounts.forEach { account ->
            AccountRow(
                account = account,
                onSync  = { viewModel.syncNow(context, account.id) },
                onEdit  = { editAccount = account },
                onDelete = { viewModel.deleteAccount(account) }
            )
        }

        SettingsClickRow(
            icon  = Icons.Default.Add,
            title = "Add Account",
            subtitle = "CalDAV, Nextcloud, Fastmail, Google…",
            onClick = { showAddAccount = true }
        )

        if (syncStatus != null) {
            Text(
                text     = syncStatus!!,
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── APPEARANCE ────────────────────────────────────────
        SettingsSectionHeader("Appearance", Icons.Default.Palette)

        // Dark mode
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Theme", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System" to 0, "Light" to 1, "Dark" to 2).forEach { (label, value) ->
                    FilterChip(
                        selected = darkMode == value,
                        onClick  = { viewModel.setDarkMode(value) },
                        label    = { Text(label) },
                        leadingIcon = when (value) {
                            1    -> { { Icon(Icons.Default.LightMode, null, Modifier.size(16.dp)) } }
                            2    -> { { Icon(Icons.Default.DarkMode, null, Modifier.size(16.dp)) } }
                            else -> { { Icon(Icons.Default.BrightnessAuto, null, Modifier.size(16.dp)) } }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Font size
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Font Size", style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        fontSize <= 0.85f -> "Small"
                        fontSize >= 1.2f  -> "Large"
                        else             -> "Normal"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                value         = fontSize,
                onValueChange = { viewModel.setFontSize(it) },
                valueRange    = 0.75f..1.4f,
                steps         = 4,
                modifier      = Modifier.fillMaxWidth()
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── CALENDAR DISPLAY ──────────────────────────────────
        SettingsSectionHeader("Calendar Display", Icons.Default.CalendarMonth)

        SettingsToggleRow(
            icon     = Icons.Default.CalendarViewWeek,
            title    = "Start week on Monday",
            checked  = firstDayMon,
            onToggle = { viewModel.setFirstDayMon(it) }
        )

        SettingsToggleRow(
            icon     = Icons.Default.Schedule,
            title    = "24-hour clock",
            checked  = use24Hr,
            onToggle = { viewModel.set24Hr(it) }
        )

        SettingsToggleRow(
            icon     = Icons.Default.Weekend,
            title    = "Show weekends",
            checked  = showWeekends,
            onToggle = { viewModel.setShowWeekends(it) }
        )

        // Default view
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ViewModule, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
                Text("Default View", style = MaterialTheme.typography.bodyMedium)
            }
        }
        val defaultView by viewModel.defaultView.collectAsState()
        Row(
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("MONTH", "WEEK", "DAY", "AGENDA").forEach { view ->
                FilterChip(
                    selected = defaultView == view,
                    onClick  = { viewModel.setDefaultView(view) },
                    label    = { Text(view.lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── SYNC ─────────────────────────────────────────────
        SettingsSectionHeader("Sync", Icons.Default.Sync)

        SettingsToggleRow(
            icon     = Icons.Default.Wifi,
            title    = "Sync on Wi-Fi only",
            subtitle = "Avoid mobile data usage",
            checked  = syncWifiOnly,
            onToggle = { viewModel.setSyncWifiOnly(it) }
        )

        SettingsClickRow(
            icon    = Icons.Default.SyncAlt,
            title   = "Sync all accounts now",
            onClick = { viewModel.syncNow(context) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── ADVANCED / KIOSK ─────────────────────────────────
        SettingsSectionHeader("Advanced", Icons.Default.Tune)

        SettingsToggleRow(
            icon     = Icons.Default.LockPerson,
            title    = "Kiosk / Launcher Mode",
            subtitle = "Lock device to OpenLight as home screen",
            checked  = kioskMode,
            onToggle = { viewModel.setKioskMode(it) }
        )

        SettingsClickRow(
            icon     = Icons.Default.Pin,
            title    = "Parental Lock PIN",
            subtitle = "Restrict access to settings",
            onClick  = { showPinDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // ── ABOUT ────────────────────────────────────────────
        SettingsSectionHeader("About", Icons.Default.Info)

        SettingsClickRow(
            icon     = Icons.Default.Shield,
            title    = "No Telemetry",
            subtitle = "Zero data collection. Ever. This app does not phone home.",
            onClick  = {}
        )

        SettingsClickRow(
            icon     = Icons.Default.Code,
            title    = "Source Code",
            subtitle = "GPL-3.0 — Free & open source",
            onClick  = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/openlight/openlight"))
                )
            }
        )

        SettingsClickRow(
            icon     = Icons.Default.AppSettingsAlt,
            title    = "Version",
            subtitle = "OpenLight 1.0.0",
            onClick  = {}
        )

        Spacer(Modifier.height(40.dp))
    }

    // ── Dialogs ──────────────────────────────────────────────
    if (showAddAccount) {
        AccountEditDialog(
            account   = null,
            onSave    = { account ->
                viewModel.saveAccount(account)
                viewModel.syncNow(context)
                showAddAccount = false
            },
            onDismiss = { showAddAccount = false },
            encodePassword = viewModel::encodePassword
        )
    }

    editAccount?.let { acc ->
        AccountEditDialog(
            account   = acc,
            onSave    = { updated ->
                viewModel.saveAccount(updated)
                editAccount = null
            },
            onDismiss = { editAccount = null },
            encodePassword = viewModel::encodePassword
        )
    }

    if (showPinDialog) {
        PinDialog(
            onSave    = { pin -> viewModel.setParentalPin(pin); showPinDialog = false },
            onDismiss = { showPinDialog = false }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// Settings row helpers
// ─────────────────────────────────────────────────────────────
@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text  = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsClickRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Account row in settings list
// ─────────────────────────────────────────────────────────────
@Composable
private fun AccountRow(
    account: CalendarAccount,
    onSync: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(account.colorHex))
    }.getOrElse { Color(0xFF2196F3) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CalendarMonth, null, tint = Color.White,
                modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(account.displayName, style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text(
                text = when {
                    account.lastSyncMs == 0L -> "Never synced"
                    else -> "Last synced: ${java.text.SimpleDateFormat("MMM d h:mm a",
                        java.util.Locale.getDefault()).format(java.util.Date(account.lastSyncMs))}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onSync) {
            Icon(Icons.Default.Sync, "Sync now",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, "Edit", modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.DeleteOutline, "Delete",
                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Add/Edit Account Dialog
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditDialog(
    account: CalendarAccount?,
    onSave: (CalendarAccount) -> Unit,
    onDismiss: () -> Unit,
    encodePassword: (String) -> String
) {
    val scope = rememberCoroutineScope()

    var displayName  by remember { mutableStateOf(account?.displayName ?: "") }
    var serverUrl    by remember { mutableStateOf(account?.serverUrl ?: "") }
    var username     by remember { mutableStateOf(account?.username ?: "") }
    var password     by remember { mutableStateOf("") }  // never pre-fill
    var colorHex     by remember { mutableStateOf(account?.colorHex ?: "#2196F3") }
    var accountType  by remember { mutableStateOf(account?.accountType ?: AccountType.CALDAV) }
    var showPassword by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }

    // Preset URLs for common providers
    val presets = mapOf(
        "Google Calendar" to "https://apidata.googleusercontent.com/caldav/v2/",
        "Fastmail"        to "https://caldav.fastmail.com/",
        "Nextcloud"       to "",    // user enters their instance URL
        "iCloud"          to "https://caldav.icloud.com/",
        "Proton Calendar" to "https://calendar.proton.me/",
        "Custom CalDAV"   to ""
    )
    var selectedPreset by remember { mutableStateOf("Custom CalDAV") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Text(if (account == null) "Add Account" else "Edit Account",
                    style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    val encPass = if (password.isNotBlank()) encodePassword(password)
                                  else account?.passwordEncrypted ?: ""
                    onSave(
                        (account ?: CalendarAccount(displayName = "")).copy(
                            displayName       = displayName.ifBlank { selectedPreset },
                            serverUrl         = serverUrl.trim(),
                            username          = username.trim(),
                            passwordEncrypted = encPass,
                            colorHex          = colorHex,
                            accountType       = accountType
                        )
                    )
                }) { Text("Save") }
            }

            Spacer(Modifier.height(16.dp))

            // Provider quick-select
            Text("Provider", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.keys.chunked(3).forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            FilterChip(
                                selected = selectedPreset == preset,
                                onClick  = {
                                    selectedPreset = preset
                                    displayName    = preset
                                    val url = presets[preset] ?: ""
                                    if (url.isNotBlank()) serverUrl = url
                                },
                                label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value         = displayName,
                onValueChange = { displayName = it },
                label         = { Text("Account name") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Default.Label, null) }
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value         = serverUrl,
                onValueChange = { serverUrl = it },
                label         = { Text("CalDAV Server URL") },
                placeholder   = { Text("https://your-server.com/dav/") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Default.Link, null) }
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value         = username,
                onValueChange = { username = it },
                label         = { Text("Username / Email") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                leadingIcon   = { Icon(Icons.Default.Person, null) }
            )
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value         = password,
                onValueChange = { password = it },
                label         = { Text(if (account == null) "Password / App Password" else "New password (leave blank to keep)") },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true,
                visualTransformation = if (showPassword) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                leadingIcon  = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            "Toggle password"
                        )
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            // Account color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Calendar Color", style = MaterialTheme.typography.bodyMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                runCatching { Color(android.graphics.Color.parseColor(colorHex)) }
                                    .getOrElse { Color(0xFF2196F3) },
                                CircleShape
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { showColorPicker = !showColorPicker }) {
                        Text(if (showColorPicker) "Done" else "Change")
                    }
                }
            }
            if (showColorPicker) {
                ColorPickerGrid(selectedHex = colorHex, onSelect = { colorHex = it })
            }

            // Help text
            Spacer(Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Tips", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "For Google: use an App Password (myaccount.google.com > Security > App Passwords)\n" +
                        "For Nextcloud: Settings > Security > Devices & Sessions\n" +
                        "For Fastmail: Settings > Passwords & Security > API Tokens",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PIN dialog
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin    by remember { mutableStateOf("") }
    var pinErr by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Parental Lock PIN") },
        text  = {
            Column {
                Text("Enter a 4-digit PIN to protect Settings.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value         = pin,
                    onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it; pinErr = false },
                    label         = { Text("4-digit PIN") },
                    isError       = pinErr,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier      = Modifier.fillMaxWidth(),
                    singleLine    = true
                )
                if (pinErr) Text("PIN must be 4 digits",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onSave("") }) { Text("Remove PIN") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (pin.length == 4) onSave(pin)
                else pinErr = true
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Reuse ColorPickerGrid here from components
@Composable
private fun ColorPickerGrid(selectedHex: String, onSelect: (String) -> Unit) {
    com.openlight.cal.ui.components.ColorPickerGrid(selectedHex = selectedHex, onSelect = onSelect)
}
