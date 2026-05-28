package com.openlight.cal.ui.screens.tasks

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.Person
import com.openlight.cal.data.model.Reward
import com.openlight.cal.data.model.RedeemedReward
import com.openlight.cal.ui.components.PersonChip
import kotlinx.coroutines.launch

/**
 * Chore Rewards Shop dialog.
 *
 * Two tabs:
 *   "Shop" — browse rewards and redeem them with earned stars
 *   "Manage" — parents create/edit/delete rewards
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RewardsShopDialog(
    database: AppDatabase,
    people: List<Person>,
    starsEarned: Map<Long, Int>,    // personId -> total stars earned
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val rewardDao = remember { database.rewardDao() }
    val rewards by rewardDao.getAllEnabledFlow().collectAsState(initial = emptyList())

    var selectedPersonId by remember { mutableStateOf(0L) }  // 0 = all
    var tab by remember { mutableStateOf(0) }
    var starsSpent by remember { mutableStateOf(0) }
    var recentRedemptions by remember { mutableStateOf<List<RedeemedReward>>(emptyList()) }
    var allRewards by remember { mutableStateOf<List<Reward>>(emptyList()) }

    // Load data
    LaunchedEffect(selectedPersonId) {
        starsSpent = rewardDao.starsSpentByPerson(selectedPersonId)
    }
    LaunchedEffect(Unit) {
        allRewards = rewardDao.getAll()
    }

    val totalEarned = if (selectedPersonId == 0L)
        starsEarned.values.sum()
    else
        starsEarned[selectedPersonId] ?: 0
    val currentBalance = totalEarned - starsSpent

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text("⭐ Rewards Shop", style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            // Person selector
            if (people.size > 1) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(selected = selectedPersonId == 0L,
                        onClick = { selectedPersonId = 0L }, label = { Text("All") })
                    people.filter { !it.isDefault }.forEach { p ->
                        FilterChip(selected = selectedPersonId == p.id,
                            onClick = { selectedPersonId = p.id },
                            label = { Text(p.name) })
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // Balance display
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Star Balance", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("$currentBalance ⭐",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.height(16.dp))

            // Tabs
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 },
                    text = { Text("Shop") })
                Tab(selected = tab == 1, onClick = { tab = 1 },
                    text = { Text("Manage Rewards") })
            }
            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> ShopTab(
                    rewards = rewards,
                    balance = currentBalance,
                    onRedeem = { reward ->
                        scope.launch {
                            rewardDao.redeem(RedeemedReward(
                                rewardId = reward.id,
                                personId = selectedPersonId
                            ))
                            starsSpent = rewardDao.starsSpentByPerson(selectedPersonId)
                        }
                    }
                )
                1 -> ManageTab(
                    rewards = allRewards,
                    onAdd = { name, cost, emoji ->
                        scope.launch {
                            rewardDao.insert(Reward(
                                name = name, starCost = cost, emoji = emoji
                            ))
                            allRewards = rewardDao.getAll()
                        }
                    },
                    onToggle = { reward ->
                        scope.launch {
                            rewardDao.update(reward.copy(isEnabled = !reward.isEnabled))
                            allRewards = rewardDao.getAll()
                        }
                    },
                    onDelete = { reward ->
                        scope.launch {
                            rewardDao.delete(reward)
                            allRewards = rewardDao.getAll()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun ShopTab(
    rewards: List<Reward>,
    balance: Int,
    onRedeem: (Reward) -> Unit
) {
    if (rewards.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CardGiftcard, null, modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No rewards yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Go to Manage tab to add some",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rewards, key = { it.id }) { reward ->
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (balance >= reward.starCost)
                            MaterialTheme.colorScheme.surface
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reward emoji
                        Text(reward.emoji, fontSize = 28.sp)
                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(reward.name, style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium)
                            Text("${reward.starCost} ⭐",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }

                        Button(
                            onClick = { onRedeem(reward) },
                            enabled = balance >= reward.starCost,
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Redeem")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageTab(
    rewards: List<Reward>,
    onAdd: (name: String, cost: Int, emoji: String) -> Unit,
    onToggle: (Reward) -> Unit,
    onDelete: (Reward) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Existing rewards
        if (rewards.isNotEmpty()) {
            rewards.forEach { reward ->
                ElevatedAssistChip(
                    onClick = { },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${reward.emoji} ${reward.name}  ·  ${reward.starCost}⭐")
                            Spacer(Modifier.weight(1f))
                            Text(if (reward.isEnabled) "Active" else "Disabled",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (reward.isEnabled)
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error)
                        }
                    },
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { onToggle(reward) },
                                modifier = Modifier.size(28.dp)) {
                                Icon(
                                    if (reward.isEnabled) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    "Toggle", modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { onDelete(reward) },
                                modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.DeleteOutline, "Delete",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                )
            }
        } else {
            Text("No rewards defined. Add your first reward!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp))
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { showAdd = true },
            modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("Add Reward")
        }

        if (showAdd) {
            AddRewardDialog(
                onSave = { name, cost, emoji ->
                    onAdd(name, cost, emoji)
                    showAdd = false
                },
                onDismiss = { showAdd = false }
            )
        }
    }
}

@Composable
private fun AddRewardDialog(
    onSave: (name: String, cost: Int, emoji: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("🎁") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Reward") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Reward name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = cost, onValueChange = { cost = it.filter { c -> c.isDigit() } },
                    label = { Text("Star cost") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = emoji, onValueChange = { emoji = it },
                    label = { Text("Emoji") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text("Pick an emoji to represent this reward",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val costInt = cost.toIntOrNull() ?: return@TextButton
                if (name.isNotBlank() && costInt > 0) onSave(name.trim(), costInt, emoji)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}