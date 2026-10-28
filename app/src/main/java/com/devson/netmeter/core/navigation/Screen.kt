package com.devson.netmeter.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Top-level destinations in the NetMeter application.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Dashboard : Screen(
        route = "dashboard",
        title = "Dashboard",
        icon = Icons.Default.Speed
    )

    data object AppUsage : Screen(
        route = "app_usage",
        title = "App Usage",
        icon = Icons.Default.DataUsage
    )

    data object History : Screen(
        route = "history",
        title = "History",
        icon = Icons.Default.History
    )

    data object SpeedTest : Screen(
        route = "speed_test",
        title = "Speed Test",
        icon = Icons.Default.NetworkCheck
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        icon = Icons.Default.Settings
    )

    companion object {
        val bottomNavScreens: List<Screen>
            get() = listOf(
                Dashboard,
                AppUsage,
                History,
                SpeedTest,
                Settings
            )
    }
}
