package com.openlight.cal.ui.screens.recipes

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openlight.cal.data.mealie.MealieApi
import com.openlight.cal.data.model.Recipe
import com.openlight.cal.data.preferences.AppPreferences
import com.openlight.cal.data.repository.RecipeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Recipe browser — syncs from Mealie self-hosted recipe manager,
 * and supports local-only recipes created directly on the device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    database: com.openlight.cal.data.db.AppDatabase,
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

    var showEditDialog by remember { mutableStateOf(false) }
    var editRecipeTarget by remember { mutableStateOf<Recipe?>(null) }
    val scope = rememberCoroutineScope()

    // ── Recipe detail view ─────────────────────────────────────
    var selectedLocalRecipe   by remember { mutableStateOf<Recipe?>(null) }
    var selectedMealieSlug    by remember { mutableStateOf<String?>(null) }
    var selectedMealieDetail  by remember { mutableStateOf<MealieApi.MealieRecipeDetail?>(null) }
    var mealieDetailLoading   by remember { mutableStateOf(false) }
    var mealieDetailError     by remember { mutableStateOf<String?>(null) }

    // ── People for grocery list assignment ─────────────────────
    var people by remember { mutableStateOf<List<com.openlight.cal.data.model.Person>>(emptyList()) }
    LaunchedEffect(Unit) {
        people = withContext(Dispatchers.IO) { database.personDao().getAll() }
    }

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

    // ── Fetch Mealie detail on demand ──────────────────────────
    LaunchedEffect(selectedMealieSlug) {
        val slug = selectedMealieSlug ?: return@LaunchedEffect
        mealieDetailLoading = true
        mealieDetailError = null
        selectedMealieDetail = null
        withContext(Dispatchers.IO) {
            try {
                val api = MealieApi(mealieUrl, mealieToken)
                selectedMealieDetail = api.getRecipeDetail(slug)
                if (selectedMealieDetail == null) {
                    mealieDetailError = "Could not load recipe details"
                }
            } catch (e: Exception) {
                mealieDetailError = "Failed: ${e.message}"
            }
        }
        mealieDetailLoading = false
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
                    editRecipeTarget = null
                    showEditDialog = true
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

                val q = searchQuery.trim().lowercase()
                val localFiltered = if (q.isBlank()) localRecipes
                    else localRecipes.filter { r ->
                        r.name.contains(q, ignoreCase = true) ||
                        r.tags.contains(q, ignoreCase = true) ||
                        r.description.contains(q, ignoreCase = true) ||
                        r.notes.contains(q, ignoreCase = true) ||
                        r.ingredientsJson.contains(q, ignoreCase = true) ||
                        r.instructionsJson.contains(q, ignoreCase = true)
                    }

                val syncedFiltered = if (q.isBlank()) mealieRecipes
                    else mealieRecipes.filter { r ->
                        r.name.contains(q, ignoreCase = true) ||
                        r.description.contains(q, ignoreCase = true) ||
                        r.category.contains(q, ignoreCase = true)
                    }

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
                                    onClick = { selectedLocalRecipe = recipe },
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
                                    onClick = { selectedMealieSlug = recipe.slug },
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

    // ── Recipe Add/Edit dialog ──────────────────────────────
    if (showEditDialog) {
        RecipeEditDialog(
            existing   = editRecipeTarget,
            onSave     = { recipe ->
                scope.launch { recipeRepository.saveLocal(recipe) }
                showEditDialog = false
                editRecipeTarget = null
            },
            onDismiss  = {
                showEditDialog = false
                editRecipeTarget = null
            }
        )
    }

    // ── Grocery list helper ─────────────────────────────────
    val groceryListHelper = remember { GroceryListHelper(database) }

    // ── Local recipe detail dialog ─────────────────────────────
    selectedLocalRecipe?.let { recipe ->
        val ings = remember { parseJsonList(recipe.ingredientsJson) }
        RecipeDetailDialog(
            recipe  = recipe,
            isMealie = false,
            people   = people,
            loading  = false,
            detailError = null,
            mealieDetail = null,
            onDismiss    = { selectedLocalRecipe = null },
            onEdit       = {
                editRecipeTarget = recipe
                showEditDialog = true
                selectedLocalRecipe = null
            },
            onDelete     = {
                scope.launch { recipeRepository.delete(recipe) }
                selectedLocalRecipe = null
            },
            onAddToMealPlan = { onAddToMealPlan(recipe.name) },
            onAddToGroceryList = { personId ->
                scope.launch {
                    groceryListHelper.addIngredientsToGroceryList(
                        recipeName = recipe.name,
                        ingredients = ings,
                        personId = personId
                    )
                }
            },
            mealieConfigured = mealieUrl.isNotBlank(),
            onPublishToMealie = {
                val result = recipeRepository.pushToMealie(
                    recipe, mealieUrl, mealieToken
                )
                result.onSuccess { updated ->
                    selectedLocalRecipe = updated
                }
            }
        )
    }

    // ── Mealie recipe detail dialog ────────────────────────────
    if (selectedMealieSlug != null) {
        val ings = remember(selectedMealieDetail) {
            selectedMealieDetail?.ingredients ?: emptyList()
        }
        RecipeDetailDialog(
            recipe  = null,
            isMealie = true,
            people   = people,
            loading  = mealieDetailLoading,
            detailError = mealieDetailError,
            mealieDetail = selectedMealieDetail,
            onDismiss    = {
                selectedMealieSlug = null
                selectedMealieDetail = null
                mealieDetailError = null
            },
            onEdit       = null,
            onDelete     = null,
            onAddToMealPlan = {
                selectedMealieDetail?.let { onAddToMealPlan(it.name) }
            },
            onAddToGroceryList = { personId ->
                scope.launch {
                    groceryListHelper.addIngredientsToGroceryList(
                        recipeName = selectedMealieDetail?.name ?: "Recipe",
                        ingredients = ings,
                        personId = personId
                    )
                }
            },
            mealieConfigured = false,
            onPublishToMealie = null
        )
    }
}

// ─────────────────────────────────────────────────────────────
// RecipeDetailDialog — shows full recipe info
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecipeDetailDialog(
    recipe: Recipe?,
    isMealie: Boolean,
    people: List<com.openlight.cal.data.model.Person> = emptyList(),
    loading: Boolean,
    detailError: String?,
    mealieDetail: MealieApi.MealieRecipeDetail?,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    onAddToMealPlan: () -> Unit,
    onAddToGroceryList: (personId: Long) -> Unit = {},
    mealieConfigured: Boolean = false,
    onPublishToMealie: (suspend () -> Unit)? = null
) {
    val name = recipe?.name ?: mealieDetail?.name ?: ""
    val description = recipe?.description ?: mealieDetail?.description ?: ""
    val sourceUrl = recipe?.sourceUrl ?: ""
    val tags = recipe?.tags ?: ""

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var publishBusy by remember { mutableStateOf(false) }
    var publishError by remember { mutableStateOf<String?>(null) }
    val dialogScope = rememberCoroutineScope()

    // Ingredients
    val ingredients = remember(recipe, mealieDetail) {
        when {
            recipe != null -> parseJsonList(recipe.ingredientsJson)
            mealieDetail != null -> mealieDetail.ingredients
            else -> emptyList()
        }
    }

    // Instructions
    val instructions = remember(recipe, mealieDetail) {
        when {
            recipe != null -> parseJsonList(recipe.instructionsJson)
            mealieDetail != null -> mealieDetail.instructions
            else -> emptyList()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            // Loading / Error
            if (loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }
            if (detailError != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CloudOff, null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(detailError, color = MaterialTheme.colorScheme.error)
                    }
                }
                return@Column
            }

            // ── Header ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold)
                    if (description.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(description, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (isMealie) {
                        Spacer(Modifier.height(2.dp))
                        Text("via Mealie", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
                // Delete / Edit action buttons
                Row {
                    if (onEdit != null) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Filled.Edit, "Edit recipe",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (onDelete != null) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Filled.Delete, "Delete recipe",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // ── Metadata chips ─────────────────────────────────
            var hasMeta = false
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recipe?.let { r ->
                    if (r.prepTimeMinutes > 0) {
                        MetaChip(Icons.Filled.Timer, "Prep: ${r.prepTimeMinutes}m")
                        hasMeta = true
                    }
                    if (r.cookTimeMinutes > 0) {
                        MetaChip(Icons.Filled.LocalFireDepartment, "Cook: ${r.cookTimeMinutes}m")
                        hasMeta = true
                    }
                    if (r.servings > 0) {
                        MetaChip(Icons.Filled.People, "${r.servings} servings")
                        hasMeta = true
                    }
                    if (r.rating > 0) {
                        MetaChip(Icons.Filled.Star, "★".repeat(r.rating))
                        hasMeta = true
                    }
                    if (r.isFavorite) {
                        MetaChip(Icons.Filled.Favorite, "Favorite")
                        hasMeta = true
                    }
                }
                mealieDetail?.let { md ->
                    if (md.recipeYield.isNotBlank()) {
                        MetaChip(Icons.Filled.People, md.recipeYield)
                        hasMeta = true
                    }
                    if (md.totalTime.isNotBlank()) {
                        MetaChip(
                            if (md.totalTime.contains("hour", true) || md.totalTime.contains("min", true))
                                Icons.Filled.Timer else Icons.Filled.Schedule,
                            md.totalTime
                        )
                        hasMeta = true
                    }
                }
            }
            if (hasMeta) Spacer(Modifier.height(12.dp))

            // ── Tags ───────────────────────────────────────────
            if (tags.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tags.split(",").mapNotNull { it.trim().ifBlank { null } }.forEach { tag ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(tag,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Source URL ─────────────────────────────────────
            if (sourceUrl.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Link, null, Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(sourceUrl, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Ingredients ────────────────────────────────────
            if (ingredients.isNotEmpty()) {
                Text("Ingredients", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                ingredients.forEach { ingredient ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("•  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(ingredient, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else if (!loading && !isMealie) {
                Text("No ingredients listed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
            }

            // ── Instructions ───────────────────────────────────
            if (instructions.isNotEmpty()) {
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Instructions", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                instructions.forEachIndexed { i, step ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${i + 1}", fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(step, style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── Notes (local only) ────────────────────────────
            recipe?.let { r ->
                if (r.notes.isNotBlank()) {
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text("Notes", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(r.notes, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                }
            }

            // ── Grocery list person picker ─────────────────────
            var groceryPersonId by remember { mutableStateOf(0L) }
            if (ingredients.isNotEmpty() && people.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Shop for this recipe", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        com.openlight.cal.ui.components.PersonChip(
                            person   = person,
                            selected = person.id == groceryPersonId,
                            onClick  = {
                                groceryPersonId = if (groceryPersonId == person.id) 0L else person.id
                            }
                        )
                    }
                }
            }

            // ── Action buttons ─────────────────────────────────
            Spacer(Modifier.height(12.dp))
            if (ingredients.isNotEmpty()) {
                OutlinedButton(
                    onClick = { onAddToGroceryList(groceryPersonId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.ShoppingCart, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Grocery List")
                }
                Spacer(Modifier.height(8.dp))
            }

            // ── Publish to Mealie (local recipes only) ────────
            if (recipe != null && recipe.mealieId.isBlank() && mealieConfigured && onPublishToMealie != null) {
                if (publishError != null) {
                    Text(publishError!!, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                }
                OutlinedButton(
                    onClick = {
                        publishError = null
                        publishBusy = true
                        dialogScope.launch {
                            try {
                                onPublishToMealie()
                            } catch (e: Exception) {
                                publishError = "Publish failed: ${e.message}"
                            }
                            publishBusy = false
                        }
                    },
                    enabled = !publishBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (publishBusy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.CloudUpload, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(if (publishBusy) "Publishing…" else "Publish to Mealie")
                }
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = { onAddToMealPlan(); onDismiss() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add to Meal Plan")
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Filled.Delete, null,
                tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete recipe?") },
            text = {
                Text("This cannot be undone. Local recipes are deleted permanently.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete?.invoke()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ── Small metadata pill ──────────────────────────────────────
@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

// ── Parse JSON array string to list ──────────────────────────
private fun parseJsonList(json: String): List<String> {
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Exception) {
        emptyList()
    }
}

// ── Grocery list helper ──────────────────────────────────────
private class GroceryListHelper(private val db: com.openlight.cal.data.db.AppDatabase) {
    suspend fun addIngredientsToGroceryList(
        recipeName: String,
        ingredients: List<String>,
        personId: Long = 0L
    ) {
        if (ingredients.isEmpty()) return
        val dao = db.checkListDao()

        // Find or create "Grocery List" checklist
        var groceryList = dao.getAll().find { it.name == "Grocery List" }
        if (groceryList == null) {
            val listId = dao.insertList(
                com.openlight.cal.data.model.CheckList(
                    name = "Grocery List",
                    colorHex = "#4CAF50"
                )
            )
            groceryList = com.openlight.cal.data.model.CheckList(
                id = listId, name = "Grocery List", colorHex = "#4CAF50"
            )
        }

        // Add each ingredient as a checklist item
        var sortOrder = 0
        for (ingredient in ingredients) {
            if (ingredient.isBlank()) continue
            dao.insertItem(
                com.openlight.cal.data.model.CheckListItem(
                    listId = groceryList.id,
                    text = ingredient.trim(),
                    sortOrder = sortOrder++
                )
            )
        }

        // Create a task: "Go shopping for [recipe name]"
        val tomorrow = java.time.LocalDate.now().plusDays(1)
        val taskDao = db.taskDao()
        taskDao.insert(
            com.openlight.cal.data.model.Task(
                title = "Go shopping for $recipeName",
                description = "Ingredients added to Grocery List",
                dueMs = tomorrow.atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli(),
                priority = com.openlight.cal.data.model.TaskPriority.NORMAL,
                isLocalOnly = true
            )
        )
    }
}

// ── Recipe Add/Edit Dialog ───────────────────────────────────
@Composable
private fun RecipeEditDialog(
    existing: Recipe?,
    onSave: (Recipe) -> Unit,
    onDismiss: () -> Unit
) {
    val isAdd = existing == null
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var description by remember { mutableStateOf(existing?.description ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    var prepTimeMinutes by remember { mutableIntStateOf(existing?.prepTimeMinutes ?: 0) }
    var cookTimeMinutes by remember { mutableIntStateOf(existing?.cookTimeMinutes ?: 0) }
    var servings by remember { mutableIntStateOf(existing?.servings ?: 0) }
    var tags by remember { mutableStateOf(existing?.tags ?: "") }
    var sourceUrl by remember { mutableStateOf(existing?.sourceUrl ?: "") }
    var rating by remember { mutableIntStateOf(existing?.rating ?: 0) }
    var isFavorite by remember { mutableStateOf(existing?.isFavorite ?: false) }

    // Ingredients / Instructions as editable text
    var ingredientsText by remember {
        mutableStateOf(
            if (existing != null) parseJsonList(existing.ingredientsJson).joinToString("\n")
            else ""
        )
    }
    var instructionsText by remember {
        mutableStateOf(
            if (existing != null) parseJsonList(existing.instructionsJson).joinToString("\n")
            else ""
        )
    }

    var nameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAdd) "Add Recipe" else "Edit Recipe") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("Name *") },
                    isError = nameError,
                    supportingText = if (nameError) {{ Text("Name is required") }} else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ingredientsText,
                    onValueChange = { ingredientsText = it },
                    label = { Text("Ingredients (one per line)") },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = instructionsText,
                    onValueChange = { instructionsText = it },
                    label = { Text("Instructions (one step per line)") },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = if (prepTimeMinutes > 0) prepTimeMinutes.toString() else "",
                        onValueChange = { prepTimeMinutes = it.toIntOrNull() ?: 0 },
                        label = { Text("Prep time (min)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = if (cookTimeMinutes > 0) cookTimeMinutes.toString() else "",
                        onValueChange = { cookTimeMinutes = it.toIntOrNull() ?: 0 },
                        label = { Text("Cook time (min)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Servings: ", style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = { if (servings > 0) servings-- }) {
                        Icon(Icons.Filled.Remove, null)
                    }
                    Text("$servings", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { servings++ }) {
                        Icon(Icons.Filled.Add, null)
                    }
                }
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it },
                    label = { Text("Source URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                // Rating
                Text("Rating", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 1..5) {
                        IconButton(
                            onClick = { rating = if (rating == i) 0 else i },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                null,
                                tint = if (i <= rating) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // Favorite toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isFavorite, onCheckedChange = { isFavorite = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Favorite", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    nameError = true
                    return@TextButton
                }
                val ingsJson = JSONArray(ingredientsText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }).toString()
                val instJson = JSONArray(instructionsText.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }).toString()
                onSave(
                    Recipe(
                        id = existing?.id ?: 0,
                        name = name.trim(),
                        description = description.trim(),
                        notes = notes.trim(),
                        ingredientsJson = ingsJson,
                        instructionsJson = instJson,
                        prepTimeMinutes = prepTimeMinutes,
                        cookTimeMinutes = cookTimeMinutes,
                        servings = servings,
                        tags = tags.trim(),
                        sourceUrl = sourceUrl.trim(),
                        rating = rating,
                        isFavorite = isFavorite
                    )
                )
            }) {
                Text(if (isAdd) "Add" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
