package com.v2rayvpn.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.v2rayvpn.app.ui.screens.AboutScreen
import com.v2rayvpn.app.ui.screens.DnsScreen
import com.v2rayvpn.app.ui.screens.HomeScreen
import com.v2rayvpn.app.ui.screens.ImportScreen
import com.v2rayvpn.app.ui.screens.LogsScreen
import com.v2rayvpn.app.ui.screens.RoutingScreen
import com.v2rayvpn.app.ui.screens.ServersScreen
import com.v2rayvpn.app.ui.screens.SettingsScreen
import com.v2rayvpn.app.ui.screens.SplitTunnelScreen
import com.v2rayvpn.app.ui.screens.StatisticsScreen
import com.v2rayvpn.app.ui.screens.SubscriptionsScreen
import com.v2rayvpn.app.ui.theme.VpnColors

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("servers", "Servers", Icons.Filled.List),
    Tab("statistics", "Statistics", Icons.Filled.DateRange),
    Tab("settings", "Settings", Icons.Filled.Settings)
)

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: "home"
    val showBottomBar = tabs.any { it.route == currentRoute }

    Scaffold(
        containerColor = VpnColors.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = VpnColors.SurfaceElevated) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo("home") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = VpnColors.Purple,
                                selectedTextColor = VpnColors.Purple,
                                unselectedIconColor = VpnColors.TextTertiary,
                                unselectedTextColor = VpnColors.TextTertiary,
                                indicatorColor = VpnColors.Purple.copy(alpha = 0.14f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        AppNavHost(
            navController = navController,
            viewModel = viewModel,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    val back: () -> Unit = { navController.popBackStack() }
    val go: (String) -> Unit = { route -> navController.navigate(route) { launchSingleTop = true } }

    NavHost(navController = navController, startDestination = "home", modifier = modifier) {
        composable("home") { HomeScreen(viewModel, go) }
        composable("servers") { ServersScreen(viewModel, back, onImport = { go("import") }) }
        composable("import") { ImportScreen(viewModel, back) }
        composable("subscriptions") { SubscriptionsScreen(viewModel, back) }
        composable("statistics") { StatisticsScreen(viewModel, back) }
        composable("logs") { LogsScreen(viewModel, back) }
        composable("dns") { DnsScreen(viewModel, back) }
        composable("routing") { RoutingScreen(viewModel, back) }
        composable("split-tunnel") { SplitTunnelScreen(viewModel, back) }
        composable("settings") { SettingsScreen(viewModel, back, go) }
        composable("about") { AboutScreen(viewModel, back) }
    }
}
