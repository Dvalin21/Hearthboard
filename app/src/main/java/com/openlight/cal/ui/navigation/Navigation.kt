package com.openlight.cal.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
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
import androidx.compose.ui.text.font.FontWeight
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
// Screen definitions — Spec order for left nav rail
// Primary: Calendar → Lists → Tasks → Chores → Rewards → Meals → Recipes → Photos
// Secondary (below divider): Sleep
// Settings at bottom
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
    object Recipes  : Screen("recipes",  "Recipes",  Icons.Filled.RestaurantMenu,   Icons.Outlined.RestaurantMenu)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings,         Icons.Outlined.Settings)
    object Chores   : Screen("chores",   "Chores",   Icons.Filled.TaskAlt,          Icons.Outlined.TaskAlt)

    companion object {
        // Primary items — shown in left nav rail (specified order)
        val primary = listOf(Calendar, Lists, Tasks, Chores, Rewards, Meals, Recipes, Photos)

        // Below divider in the rail
        val secondary = listOf(Sleep)

        // Bottom nav items (compact/portrait) — first 4 visible, rest in "More"
        val bottomTabs = listOf(Calendar, Lists, Tasks, Chores)

        // Items in the "More" bottom sheet on compact screens
        val moreItems = listOf(Rewards, Meals, Recipes, Photos, Sleep, Settings)

        val all = primary + secondary + Settings
    }
}

// ── RAIL CONSTANTS ──────────────────────────────────────────
private val RailWidthCompact   = 56.dp
private val RailWidthExpanded  = 72.dp
private val SpecActivePurple   = Color(0xFF7C4DFF)
private val SpecActiveBg       = Color(0xFFF3E8FF)
private val SpecInactiveGray   = Color(0xFF9CA3AF)

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
    val showAddEvent by calVm.showAddEvent.collectAsState()
    val editEvent    by calVm.editEvent.collectAsState()
    val selectedDate by calVm.selectedDate.collectAsState()

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
                    viewModel  = calVm,
                    people     = people,
                    onAddEvent = { calVm.showAddEvent() }
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

    // ── Spec-style rail icon ──────────────────────────────────
    @Composable
    fun SpecNavIcon(screen: Screen) {
        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
        val bgColor  = if (selected) SpecActiveBg else Color.Transparent
        val tint     = if (selected) SpecActivePurple else SpecInactiveGray
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .clickable { navigateTo(screen) }
                .fillMaxWidth()
        ) {
            Icon(
                imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                contentDescription = screen.label,
                tint    = tint,
                modifier = Modifier.size(24.dp)
            )
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
                    containerColor = MaterialTheme.colorScheme.surface,
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
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            label = {
                                Text(
                                    text     = screen.label,
                                    style    = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor    = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    // "More" button
                    NavigationBarItem(
                        selected = showMore,
                        onClick  = { showMore = true },
                        icon     = { Icon(Icons.Filled.MoreHoriz, "More", modifier = Modifier.size(24.dp)) },
                        label    = { Text("More") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
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
        // ── LANDSCAPE / TABLET: Navigation Rail ──────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            NavigationRail(
                containerColor = Color.White,
                modifier       = Modifier.width(RailWidthExpanded)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    Spacer(Modifier.height(24.dp))

                    // Primary items: Calendar, Lists, Tasks, Chores, Rewards, Meals, Recipes, Photos
                    Screen.primary.forEach { screen ->
                        Spacer(Modifier.height(12.dp))
                        SpecNavIcon(screen)
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    Spacer(Modifier.height(8.dp))

                    // Sleep below divider
                    Screen.secondary.forEach { screen ->
                        Spacer(Modifier.height(12.dp))
                        SpecNavIcon(screen)
                    }
                }

                // Settings at very bottom
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.height(8.dp))
                SpecNavIcon(Screen.Settings)
                Spacer(Modifier.height(16.dp))
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
    // Editing always goes straight to the form.
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