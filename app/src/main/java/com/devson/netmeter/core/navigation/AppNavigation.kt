package com.devson.netmeter.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.devson.netmeter.core.permission.UsagePermissionViewModel
import com.devson.netmeter.features.appusage.AppUsageScreen
import com.devson.netmeter.features.dashboard.DashboardScreen
import com.devson.netmeter.features.history.HistoryScreen
import com.devson.netmeter.features.settings.SettingsScreen
import com.devson.netmeter.features.settings.SettingsViewModel
import com.devson.netmeter.features.speedtest.SpeedTestScreen

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.devson.netmeter.features.appinspector.AppDetailScreen
import com.devson.netmeter.features.appinspector.AppInspectorScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel,
    permissionViewModel: UsagePermissionViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Dashboard.route,
        modifier = modifier
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                permissionViewModel = permissionViewModel,
                onNavigateToScreen = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
        composable(Screen.AppUsage.route) {
            AppUsageScreen(
                permissionViewModel = permissionViewModel,
                onInspectApp = { packageName ->
                    navController.navigate(Screen.AppInspectorDetail.createRoute(packageName))
                }
            )
        }
        composable(Screen.History.route) {
            HistoryScreen(permissionViewModel = permissionViewModel)
        }
        composable(Screen.SpeedTest.route) {
            SpeedTestScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateToInspector = {
                    navController.navigate(Screen.AppInspector.route)
                }
            )
        }
        composable(Screen.AppInspector.route) {
            AppInspectorScreen(
                onAppSelected = { packageName ->
                    navController.navigate(Screen.AppInspectorDetail.createRoute(packageName))
                }
            )
        }
        composable(
            route = Screen.AppInspectorDetail.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val encodedPackageName = backStackEntry.arguments?.getString("packageName").orEmpty()
            val packageName = android.net.Uri.decode(encodedPackageName)
            AppDetailScreen(
                packageName = packageName,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
