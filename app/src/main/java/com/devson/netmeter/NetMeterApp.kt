package com.devson.netmeter

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devson.netmeter.core.navigation.AppNavigation
import com.devson.netmeter.core.navigation.Screen
import com.devson.netmeter.core.permission.UsagePermissionViewModel
import com.devson.netmeter.features.settings.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetMeterApp(
    settingsViewModel: SettingsViewModel,
    permissionViewModel: UsagePermissionViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
    val currentScreen = Screen.bottomNavScreens.find { it.route == currentRoute } ?: Screen.Dashboard

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = currentScreen.title,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Screen.bottomNavScreens.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.title
                            )
                        },
                        label = {
                            Text(
                                text = screen.title,
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        AppNavigation(
            navController = navController,
            settingsViewModel = settingsViewModel,
            permissionViewModel = permissionViewModel,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
