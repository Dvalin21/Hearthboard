@file:Suppress("DEPRECATION")

package com.openlight.cal.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowWidthSizeClass
import com.openlight.cal.HearthboardApp
import com.openlight.cal.ui.components.ConnectivityBanner
import com.openlight.cal.ui.screens.calendar.CalendarScreen
import com.openlight.cal.ui.screens.calendar.EventEditDialog
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────
// Screen definitions — Skylight Calendar tab order
// Per spec §1.1: Home → Calendar → Tasks → Rewards → Meals →
// Photos → Lists → Sleep → Settings
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
    object Lists    : Screen("lists",    "Lists",    Icons.Filled.List,             Icons.Outlined.List)
    object Sleep    : Screen("sleep",    "Sleep",    Icons.Filled.Bedtime,          Icons.Outlined.Bedtime)
    object People   : Screen("people",   "People",   Icons.Filled.Group,            Icons.Outlined.Group)
    object Chores   : Screen("chores",   "Chores",   Icons.Filled.TaskAlt,          Icons.Outlined.TaskAlt)
    object Recipes  : Screen("recipes",  "Recipes",  Icons.Filled.RestaurantMenu,   Icons.Outlined.RestaurantMenu)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings,         Icons.Outlined.Settings)

    companion object {
        // Primary items — shown in left nav rail on large screens
        val primary = listOf(Home, Calendar, Tasks, Rewards, Meals, Photos, Lists, Sleep)

        // Secondary items — below divider on left nav rail
        val secondary = listOf(People, Chores, Recipes)

        // Bottom nav items (compact/portrait) — first 4 visible, rest in "More"
        val bottomTabs = listOf(Home, Calendar, Tasks, Lists)

        // Items in the "More" bottom sheet on compact screens
        val moreItems = listOf(Rewards, Meals, Photos, People, Chores, Recipes, Sleep, Settings)

        val all = primary + secondary + Settings
    }
}

// ── RAIL WIDTHS ──────────────────────────────────────────────
private val RailWidthCompact   = 56.dp
private val RailWidthExpanded  = 80.dp

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
            startDestination = Screen.Home.route,
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
            composable(Screen.Photos.route) {
                PhotosScreen()
            }
            composable(Screen.Sleep.route) {
                SleepScreen()
            }
            composable(Screen.Lists.route) {
                ListsScreen(database = app.database)
            }
            composable(Screen.Chores.route) {
                ChoresScreen(
                    database = app.database,
                    people   = people,
                    onComplete = { task ->
                        CoroutineScope(Dispatchers.IO).launch {
                            app.taskRepository.setCompleted(task.id, true)
                        }
                    },
                    onSaveChore = { chore ->
                        CoroutineScope(Dispatchers.IO).launch {
                            app.taskRepository.saveTask(chore.copy(isChore = true, isLocalOnly = true))
                        }
                    },
                    onDeleteChore = { chore ->
                        CoroutineScope(Dispatchers.IO).launch {
                            app.taskRepository.deleteTask(chore)
                        }
                    }
                )
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
                    preferences      = app.preferences,
                    recipeRepository = app.recipeRepository,
                    onAddToMealPlan  = { /* TODO: add to meal planner */ }
                )
            }
            composable(Screen.Rewards.route) {
                RewardsScreen(preferences = app.preferences)
            }
        }
    }

    // ── RailItem helper ───────────────────────────────────────
    @Composable
    fun NavRailItem(
        screen: Screen,
        colors: NavigationRailItemColors = NavigationRailItemDefaults.colors()
    ) {
        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
        NavigationRailItem(
            selected = selected,
            onClick  = { navigateTo(screen) },
            icon  = {
                Icon(
                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                    contentDescription = screen.label
                )
            },
            label = {
                if (!isCompact) {
                    Text(
                        text       = screen.label,
                        style      = MaterialTheme.typography.labelSmall,
                        maxLines   = 1
                    )
                }
            },
            alwaysShowLabel = !isCompact,
            colors = colors
        )
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
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Screen.bottomTabs.forEach { screen ->
                        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick  = { if (screen.route != currentRoute) navigateTo(screen) },
                            icon = {
                                Icon(
                                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label
                                )
                            },
                            label = {
                                Text(
                                    text     = screen.label,
                                    style    = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor    = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }

                    // "More" button
                    NavigationBarItem(
                        selected = showMore,
                        onClick  = { showMore = true },
                        icon     = { Icon(Icons.Default.MoreHoriz, "More") },
                        label    = { Text("More") }
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
        // ── LANDSCAPE / TABLET: Left Navigation Rail ────────────
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier       = Modifier.width(RailWidthExpanded)
            ) {
                Spacer(Modifier.height(8.dp))

                // Primary items
                Screen.primary.forEach { screen ->
                    NavRailItem(screen = screen)
                }

                // Secondary items below divider
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                Screen.secondary.forEach { screen ->
                    NavRailItem(
                        screen = screen,
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }

                Spacer(Modifier.weight(1f))

                // Settings at bottom
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                NavRailItem(screen = Screen.Settings)
                Spacer(Modifier.height(8.dp))
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
                                contentDescription = null
                            )
                        },
                        modifier = Modifier.clickable {
                            showMore = false
                            navigateTo(screen)
                        }
                    )
                }
            }
        }
    }

    // ── Global Add/Edit Event Sheet ───────────────────────────
    if (showAddEvent || editEvent != null) {
        EventEditDialog(
            event            = editEvent,
            people           = people,
            accounts         = accounts,
            preselectedDate  = selectedDate,
            onSave           = { event, accountId ->
                calVm.saveEvent(event, accountId)
            },
            onDelete         = if (editEvent != null) {{ calVm.deleteEvent(it) }} else null,
            onDismiss        = { calVm.hideAddEvent() }
        )
    }
}
