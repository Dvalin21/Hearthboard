@file:Suppress("DEPRECATION")

package com.openlight.cal.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
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
import androidx.window.core.layout.WindowHeightSizeClass
import androidx.window.core.layout.WindowWidthSizeClass
import com.openlight.cal.HearthboardApp
import com.openlight.cal.ui.components.ConnectivityBanner
import com.openlight.cal.ui.screens.calendar.CalendarScreen
import com.openlight.cal.ui.screens.calendar.EventEditDialog
import com.openlight.cal.ui.screens.chores.ChoresScreen
import com.openlight.cal.ui.screens.lists.ListsScreen
import com.openlight.cal.ui.screens.meals.MealsScreen
import com.openlight.cal.ui.screens.people.PeopleScreen
import com.openlight.cal.ui.screens.recipes.RecipesScreen
import com.openlight.cal.ui.screens.rewards.RewardsScreen
import com.openlight.cal.ui.screens.settings.SettingsScreen
import com.openlight.cal.ui.screens.setup.SetupScreen
import com.openlight.cal.ui.screens.tasks.TasksScreen
import com.openlight.cal.ui.viewmodel.*
import androidx.lifecycle.viewmodel.compose.viewModel

sealed class Screen(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Calendar : Screen("calendar", "Calendar",  Icons.Filled.CalendarMonth,   Icons.Outlined.CalendarMonth)
    object Tasks    : Screen("tasks",    "Tasks",     Icons.Filled.CheckCircle,      Icons.Outlined.CheckCircle)
    object Lists    : Screen("lists",    "Lists",     Icons.Filled.List,             Icons.Outlined.List)
    object Meals    : Screen("meals",    "Meals",     Icons.Filled.Restaurant,       Icons.Outlined.Restaurant)
    object People   : Screen("people",   "People",    Icons.Filled.Group,            Icons.Outlined.Group)
    object Chores   : Screen("chores",   "Chores",    Icons.Filled.TaskAlt,          Icons.Outlined.TaskAlt)
    object Recipes  : Screen("recipes",  "Recipes",   Icons.Filled.RestaurantMenu,   Icons.Outlined.RestaurantMenu)
    object Rewards  : Screen("rewards",  "Rewards",   Icons.Filled.Star,             Icons.Outlined.Star)
    object Settings : Screen("settings", "Settings",  Icons.Filled.Settings,         Icons.Outlined.Settings)

    companion object {
        // Phone bottom nav has a hard limit of 5 items before things crash
        // visually. Top-level only; everything else lives in 'More'.
        val main      = listOf(Calendar, Tasks, Lists, Meals, People)
        val secondary = listOf(Chores, Recipes, Rewards)
        val all       = main + secondary + Settings
    }
}

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
    val useNavRail   = adaptiveInfo.windowSizeClass.windowWidthSizeClass != WindowWidthSizeClass.COMPACT

    @Composable
    fun MainNav(mod: Modifier) {
        NavHost(
            navController    = navController,
            startDestination = Screen.Calendar.route,
            modifier         = mod
        ) {
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
                            // Ensure chores are always local-only with isChore=true
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
            composable("recipes") {
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

    // "More" sheet state — used by both rail and bottom-nav to expose
    // secondary destinations without overflowing the chrome.
    var showMore by remember { mutableStateOf(false) }

    // ── Large screen: NavigationRail on left ──────────────────
    // Structure restored to the pre-Claude shape after multiple
    // ill-fated attempts to add scrollability, insets, and a
    // ConnectivityBanner wrap — all of which broke the rail's layout
    // on the real tablet. Keep this dead simple: Row → Rail + MainNav
    // directly weighted. No insets here, no extra Column wrap.
    // Visual improvements that DID survive:
    //   - dividers between main/secondary/settings groups
    //   - tertiaryContainer indicator for secondary group
    //   - Rewards item in Screen.secondary
    if (useNavRail) {
        Row(modifier = Modifier.fillMaxSize()) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Spacer(Modifier.weight(1f))
                Screen.main.forEach { screen ->
                    val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                    NavigationRailItem(
                        selected = selected,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = {
                            Icon(
                                if (selected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(screen.label) },
                        alwaysShowLabel = false
                    )
                }
                // Secondary items
                Screen.secondary.forEach { screen ->
                    val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                    NavigationRailItem(
                        selected = selected,
                        onClick  = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
                        },
                        icon  = { Icon(screen.unselectedIcon, screen.label) },
                        label = { Text(screen.label) },
                        alwaysShowLabel = false,
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                }
                // Settings always at bottom
                val settingsSelected = currentDest?.hierarchy?.any { it.route == Screen.Settings.route } == true
                NavigationRailItem(
                    selected = settingsSelected,
                    onClick  = {
                        navController.navigate(Screen.Settings.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState    = true
                        }
                    },
                    icon  = {
                        Icon(
                            if (settingsSelected) Screen.Settings.selectedIcon else Screen.Settings.unselectedIcon,
                            contentDescription = Screen.Settings.label
                        )
                    },
                    label = { Text(Screen.Settings.label) },
                    alwaysShowLabel = false
                )
            }
            MainNav(Modifier.weight(1f))
        }
    }
    // ── Phone / small screen: BottomNav with capped slots ─────
    else {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    // Material guidance + UX reality: a NavigationBar takes
                    // 3–5 items. We pin Calendar/Tasks/Lists/Meals and use
                    // the 5th slot as 'More' to reach everything else.
                    val primary = Screen.main.take(4)
                    primary.forEach { screen ->
                        val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick  = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            },
                            icon  = {
                                Icon(
                                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) }
                        )
                    }
                    NavigationBarItem(
                        selected = showMore,
                        onClick  = { showMore = true },
                        icon     = { Icon(Icons.Default.Menu, contentDescription = "More") },
                        label    = { Text("More") }
                    )
                }
            }
        ) { contentPadding ->
            Box(modifier = Modifier.padding(contentPadding)) {
                Column {
                    ConnectivityBanner()
                    MainNav(Modifier.weight(1f))
                }
            }
        }
    }

    // ── 'More' bottom sheet: People + secondary + Settings ────
    if (showMore) {
        ModalBottomSheet(onDismissRequest = { showMore = false }) {
            Column(Modifier.padding(bottom = 16.dp)) {
                // People doesn't fit in the 4-slot primary nav on phones,
                // so we surface it at the top of More for one-tap access.
                val moreItems = listOf(Screen.People) + Screen.secondary + Screen.Settings
                moreItems.forEach { screen ->
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
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState    = true
                            }
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
