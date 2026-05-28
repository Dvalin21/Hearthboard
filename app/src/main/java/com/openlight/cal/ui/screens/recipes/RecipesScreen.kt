@file:Suppress("DEPRECATION")

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Recipe browser — syncs from Mealie self-hosted recipe manager.
 * Configure Mealie server URL + API token in Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    preferences: AppPreferences,
    onAddToMealPlan: (name: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val mealieUrl by preferences.mealieUrl.collectAsState(initial = "")
    val mealieToken by preferences.mealieToken.collectAsState(initial = "")

    val recipes = remember { mutableStateListOf<MealieApi.MealieRecipe>() }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

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
                    recipes.clear()
                    recipes.addAll(result)
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
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (mealieUrl.isBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Restaurant, null, modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(16.dp))
                        Text("No Mealie server configured",
                            style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Go to Settings → Mealie Recipes to set up",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Loading recipes…")
                    }
                }
            } else if (error != null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(48.dp),
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
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )

                val filtered = if (searchQuery.isBlank()) recipes
                    else recipes.filter { it.name.contains(searchQuery, ignoreCase = true) }

                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered, key = { it.slug }) { recipe ->
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
                                    Text(recipe.name, style = MaterialTheme.typography.bodyLarge,
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
                                    Icon(Icons.Default.Add, "Add to meal plan",
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