package com.openlight.cal.ui.screens.rewards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import java.text.DateFormat
import java.util.Date

/**
 * Rewards screen — two distinct modes:
 *
 *   1. Shop mode (default, kid-facing)
 *      Per-profile sections with horizontal reward cards, Redeem button,
 *      progress indicator when insufficient stars.
 *
 *   2. Admin mode (parent-only, PIN-gated)
 *      Create/edit/delete catalog rewards, adjust profile stars, view
 *      full redemption history.
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
                            Icon(Icons.Filled.Settings, "Admin")
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
                    onUndo     = viewModel::undoRedemption,
                    onGiveStars   = { id, amt -> viewModel.giveStars(id, amt) },
                    onRemoveStars = { id, amt -> viewModel.removeStars(id, amt) },
                    viewModel  = viewModel
                )
            } else {
                ShopPane(
                    rewards   = enabledRewards,
                    people    = people,
                    viewModel = viewModel,
                    history   = history
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

    // Surface redemption-result feedback as a transient dialog.
    val redeemResult by viewModel.lastRedeemResult.collectAsStateWithLifecycle()
    redeemResult?.let { result ->
        RedeemResultDialog(
            result    = result,
            onDismiss = { viewModel.clearRedeemResult() }
        )
    }
}

// ─────────────────────────────────────────────────────────────
// SHOP — kid-facing, per-profile sections
// ─────────────────────────────────────────────────────────────
@Composable
private fun ShopPane(
    rewards: List<Reward>,
    people: List<Person>,
    viewModel: RewardsViewModel,
    history: List<RedeemedReward>
) {
    val realPeople = people.filter { !it.isDefault }

    // Compute per-person balances.
    val balances: Map<Long, Int> by remember(realPeople) {
        val flows = realPeople.map { person ->
            viewModel.balanceFlow(person.id).map { balance -> person.id to balance }
        }
        if (flows.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine<Pair<Long, Int>, Map<Long, Int>>(flows) { pairs -> pairs.toMap() }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyMap())

    // Per-person set of already-redeemed reward IDs (for consumed-by-redemption check).
    val redeemedByPerson = remember(history) {
        history.groupBy({ it.personId }, { it.rewardId })
            .mapValues { it.value.toSet() }
    }

    if (realPeople.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("👤", fontSize = 48.sp)
                Spacer(Modifier.height(16.dp))
                Text("No profiles yet", style = MaterialTheme.typography.titleMedium)
                Text("Add profiles in People settings",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        realPeople.forEach { person ->
            val balance = balances[person.id] ?: 0
            val redeemedIds = redeemedByPerson[person.id] ?: emptySet()

            // Filter to rewards this person is eligible for:
            //   - assigned to this person specifically OR unassigned (0 = anyone)
            //   - NOT consumed if renewAfterRedeeming is false and already redeemed
            val eligibleRewards = rewards.filter { reward ->
                val assigned = reward.assignedPersonId == 0L
                        || reward.assignedPersonId == person.id
                val consumed = !reward.renewAfterRedeeming
                        && redeemedIds.contains(reward.id)
                assigned && !consumed
            }

            ProfileSection(
                person  = person,
                balance = balance,
                rewards = eligibleRewards,
                onRedeem = { rewardId ->
                    viewModel.redeem(rewardId, person.id)
                }
            )
        }

        // Bottom padding for nav bar
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * Single profile section: avatar + name + star balance header row,
 * followed by a horizontally scrolling row of reward cards.
 */
@Composable
private fun ProfileSection(
    person: Person,
    balance: Int,
    rewards: List<Reward>,
    onRedeem: (Long) -> Unit
) {
    val fallback = MaterialTheme.colorScheme.primary
    val color = remember(person.colorHex, fallback) {
        runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
            .getOrElse { fallback }
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        // ── Profile header row ────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(person.initial, color = Color.White,
                    fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            // Name
            Text(person.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            // Star balance
            Text("⭐ $balance",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFB300))
        }

        // ── Reward cards row (horizontal scroll) ─────────────
        if (rewards.isEmpty()) {
            Text("  No rewards available",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rewards.forEach { reward ->
                    RewardCard(
                        reward  = reward,
                        balance = balance,
                        onRedeem = { onRedeem(reward.id) }
                    )
                }
            }
        }
    }

    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

/**
 * Single reward card in the horizontal scroll row.
 *
 * When the person can afford it: show a prominent Redeem button.
 * When they cannot: show progress bar + "X more ⭐ needed".
 */
@Composable
private fun RewardCard(
    reward: Reward,
    balance: Int,
    onRedeem: () -> Unit
) {
    val canAfford = balance >= reward.starCost

    Card(
        modifier = Modifier
            .width(150.dp)
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (canAfford)
                MaterialTheme.colorScheme.surfaceVariant
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emoji
            Text(reward.emoji, fontSize = 32.sp)

            Spacer(Modifier.height(6.dp))

            // Name
            Text(reward.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                textAlign = TextAlign.Center,
                color = if (canAfford)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(Modifier.height(4.dp))

            // Star cost badge
            Surface(
                color = if (canAfford)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50)
            ) {
                Text("${reward.starCost} ⭐",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(8.dp))

            // Redeem button or progress indicator
            if (canAfford) {
                Button(
                    onClick = onRedeem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("Redeem", fontSize = 12.sp)
                }
            } else {
                val needed = reward.starCost - balance
                val progress = if (reward.starCost > 0)
                    balance.toFloat() / reward.starCost else 0f

                // Thin progress bar
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFFFFB300),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text("$needed more ⭐",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium)
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
    onUndo: (RedeemedReward) -> Unit,
    onGiveStars: (personId: Long, amount: Int) -> Unit,
    onRemoveStars: (personId: Long, amount: Int) -> Unit,
    viewModel: RewardsViewModel
) {
    var editingReward by remember { mutableStateOf<Reward?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }

    Column {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Catalog") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Stars") })
            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("History") })
        }
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                0 -> CatalogTab(
                    rewards     = allRewards,
                    onEdit      = { editingReward = it },
                    onDelete    = onDelete,
                    onNew       = { showNew = true }
                )
                1 -> StarsTab(
                    people    = people,
                    viewModel = viewModel,
                    onGiveStars   = onGiveStars,
                    onRemoveStars = onRemoveStars
                )
                2 -> HistoryTab(history = history, people = people, onUndo = onUndo)
            }
        }
    }

    if (showNew || editingReward != null) {
        RewardEditDialog(
            existing  = editingReward,
            people    = people,
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
        ListItem(
            headlineContent = { Text("Add reward") },
            leadingContent  = { Icon(Icons.Filled.Add, null) },
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
                            (if (!reward.isEnabled) " · disabled" else "") +
                            (if (reward.renewAfterRedeeming) " · renew" else ""))
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { onEdit(reward) }) {
                                Icon(Icons.Filled.Edit, "Edit")
                            }
                            IconButton(onClick = { onDelete(reward) }) {
                                Icon(Icons.Filled.Delete, "Delete")
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

/**
 * Admin tab for adjusting profile star balances.
 * Shows each person with their current balance and +/- buttons.
 */
@Composable
private fun StarsTab(
    people: List<Person>,
    viewModel: RewardsViewModel,
    onGiveStars: (personId: Long, amount: Int) -> Unit,
    onRemoveStars: (personId: Long, amount: Int) -> Unit
) {
    val realPeople = people.filter { !it.isDefault }

    // Per-person balances
    val balances: Map<Long, Int> by remember(realPeople) {
        val flows = realPeople.map { person ->
            viewModel.balanceFlow(person.id).map { balance -> person.id to balance }
        }
        if (flows.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine<Pair<Long, Int>, Map<Long, Int>>(flows) { pairs -> pairs.toMap() }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyMap())

    var adjustingPerson by remember { mutableStateOf<Person?>(null) }
    var adjustAmount by remember { mutableStateOf("") }
    var isGiving by remember { mutableStateOf(true) }

    if (realPeople.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No profiles yet",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(realPeople) { person ->
            val balance = balances[person.id] ?: 0
            val fallback = MaterialTheme.colorScheme.primary
            val color = remember(person.colorHex, fallback) {
                runCatching { Color(android.graphics.Color.parseColor(person.colorHex)) }
                    .getOrElse { fallback }
            }

            ListItem(
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(person.initial, color = Color.White,
                            fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                },
                headlineContent = { Text(person.name) },
                supportingContent = { Text("⭐ $balance") },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = {
                                adjustingPerson = person
                                isGiving = false
                                adjustAmount = ""
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("-", fontSize = 16.sp) }
                        FilledTonalButton(
                            onClick = {
                                adjustingPerson = person
                                isGiving = true
                                adjustAmount = ""
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) { Text("+", fontSize = 16.sp) }
                    }
                }
            )
            HorizontalDivider()
        }
    }

    // Star adjustment dialog
    if (adjustingPerson != null) {
        val person = adjustingPerson!!
        AlertDialog(
            onDismissRequest = { adjustingPerson = null },
            title = { Text(if (isGiving) "Give Stars" else "Remove Stars") },
            text = {
                Column {
                    Text("${person.name} · Current: ⭐ ${balances[person.id] ?: 0}")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = adjustAmount,
                        onValueChange = { adjustAmount = it.filter { c -> c.isDigit() } },
                        label = { Text(if (isGiving) "Stars to give" else "Stars to remove") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val amt = adjustAmount.toIntOrNull()
                        if (amt != null && amt > 0) {
                            if (isGiving) onGiveStars(person.id, amt)
                            else onRemoveStars(person.id, amt)
                        }
                        adjustingPerson = null
                    }
                ) { Text(if (isGiving) "Give" else "Remove") }
            },
            dismissButton = {
                TextButton(onClick = { adjustingPerson = null }) { Text("Cancel") }
            }
        )
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

// ─────────────────────────────────────────────────────────────
// REWARD EDIT DIALOG
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardEditDialog(
    existing: Reward?,
    people: List<Person>,
    onSave: (Reward) -> Unit,
    onDismiss: () -> Unit
) {
    val realPeople = people.filter { !it.isDefault }

    var name      by remember { mutableStateOf(existing?.name ?: "") }
    var emoji     by remember { mutableStateOf(existing?.emoji ?: "🎁") }
    var starCost  by remember { mutableStateOf((existing?.starCost ?: 5).toString()) }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var isEnabled by remember { mutableStateOf(existing?.isEnabled ?: true) }
    var renewAfterRedeeming by remember { mutableStateOf(existing?.renewAfterRedeeming ?: false) }
    var assignedPersonId by remember { mutableStateOf(existing?.assignedPersonId ?: 0L) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 32.dp)
        ) {
            // ── Header ────────────────────────────────────────
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
                            isEnabled   = isEnabled,
                            renewAfterRedeeming = renewAfterRedeeming,
                            assignedPersonId = assignedPersonId
                        )
                    )
                }) { Text("Save") }
            }

            Spacer(Modifier.height(16.dp))

            // ── Emoji ─────────────────────────────────────────
            OutlinedTextField(
                value = emoji,
                onValueChange = { if (it.length <= 2) emoji = it },
                label = { Text("Emoji") },
                modifier = Modifier.width(120.dp),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // ── Name ──────────────────────────────────────────
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // ── Cost ──────────────────────────────────────────
            OutlinedTextField(
                value = starCost,
                onValueChange = { s -> starCost = s.filter { it.isDigit() } },
                label = { Text("Cost in stars") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(Modifier.height(12.dp))

            // ── Description ───────────────────────────────────
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // ── Renew after redeeming toggle ──────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = renewAfterRedeeming,
                    onCheckedChange = { renewAfterRedeeming = it })
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Renew after redeeming",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("Reward will be available again after redemption",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Enable toggle ─────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                Spacer(Modifier.width(12.dp))
                Text(if (isEnabled) "Enabled — shows in shop"
                     else            "Disabled — hidden but kept for history")
            }

            // ── Eligible Profile ──────────────────────────────
            if (realPeople.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Eligible Profile",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                // "Anyone" chip
                FilterChip(
                    selected = assignedPersonId == 0L,
                    onClick  = { assignedPersonId = 0L },
                    label = { Text("Anyone") },
                    modifier = Modifier.padding(end = 4.dp)
                )

                Spacer(Modifier.height(4.dp))

                // Per-person chips in a flow row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    realPeople.forEach { person ->
                        val sel = assignedPersonId == person.id
                        val fallback = MaterialTheme.colorScheme.primary
                        val color = remember(person.colorHex, fallback) {
                            runCatching {
                                Color(android.graphics.Color.parseColor(person.colorHex))
                            }.getOrElse { fallback }
                        }
                        FilterChip(
                            selected = sel,
                            onClick  = { assignedPersonId = if (sel) 0L else person.id },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(person.initial, color = Color.White,
                                        fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            },
                            label = { Text(person.name) }
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Shared dialogs
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
