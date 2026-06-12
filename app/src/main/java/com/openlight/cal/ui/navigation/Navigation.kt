package com.openlight.cal.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowWidthSizeClass
import com.openlight.cal.HearthboardApp
import com.openlight.cal.data.model.CalendarEvent
import com.openlight.cal.data.model.MealPlan
import com.openlight.cal.data.model.MealSlot
import com.openlight.cal.ui.components.ConnectivityBanner
import com.openlight.cal.ui.screens.calendar.CalendarScreen
import com.openlight.cal.ui.screens.calendar.EntryMethod
import com.openlight.cal.ui.screens.calendar.EventEditDialog
import com.openlight.cal.ui.screens.calendar.EventEntryMethodDialog
import androidx.compose.ui.platform.LocalContext
import com.openlight.cal.ui.screens.chores.ChoresScreen
import com.openlight.cal.ui.screens.home.HomeScreen
import com.openlight.cal.ui.screens.lists.ListsScreen
import com.openlight.cal.ui.screens.meals.MealsScreen
import com.openlight.cal.ui.screens.people.PeopleScreen
import com.openlight.cal.ui.screens.photos.PhotosScreen
import com.openlight.cal.ui.screens.recipes.RecipesScreen
import com.openlight.cal.ui.screens.rewards.RewardsScreen
import com.openlight.cal.ui.screens.settings.SettingsScreen
import com.openlight.cal.ui.screens.setup.SetupScreen
import com.openlight.cal.ui.screens.sleep.SleepScreen
import com.openlight.cal.ui.screens.tasks.TasksScreen
import com.openlight.cal.ui.viewmodel.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Screen definitions — All Hearthboard features, Skylight visual style
// Primary rail (top to bottom): Calendar → Lists → Tasks → Chores → Rewards → Meals → Recipes → Photos → Sleep
// Divider
// Settings
// Bottom tabs (compact): Calendar, Lists, Tasks, Chores
// More sheet: Rewards, Meals, Recipes, Photos, Sleep, Settings
// ─────────────────────────────────────────────────────────────
sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home     : Screen("home",     "Home",     Icons.Filled.Home,            Icons.Outlined.Home)
    object Calendar : Screen("calendar", "Calendar", Icons.Filled.CalendarMonth,   Icons.Outlined.CalendarMonth)
    object Tasks    : Screen("tasks",    "Tasks",    Icons.Filled.CheckCircle,      Icons.Outlined.CheckCircle)
    object Rewards  : Screen("rewards",  "Rewards",  Icons.Filled.Star,             Icons.Outlined.Star)
    object Meals    : Screen("meals",    "Meals",    Icons.Filled.Restaurant,       Icons.Outlined.Restaurant)
    object Photos   : Screen("photos",   "Photos",   Icons.Filled.PhotoLibrary,     Icons.Outlined.PhotoLibrary)
    object Lists    : Screen("lists",    "Lists",    Icons.AutoMirrored.Filled.List,             Icons.AutoMirrored.Outlined.List)
    object Sleep    : Screen("sleep",    "Sleep",    Icons.Filled.Bedtime,          Icons.Outlined.Bedtime)
    object People   : Screen("people",   "People",   Icons.Filled.Group,            Icons.Outlined.Group)
    object Recipes  : Screen("recipes",  "Recipes",  Icons.AutoMirrored.Filled.MenuBook,         Icons.AutoMirrored.Outlined.MenuBook)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings,         Icons.Outlined.Settings)
    // Chores uses distinct icon from Tasks (not checkmark)
    object Chores   : Screen("chores",   "Chores",   Icons.Filled.CleaningServices,     Icons.Outlined.CleaningServices)

    companion object {
        // Primary items — all features in user-specified order, Skylight visual style
        val primary = listOf(Calendar, Lists, Tasks, Chores, Rewards, Meals, Recipes, Photos, Sleep)

        // Below divider in the rail: ONLY Settings (People moved to Settings screen)
        val secondary = listOf(Settings)

        // Bottom nav items (compact/portrait) — first 4 visible, rest in "More"
        val bottomTabs = listOf(Calendar, Lists, Tasks, Chores)

        // Items in the "More" bottom sheet on compact screens
        val moreItems = listOf(Rewards, Meals, Recipes, Photos, Sleep, Settings)

        val all = primary + secondary
    }
}

// ── RAIL CONSTANTS (Skylight Calendar 2 spec) ────────────────────
private val RailWidthCompact   = 56.dp
private val RailWidthExpanded  = 80.dp

// Colors — using theme for dark mode support
// Selected: White rounded pill, grayish rail background
private val RailBgColor        = Color(0xFFF5F5F5)  // Grayish rail bg
private val SelectedPillColor  = Color.White        // White pill for active
private val UnselectedTint     = Color(0xFF757575)  // Gray for inactive
private val UnselectedLabel    = Color(0xFF757575)
private val SelectedLabel      = Color(0xFF1F1F1F)  // Dark for active
private val RailDividerColor   = Color(0xFFEEEEEE)  // Right edge divider

// ─────────────────────────────────────────────────────────────
// Main Navigation Host
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HearthboardNavHost(app: HearthboardApp) {
    val navController = rememberNavController()
    val navBackStack  by navController.currentBackStackEntryAsState()
    val currentDest   = navBackStack?.destination

    // ── ViewModels ────────────────────────────────────────────
    val calVm: CalendarViewModel  = viewModel()
    val taskVm: TaskViewModel     = viewModel()
    val personVm: PersonViewModel = viewModel()
    val settingsVm: SettingsViewModel = viewModel()

    val people by personVm.people.collectAsState()
    val accounts by settingsVm.accounts.collectAsState()
    val familyName by app.preferences.familyName.collectAsStateWithLifecycle(initialValue = "Family")
    val tempUnit by app.preferences.tempUnit.collectAsStateWithLifecycle(initialValue = "F")
    val showAddEvent by calVm.showAddEvent.collectAsState()
    val editEvent    by calVm.editEvent.collectAsState()
    val selectedDate by calVm.selectedDate.collectAsState()

    // Admin person for initial (first non-default person, or first person)
    // Also used for "The [LastName] Family" format
    val adminPerson = remember(people) {
        people.firstOrNull { !it.isDefault } ?: people.firstOrNull()
    }
    val adminInitial = adminPerson?.name?.firstOrNull()?.uppercase() ?: "?"
    val adminLastName = adminPerson?.name?.split(" ")?.lastOrNull() ?: "Family"
    val displayFamilyName = "The $adminLastName Family"
    // ── Setup check: show onboarding on first launch ──────────
    val setupDone by app.preferences.setupDone.collectAsStateWithLifecycle(initialValue = false)
    if (!setupDone) {
        SetupScreen(
            app        = app,
            onComplete = { /* NavHost will recompose and show main UI */ }
        )
        return
    }

    // ── Adaptive layout ───────────────────────────────────────
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val isCompact   = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.COMPACT

    // "More" sheet state — used by compact mode for overflow items
    var showMore by remember { mutableStateOf(false) }

    // ── Navigation helper ─────────────────────────────────────
    val navScope = rememberCoroutineScope()
    fun navigateTo(screen: Screen) {
        navController.navigate(screen.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState    = true
        }
    }

    // ── NavHost content (routes) ──────────────────────────────
    @Composable
    fun MainNav(mod: Modifier) {
        NavHost(
            navController    = navController,
            startDestination = Screen.Calendar.route,
            modifier         = mod
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    app         = app,
                    calVm       = calVm,
                    taskVm      = taskVm,
                    people      = people,
                    onDayClick  = { calVm.setSelectedDate(it) },
                    onEventClick = { calVm.editEvent(it) },
                    onAddEvent  = { calVm.showAddEvent() }
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    viewModel     = calVm,
                    people        = people,
                    onAddEvent    = { calVm.showAddEvent() },
                    familyName    = displayFamilyName,
                    tempUnit      = tempUnit,
                    adminInitial  = adminInitial
                )
            }
            composable(Screen.Tasks.route) {
                TasksScreen(viewModel = taskVm)
            }
            composable(Screen.Chores.route) {
                ChoresScreen(
                    database    = app.database,
                    people      = people,
                    onComplete  = { task -> taskVm.saveTask(task) },
                    onSaveChore = { task -> taskVm.saveTask(task) },
                    onDeleteChore = { task -> taskVm.deleteTask(task) }
                )
            }
            composable(Screen.Photos.route) {
                PhotosScreen()
            }
            composable(Screen.Sleep.route) {
                SleepScreen()
            }
            composable(Screen.Lists.route) {
                ListsScreen(database = app.database)
            }
            composable(Screen.Meals.route) {
                MealsScreen(database = app.database)
            }
            composable(Screen.People.route) {
                PeopleScreen(viewModel = personVm, database = app.database)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = settingsVm)
            }
            composable(Screen.Recipes.route) {
                RecipesScreen(
                    database         = app.database,
                    preferences      = app.preferences,
                    recipeRepository = app.recipeRepository,
                    onAddToMealPlan  = { name ->
                        navScope.launch {
                            val today = java.time.LocalDate.now()
                            val slot = MealSlot.DINNER
                            app.database.mealPlanDao().upsert(
                                MealPlan(dateIso = today.toString(), slot = slot, title = name)
                            )
                            val start = today.atTime(18, 0)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                            val end   = today.atTime(19, 0)
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant().toEpochMilli()
                            app.database.calendarEventDao().insert(
                                CalendarEvent(
                                    uid = "meal_${today}_$slot",
                                    title = name,
                                    startMs = start,
                                    endMs = end,
                                    colorHex = "#FF9800",
                                    isLocalOnly = true
                                )
                            )
                        }
                    }
                )
            }
            composable(Screen.Rewards.route) {
                RewardsScreen(preferences = app.preferences)
            }
        }
    }

    // ── Skylight-style Navigation Rail Item ─────────────────────
    // Vertical stack: Icon ABOVE Label, single-word labels
    // Active: White rounded pill full width; Inactive: Transparent
    @Composable
    fun SkylightNavItem(screen: Screen) {
        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true

        val bgColor    = if (selected) SelectedPillColor else Color.Transparent
        val tint       = if (selected) SelectedLabel else UnselectedTint
        val labelColor = if (selected) SelectedLabel else UnselectedLabel

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navigateTo(screen) }
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .background(
                    if (selected) SelectedPillColor else Color.Transparent,
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Icon (28dp)
                Icon(
                    imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                    contentDescription = screen.label,
                    tint    = tint,
                    modifier = Modifier.size(28.dp)
                )

                // Label (single word, 11sp Medium, centered)
                Text(
                    text       = screen.label,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color      = labelColor,
                    textAlign  = TextAlign.Center,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
            }
        }
    }

    // ── Layout: Bottom Nav (compact) or NavigationRail (expanded) ──
    if (isCompact) {
        // ── PORTRAIT / PHONE: Bottom Navigation Bar ──────────────
        val currentRoute = currentDest?.route

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
            topBar = { /* content uses its own headers */ },
            bottomBar = {
                NavigationBar(
                    containerColor = Color(0xFFF5F5F5),  // Grayish, not white
                    modifier = Modifier.padding(bottom = 0.dp)
                ) {
                    Screen.bottomTabs.forEach { screen ->
                        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick  = { if (screen.route != currentRoute) navigateTo(screen) },
                            icon = {
                                Icon(
                                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selected) Color.White else UnselectedTint
                                )
                            },
                            label = {
                                Text(
                                    text     = screen.label,
                                    style    = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    color = if (selected) Color.White else UnselectedLabel
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = UnselectedTint,
                                unselectedTextColor = UnselectedLabel,
                                indicatorColor    = Color.White  // White pill indicator
                            )
                        )
                    }

                    // "More" button
                    NavigationBarItem(
                        selected = showMore,
                        onClick  = { showMore = true },
                        icon = {
                            Icon(
                                Icons.Filled.MoreHoriz,
                                "More",
                                modifier = Modifier.size(24.dp),
                                tint = if (showMore) Color.White else UnselectedTint
                            )
                        },
                        label = {
                            Text(
                                "More",
                                color = if (showMore) Color.White else UnselectedLabel,
                                fontWeight = if (showMore) FontWeight.Medium else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = Color.White,
                            unselectedIconColor = UnselectedTint,
                            unselectedTextColor = UnselectedLabel,
                            indicatorColor    = Color.White
                        )
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                ConnectivityBanner()
                MainNav(Modifier.weight(1f))
            }
        }
    } else {
        // ── LANDSCAPE / TABLET: Navigation Rail (Skylight spec) ──────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // Rail: 80dp wide, grayish background, right divider
            NavigationRail(
                containerColor = RailBgColor,
                modifier       = Modifier
                    .width(RailWidthExpanded)
                    .border(width = 1.dp, color = RailDividerColor, shape = RoundedCornerShape(0.dp))
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp)
                ) {
                    // Calendar initial badge at top of rail (above Calendar item)
                    // We'll handle this inside SkylightNavItem for Calendar

                    // Primary items: Calendar, Lists, Tasks, Chores, Rewards, Meals, Recipes, Photos, Sleep
                    Screen.primary.forEachIndexed { index, screen ->
                        SkylightNavItem(screen)
                        // 16dp spacing between items
                        if (index != Screen.primary.lastIndex) {
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // Divider before Settings
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(
                        color = RailDividerColor,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(24.dp))

                    // Settings at bottom (only item below divider)
                    Screen.secondary.forEach { screen ->
                        SkylightNavItem(screen)
                    }
                }
            }

            // Content area
            Column(modifier = Modifier.weight(1f)) {
                ConnectivityBanner()
                MainNav(Modifier.weight(1f))
            }
        }
    }

    // ── "More" bottom sheet (compact mode) ──────────────────────
    if (showMore && isCompact) {
        ModalBottomSheet(onDismissRequest = { showMore = false }) {
            Column(Modifier.padding(bottom = 32.dp)) {
                Screen.moreItems.forEach { screen ->
                    val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                    ListItem(
                        headlineContent = { Text(screen.label) },
                        leadingContent  = {
                            Icon(
                                if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showMore = false
                                navigateTo(screen)
                            }
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    if (screen != Screen.moreItems.last()) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }

    // ── Entry method picker (§5.4) for new events ──────────
    var entryMethod by remember { mutableStateOf<EntryMethod?>(null) }
    val ctx = LocalContext.current

    if (showAddEvent && editEvent == null && entryMethod == null) {
        EventEntryMethodDialog(
            onSelect = { method ->
                when (method) {
                    EntryMethod.TYPE -> entryMethod = EntryMethod.TYPE
                    else -> android.widget.Toast.makeText(
                        ctx,
                        "${method.name} entry coming soon — use Type for now",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = { calVm.hideAddEvent() }
        )
    }

    // ── Global Add/Edit Event Sheet ───────────────────────────
    if ((showAddEvent && entryMethod == EntryMethod.TYPE) || editEvent != null) {
        EventEditDialog(
            event            = editEvent,
            people           = people,
            accounts         = accounts,
            preselectedDate  = selectedDate,
            onSave           = { event, accountId ->
                calVm.saveEvent(event, accountId)
            },
            onDelete         = if (editEvent != null) {{ calVm.deleteEvent(it) }} else null,
            onDismiss        = {
                calVm.hideAddEvent()
                entryMethod = null
            }
        )
    }
}