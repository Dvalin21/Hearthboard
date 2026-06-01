@file:Suppress("DEPRECATION")

package com.openlight.cal.ui.screens.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openlight.cal.data.model.Person
import com.openlight.cal.data.model.RedeemedReward
import com.openlight.cal.data.model.Reward
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.repository.RewardRepository
import com.openlight.cal.ui.viewmodel.RewardsViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Rewards screen — two distinct modes:
 *
 *   1. Shop mode (default, kid-facing)
 *      Big colorful tiles, tap to redeem, balance always visible.
 *
 *   2. Admin mode (parent-only, PIN-gated)
 *      Create/edit/delete catalog rewards, toggle enable state,
 *      view full redemption history.
 *
 * The admin mode uses the existing parentalPin pref (same gate as kiosk
 * mode unlock) so families with kids on the tablet have a single PIN to
 * remember. If no PIN is set, admin mode is open — same policy as kiosk.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(
    preferences: AppPreferences,
    viewModel: RewardsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val enabledRewards by viewModel.enabledRewards.collectAsStateWithLifecycle()
    val allRewards     by viewModel.allRewards.collectAsStateWithLifecycle()
    val people         by viewModel.people.collectAsStateWithLifecycle()
    val history        by viewModel.history.collectAsStateWithLifecycle()
    val storedPin      by preferences.parentalPin.collectAsStateWithLifecycle(initialValue = "")

    var adminMode by remember { mutableStateOf(false) }
    var showPinPrompt by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (adminMode) "🛠️ Rewards Admin" else "🌟 Rewards Shop") },
                actions = {
                    if (adminMode) {
                        TextButton(onClick = { adminMode = false }) {
                            Text("Done")
                        }
                    } else {
                        IconButton(onClick = {
                            if (storedPin.isBlank()) adminMode = true
                            else showPinPrompt = true
                        }) {
                            Icon(Icons.Default.Settings, "Admin")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (adminMode) {
                AdminPane(
                    allRewards = allRewards,
                    history    = history,
                    people     = people,
                    onSave     = viewModel::saveReward,
                    onDelete   = viewModel::deleteReward,
                    onUndo     = viewModel::undoRedemption
                )
            } else {
                ShopPane(
                    rewards    = enabledRewards,
                    people     = people,
                    viewModel  = viewModel
                )
            }
        }
    }

    if (showPinPrompt) {
        PinPromptDialog(
            storedPin = storedPin,
            onSuccess = {
                adminMode = true
                showPinPrompt = false
            },
            onDismiss = { showPinPrompt = false }
        )
    }

    // Surface redemption-result feedback as a transient snackbar-like banner.
    val redeemResult by viewModel.lastRedeemResult.collectAsStateWithLifecycle()
    redeemResult?.let { result ->
        RedeemResultDialog(
            result    = result,
            onDismiss = { viewModel.clearRedeemResult() }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// SHOP — kid-facing
// ─────────────────────────────────────────────────────────────
@Composable
private fun ShopPane(
    rewards: List<Reward>,
    people: List<Person>,
    viewModel: RewardsViewModel
) {
    // Default to the first non-"Everyone" person; UI doesn't currently expose
    // "Everyone" as a stars-spending entity.
    val realPeople = people.filter { !it.isDefault }
    var selectedPersonId by remember(realPeople) {
        mutableStateOf(realPeople.firstOrNull()?.id ?: 0L)
    }
    val selectedPerson = realPeople.find { it.id == selectedPersonId }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Person picker ─────────────────────────────────────
        if (realPeople.isNotEmpty()) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        realPeople.forEach { person ->
                            PersonPickerChip(
                                person   = person,
                                selected = person.id == selectedPersonId,
                                onClick  = { selectedPersonId = person.id }
                            )
                        }
                    }
                }
            }
        }

        // ── Live balance card ─────────────────────────────────
        if (selectedPerson != null) {
            val balance by viewModel.balanceFlow(selectedPersonId)
                .collectAsStateWithLifecycle(initialValue = 0)
            BalanceCard(person = selectedPerson, balance = balance)
        }

        Spacer(Modifier.height(8.dp))

        // ── Reward tiles ──────────────────────────────────────
        if (rewards.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text("No rewards yet", style = MaterialTheme.typography.titleMedium)
                    Text("Tap the gear above to add some",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement   = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(rewards) { reward ->
                    RewardTile(
                        reward    = reward,
                        canAfford = selectedPerson != null,
                        onClick   = {
                            if (selectedPersonId > 0) {
                                viewModel.redeem(reward.id, selectedPersonId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonPickerChip(person: Person, selected: Boolean, onClick: () -> Unit) {
    val color = remember(person.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
            .getOrElse { Color.Gray }
    }
    FilterChip(
        selected = selected,
        onClick  = onClick,
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(person.initial, color = Color.White,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        },
        label = { Text(person.name) }
    )
}

@Composable
private fun BalanceCard(person: Person, balance: Int) {
    val color = remember(person.colorHex) {
        runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
            .getOrElse { MaterialTheme.colorScheme.primary }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⭐", fontSize = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("${person.name}'s balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("$balance ⭐", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RewardTile(reward: Reward, canAfford: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(enabled = canAfford, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(reward.emoji, fontSize = 40.sp)
            Spacer(Modifier.height(8.dp))
            Text(reward.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(50)
            ) {
                Text("${reward.starCost} ⭐",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ADMIN — parent-facing (PIN-gated)
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPane(
    allRewards: List<Reward>,
    history: List<RedeemedReward>,
    people: List<Person>,
    onSave: (Reward) -> Unit,
    onDelete: (Reward) -> Unit,
    onUndo: (RedeemedReward) -> Unit
) {
    var editingReward by remember { mutableStateOf<Reward?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Catalog") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
        }
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> CatalogTab(
                    rewards     = allRewards,
                    onEdit      = { editingReward = it },
                    onDelete    = onDelete,
                    onNew       = { showNew = true }
                )
                1 -> HistoryTab(history = history, people = people, onUndo = onUndo)
            }
        }
    }

    if (showNew || editingReward != null) {
        RewardEditDialog(
            existing  = editingReward,
            onSave    = { reward ->
                onSave(reward)
                showNew = false
                editingReward = null
            },
            onDismiss = {
                showNew = false
                editingReward = null
            }
        )
    }
}

@Composable
private fun CatalogTab(
    rewards: List<Reward>,
    onEdit: (Reward) -> Unit,
    onDelete: (Reward) -> Unit,
    onNew: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // FAB-style add row
        ListItem(
            headlineContent = { Text("Add reward") },
            leadingContent  = { Icon(Icons.Default.Add, null) },
            modifier = Modifier.clickable(onClick = onNew)
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(rewards) { reward ->
                ListItem(
                    headlineContent = {
                        Text("${reward.emoji} ${reward.name}",
                            color = if (reward.isEnabled)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    supportingContent = {
                        Text("${reward.starCost} ⭐" +
                            (if (!reward.isEnabled) " · disabled" else ""))
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onEdit(reward) }) {
                                Icon(Icons.Default.Edit, "Edit")
                            }
                            IconButton(onClick = { onDelete(reward) }) {
                                Icon(Icons.Default.Delete, "Delete")
                            }
                        }
                    },
                    modifier = Modifier.clickable { onEdit(reward) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<RedeemedReward>,
    people: List<Person>,
    onUndo: (RedeemedReward) -> Unit
) {
    val df = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No redemptions yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(history) { item ->
            val person = people.find { it.id == item.personId }
            ListItem(
                headlineContent = {
                    Text("${item.rewardEmoji} ${item.rewardName}")
                },
                supportingContent = {
                    Text("${person?.name ?: "?"} · ${item.cost} ⭐ · ${df.format(Date(item.redeemedAtMs))}")
                },
                trailingContent = {
                    TextButton(onClick = { onUndo(item) }) {
                        Text("Undo")
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardEditDialog(
    existing: Reward?,
    onSave: (Reward) -> Unit,
    onDismiss: () -> Unit
) {
    var name      by remember { mutableStateOf(existing?.name ?: "") }
    var emoji     by remember { mutableStateOf(existing?.emoji ?: "🎁") }
    var starCost  by remember { mutableStateOf((existing?.starCost ?: 5).toString()) }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var isEnabled by remember { mutableStateOf(existing?.isEnabled ?: true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Text(if (existing == null) "New Reward" else "Edit Reward",
                    style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = {
                    val cost = starCost.toIntOrNull() ?: 0
                    if (name.isBlank() || cost <= 0) return@TextButton
                    onSave(
                        (existing ?: Reward(name = "", starCost = 1)).copy(
                            name        = name.trim(),
                            emoji       = emoji.ifBlank { "🎁" },
                            starCost    = cost,
                            description = description.trim(),
                            isEnabled   = isEnabled
                        )
                    )
                }) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = emoji,
                onValueChange = { if (it.length <= 2) emoji = it },
                label = { Text("Emoji") },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = starCost,
                onValueChange = { s -> starCost = s.filter { it.isDigit() } },
                label = { Text("Cost in stars") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                Spacer(Modifier.width(12.dp))
                Text(if (isEnabled) "Enabled — shows in shop"
                     else            "Disabled — hidden but kept for history")
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared bits
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinPromptDialog(
    storedPin: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter parent PIN") },
        text = {
            OutlinedTextField(
                value = entered,
                onValueChange = { s ->
                    entered = s.filter { it.isDigit() }.take(8)
                    error = false
                },
                isError = error,
                supportingText = if (error) ({ Text("Incorrect PIN") }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (entered == storedPin) onSuccess() else error = true
            }) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun RedeemResultDialog(
    result: RewardRepository.RedeemResult,
    onDismiss: () -> Unit
) {
    val (title, body) = when (result) {
        is RewardRepository.RedeemResult.Success ->
            "🎉 Redeemed!" to "New balance: ${result.newBalance} ⭐"
        is RewardRepository.RedeemResult.InsufficientStars ->
            "Not enough stars" to "Need ${result.need} ⭐, have ${result.have} ⭐"
        RewardRepository.RedeemResult.RewardNotFound ->
            "That reward is gone" to "Catalog may have been edited; try again."
        RewardRepository.RedeemResult.RewardDisabled ->
            "Reward unavailable" to "A parent disabled this reward."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text  = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}
