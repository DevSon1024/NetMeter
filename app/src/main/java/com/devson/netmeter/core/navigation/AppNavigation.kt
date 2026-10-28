package com.devson.netmeter.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
            DashboardScreen(permissionViewModel = permissionViewModel)
        }
        composable(Screen.AppUsage.route) {
            AppUsageScreen(permissionViewModel = permissionViewModel)
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
        composable(Screen.SpeedTest.route) {
            SpeedTestScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen(viewModel = settingsViewModel)
        }
    }
}
