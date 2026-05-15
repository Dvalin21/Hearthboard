package com.openlight.cal.ui.screens.lists

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import androidx.compose.ui.unit.dp
import com.openlight.cal.data.db.AppDatabase
import com.openlight.cal.data.model.CheckList
import com.openlight.cal.data.model.CheckListItem
import com.openlight.cal.ui.components.ColorPickerGrid
import com.openlight.cal.ui.components.SectionHeader
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    database: AppDatabase,
    modifier: Modifier = Modifier
) {
    val dao    = remember { database.checkListDao() }
    val scope  = rememberCoroutineScope()

    val allLists by dao.getAllFlow().collectAsState(initial = emptyList())
    var selectedListId by remember { mutableStateOf<Long?>(null) }
    var showNewList    by remember { mutableStateOf(false) }

    val selectedList = allLists.find { it.id == selectedListId }

    Row(modifier = modifier.fillMaxSize()) {
        // ── Left: list of lists ──────────────────────────────
        if (selectedListId == null || allLists.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title   = { Text("Lists") },
                    actions = {
                        IconButton(onClick = { showNewList = true }) {
                            Icon(Icons.Default.Add, "New list")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                if (allLists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.List, null, modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Text("No lists yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { showNewList = true }) {
                                Text("Create a list")
                            }
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(8.dp)) {
                        items(allLists, key = { it.id }) { list ->
                            ListCard(
                                list     = list,
                                onClick  = { selectedListId = list.id },
                                onDelete = {
                                    scope.launch { dao.deleteList(list) }
                                }
                            )
                        }
                    }
                }
            }
        } else {
            // ── Right: list detail ──────────────────────────
            selectedList?.let { list ->
                ListDetailScreen(
                    list     = list,
                    dao      = dao,
                    onBack   = { selectedListId = null },
                    onUpdate = { updated -> scope.launch { dao.updateList(updated) } }
                )
            }
        }
    }

    if (showNewList) {
        NewListDialog(
            onSave = { name, colorHex ->
                scope.launch {
                    val id = dao.insertList(CheckList(name = name, colorHex = colorHex))
                    selectedListId = id
                }
                showNewList = false
            },
            onDismiss = { showNewList = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListCard(
    list: CheckList,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val color = runCatching {
        Color(android.graphics.Color.parseColor(list.colorHex))
    }.getOrElse { Color(0xFFFF9800) }

    Card(
        onClick  = onClick,
        modifier = Modifier.fillMaxWidth().padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.List, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(16.dp))
            Text(
                list.name,
                style    = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListDetailScreen(
    list: CheckList,
    dao: com.openlight.cal.data.db.dao.CheckListDao,
    onBack: () -> Unit,
    onUpdate: (CheckList) -> Unit
) {
    val scope = rememberCoroutineScope()
    val items by dao.getItemsFlow(list.id).collectAsState(initial = emptyList())
    var newItemText by remember { mutableStateOf("") }
    val listColor   = runCatching {
        Color(android.graphics.Color.parseColor(list.colorHex))
    }.getOrElse { Color(0xFFFF9800) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(28.dp).background(listColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.List, null, tint = Color.White,
                                modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(list.name)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Clear checked
                    val hasChecked = items.any { it.isChecked }
                    if (hasChecked) {
                        TextButton(onClick = {
                            scope.launch { dao.clearCheckedItems(list.id) }
                        }) { Text("Clear done") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                val unchecked = items.filter { !it.isChecked }
                val checked   = items.filter { it.isChecked }

                items(unchecked, key = { it.id }) { item ->
                    CheckListItemRow(
                        item      = item,
                        onToggle  = { scope.launch { dao.setItemChecked(item.id, !item.isChecked) } },
                        onDelete  = { scope.launch { dao.deleteItem(item) } },
                        listColor = listColor
                    )
                }
                if (checked.isNotEmpty()) {
                    item { SectionHeader("Completed  •  ${checked.size}") }
                    items(checked, key = { "c_${it.id}" }) { item ->
                        CheckListItemRow(
                            item      = item,
                            onToggle  = { scope.launch { dao.setItemChecked(item.id, !item.isChecked) } },
                            onDelete  = { scope.launch { dao.deleteItem(item) } },
                            listColor = listColor
                        )
                    }
                }
            }

            // Add item row
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value         = newItemText,
                    onValueChange = { newItemText = it },
                    placeholder   = { Text("Add item…") },
                    modifier      = Modifier.weight(1f),
                    singleLine    = true
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick  = {
                        if (newItemText.isNotBlank()) {
                            scope.launch {
                                dao.insertItem(
                                    CheckListItem(
                                        listId    = list.id,
                                        text      = newItemText.trim(),
                                        sortOrder = items.size
                                    )
                                )
                                newItemText = ""
                            }
                        }
                    },
                    enabled  = newItemText.isNotBlank()
                ) {
                    Icon(Icons.Default.AddCircle, "Add", tint = listColor)
                }
            }
        }
    }
}

@Composable
private fun CheckListItemRow(
    item: CheckListItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    listColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked  = item.isChecked,
            onCheckedChange = { onToggle() },
            colors   = CheckboxDefaults.colors(checkedColor = listColor)
        )
        Text(
            text      = item.text,
            style     = MaterialTheme.typography.bodyMedium,
            modifier  = Modifier.weight(1f),
            color     = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (item.isChecked)
                androidx.compose.ui.text.style.TextDecoration.LineThrough else null
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewListDialog(
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var name        by remember { mutableStateOf("") }
    var colorHex    by remember { mutableStateOf("#FF9800") }
    var nameError   by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New List") },
        text  = {
            Column {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it; nameError = false },
                    label         = { Text("List name") },
                    isError       = nameError,
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                ColorPickerGrid(selectedHex = colorHex, onSelect = { colorHex = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { nameError = true } else onSave(name.trim(), colorHex)
            }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
