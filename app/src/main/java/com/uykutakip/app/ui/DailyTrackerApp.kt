package com.uykutakip.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.EventNote
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.outlined.Lock
import com.uykutakip.feature.agenda.AgendaRoute
import com.uykutakip.core.ui.theme.WarmAccent
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.uykutakip.app.AppContainer
import com.uykutakip.core.ui.theme.AppBackground
import com.uykutakip.core.ui.theme.AppSurface
import com.uykutakip.core.ui.theme.SleepAccent
import com.uykutakip.core.ui.theme.TextSecondary
import com.uykutakip.feature.ideas.IdeasRoute
import com.uykutakip.feature.shopping.ShoppingRoute
import com.uykutakip.feature.sleep.SleepRoute
import com.uykutakip.feature.private_tracking.PrivateTrackingRoute
import com.uykutakip.feature.stats.StatsRoute
import com.uykutakip.feature.settings.SettingsRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTrackerApp(container: AppContainer, diagnostics: com.uykutakip.app.diagnostics.DiagnosticsStore) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Sleep.route
    var showDiagnostics by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showPcSync by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showMcp by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (showMcp) com.uykutakip.app.mcp.McpAccessDialog { showMcp = false }
    val syncClient = androidx.compose.runtime.remember(container) { com.uykutakip.app.sync.PcSyncClient(container.syncSnapshotRepository) }
    androidx.compose.runtime.LaunchedEffect(currentRoute, showDiagnostics) {
        diagnostics.setScreen(if (showDiagnostics) "diagnostics" else currentRoute)
    }
    if (showDiagnostics) {
        com.uykutakip.app.diagnostics.DiagnosticsDialog(diagnostics) { showDiagnostics = false }
    }
    if (showPcSync) {
        com.uykutakip.app.sync.PcSyncDialog(syncClient) { showPcSync = false }
    }

    Scaffold(
        containerColor = AppBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (currentRoute == "settings") {
                TopAppBar(title = { Text("Ayarlar") }, navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Geri") }
                })
            } else if (currentRoute == AppDestination.Ideas.route) {
                TopAppBar(title = { Text("Fikirler") }, actions = {
                    IconButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                        Icon(Icons.Outlined.Settings, "Ayarlar")
                    }
                })
            }
        },
        floatingActionButton = {
            if (currentRoute != "settings" && currentRoute != AppDestination.Ideas.route) {
                androidx.compose.material3.FloatingActionButton(onClick = { navController.navigate("settings") { launchSingleTop = true } }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Ayarlar")
                }
            }
        },
        bottomBar = {
          if (currentRoute != "settings") {
            NavigationBar(containerColor = AppSurface) {
                AppDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = destination.accent,
                            selectedTextColor = destination.accent,
                            indicatorColor = destination.accent.copy(alpha = 0.13f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                        ),
                    )
                }
            }
          }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Sleep.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(AppDestination.Sleep.route) { SleepRoute(container.sleepRepository) }
            composable(AppDestination.Ideas.route) { IdeasRoute(container.ideasRepository) }
            composable(AppDestination.Shopping.route) { ShoppingRoute(container.shoppingRepository) }
            composable(AppDestination.Agenda.route) { AgendaRoute(container.agendaRepository, container.reminderScheduler) }
            composable(AppDestination.Private.route) { PrivateTrackingRoute(container.privateActivityRepository) }
            composable(AppDestination.Stats.route) { StatsRoute(container.sleepRepository, container.settingsRepository) }
            composable("settings") {
                SettingsRoute(container.sleepRepository, container.hydrationRepository, container.privateActivityRepository, container.settingsRepository, container.ideasRepository, container.shoppingRepository,
                    onOpenPcSync = { showPcSync = true }, onOpenDiagnostics = { showDiagnostics = true }, onOpenMcp = { showMcp = true })
            }
        }
    }
}

private enum class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val accent: Color,
) {
    Sleep("sleep", "Uyku", Icons.Outlined.Bedtime, SleepAccent),
    Ideas("ideas", "Ideas", Icons.Outlined.Lightbulb, WarmAccent),
    Shopping("shopping", "Liste", Icons.Outlined.ShoppingCart, WarmAccent),
    Agenda("agenda", "Ajanda", Icons.AutoMirrored.Outlined.EventNote, WarmAccent),
    Private("private", "Özel", Icons.Outlined.Lock, WarmAccent),
    Stats("stats", "İstatistik", Icons.Outlined.CalendarMonth, WarmAccent),
}
