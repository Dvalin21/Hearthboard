package com.openlight.cal.ui.screens.recipes

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openlight.cal.data.mealie.MealieApi
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Recipe browser — syncs from Mealie self-hosted recipe manager,
 * and supports local-only recipes created directly on the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    preferences: AppPreferences,
    recipeRepository: RecipeRepository,
    onAddToMealPlan: (name: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val mealieUrl by preferences.mealieUrl.collectAsState(initial = "")
    val mealieToken by preferences.mealieToken.collectAsState(initial = "")

    val mealieRecipes = remember { mutableStateListOf<MealieApi.MealieRecipe>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val localRecipes by recipeRepository.getAllFlow()
        .collectAsState(initial = emptyList())

    var showAddDialog by remember { mutableStateOf(false) }
    var newRecipeName by remember { mutableStateOf("") }
    var newRecipeNotes by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(mealieUrl, mealieToken) {
        if (mealieUrl.isBlank() || mealieToken.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        error = null
        withContext(Dispatchers.IO) {
            try {
                val api = MealieApi(mealieUrl, mealieToken)
                val result = api.getRecipes()
                if (result.isEmpty()) {
                    error = "No recipes found"
                } else {
                    mealieRecipes.clear()
                    mealieRecipes.addAll(result)
                }
            } catch (e: Exception) {
                error = "Failed to load recipes: ${e.message}"
            }
        }
        loading = false
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Recipes") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    newRecipeName = ""
                    newRecipeNotes = ""
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add recipe")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (mealieUrl.isBlank() && localRecipes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Restaurant, null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text("No recipes yet",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Connect Mealie in Settings or add a local recipe.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (loading && mealieRecipes.isEmpty() && localRecipes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading recipes…")
                    }
                }
            } else if (error != null && mealieRecipes.isEmpty() && localRecipes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CloudOff, null, modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                // Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search recipes…") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                val localFiltered = if (searchQuery.isBlank()) localRecipes
                    else localRecipes.filter { it.name.contains(searchQuery, ignoreCase = true) }

                val syncedFiltered = if (searchQuery.isBlank()) mealieRecipes
                    else mealieRecipes.filter { it.name.contains(searchQuery, ignoreCase = true) }

                if (localFiltered.isEmpty() && syncedFiltered.isEmpty() && searchQuery.isNotBlank()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No matching recipes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (localFiltered.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Local",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(localFiltered, key = { "local-${it.id}" }) { recipe ->
                                ElevatedCard(
                                    onClick = { /* view detail */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(recipe.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium)
                                            if (recipe.notes.isNotBlank()) {
                                                Text(recipe.notes,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2)
                                            }
                                        }
                                        FilledTonalIconButton(
                                            onClick = { onAddToMealPlan(recipe.name) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, "Add to meal plan",
                                                modifier = Modifier.size(18.dp))
                                        }
                                        IconButton(onClick = {
                                            scope.launch {
                                                recipeRepository.delete(recipe)
                                            }
                                        }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete local recipe",
                                                tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        if (syncedFiltered.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Mealie",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                            items(syncedFiltered, key = { it.slug }) { recipe ->
                                ElevatedCard(
                                    onClick = { /* view detail */ },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(recipe.name,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium)
                                            if (recipe.description.isNotBlank()) {
                                                Text(recipe.description,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 2)
                                            }
                                            if (recipe.category.isNotBlank()) {
                                                Text(recipe.category,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                        FilledTonalIconButton(
                                            onClick = { onAddToMealPlan(recipe.name) },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, "Add to meal plan",
                                                modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add local recipe") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newRecipeName,
                        onValueChange = { newRecipeName = it },
                        label = { Text("Recipe name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newRecipeNotes,
                        onValueChange = { newRecipeNotes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newRecipeName.isNotBlank()) {
                        scope.launch {
                            recipeRepository.saveLocal(
                                com.openlight.cal.data.model.Recipe(
                                    name = newRecipeName.trim(),
                                    notes = newRecipeNotes.trim()
                                )
                            )
                            showAddDialog = false
                        }
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
