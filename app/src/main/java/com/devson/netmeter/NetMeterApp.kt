package com.devson.netmeter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.devson.netmeter.core.navigation.AppNavigation
import com.devson.netmeter.core.navigation.Screen
import com.devson.netmeter.core.permission.UsagePermissionViewModel
import com.devson.netmeter.features.settings.SettingsViewModel

@Composable
fun NetMeterApp(
    settingsViewModel: SettingsViewModel,
    permissionViewModel: UsagePermissionViewModel,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Dashboard.route
    val currentTitle = when {
        currentRoute == Screen.AppInspector.route -> Screen.AppInspector.title
        currentRoute.startsWith("app_inspector_detail") -> Screen.AppInspectorDetail.title
        else -> (Screen.bottomNavScreens.find { it.route == currentRoute } ?: Screen.Dashboard).title
    }
    val canNavigateBack = currentRoute == Screen.AppInspector.route || currentRoute.startsWith("app_inspector_detail")

    Scaffold(
        topBar = {
            val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = statusBarPadding)
                        .height(44.dp)
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (canNavigateBack) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Navigate back",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        bottomBar = {
            val bottomInsets = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        thickness = 0.8.dp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = bottomInsets)
                            .height(52.dp)
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Screen.bottomNavScreens.forEach { screen ->
                            val selected = currentRoute == screen.route
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(height = 24.dp, width = 48.dp)
                                        .then(
                                            if (selected) {
                                                Modifier.background(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                            } else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title,
                                        modifier = Modifier.size(19.dp),
                                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
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
