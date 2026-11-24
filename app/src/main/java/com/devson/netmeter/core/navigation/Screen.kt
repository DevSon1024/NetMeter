package com.devson.netmeter.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
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

    data object SettingsAppearance : Screen(
        route = "settings_appearance",
        title = "Appearance",
        icon = Icons.Default.Palette
    )

    data object SettingsApp : Screen(
        route = "settings_app",
        title = "App Settings",
        icon = Icons.Default.DataUsage
    )

    data object SettingsData : Screen(
        route = "settings_data",
        title = "Data Management",
        icon = Icons.Default.Storage
    )

    data object SettingsAbout : Screen(
        route = "settings_about",
        title = "About",
        icon = Icons.Default.Info
    )

    data object AppInspector : Screen(
        route = "app_inspector",
        title = "App Inspector",
        icon = Icons.Default.Search
    )

    data object AppInspectorDetail : Screen(
        route = "app_inspector_detail/{packageName}",
        title = "App Details",
        icon = Icons.Default.Search
    ) {
        fun createRoute(packageName: String): String = "app_inspector_detail/${android.net.Uri.encode(packageName)}"
    }

    data object ApkAnalyzer : Screen(
        route = "apk_analyzer",
        title = "APK Analyzer",
        icon = Icons.Default.Analytics
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
