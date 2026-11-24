package com.devson.netmeter.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.netmeter.core.navigation.Screen

/**
 * Unified application top bar for all NetMeter screens.
 * Automatically resolves page title, back navigation handler, and colors from active theme.
 */
@Composable
fun UnifiedTopBar(
    currentRoute: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    customTitle: String? = null,
    actions: @Composable (RowScope.() -> Unit)? = null
) {
    val title = customTitle ?: when {
        currentRoute == Screen.Dashboard.route -> Screen.Dashboard.title
        currentRoute == Screen.AppUsage.route -> Screen.AppUsage.title
        currentRoute == Screen.History.route -> Screen.History.title
        currentRoute == Screen.SpeedTest.route -> Screen.SpeedTest.title
        currentRoute == Screen.Settings.route -> Screen.Settings.title
        currentRoute == Screen.SettingsAppearance.route -> "Appearance & Theme"
        currentRoute == Screen.SettingsApp.route -> Screen.SettingsApp.title
        currentRoute == Screen.SettingsData.route -> Screen.SettingsData.title
        currentRoute == Screen.SettingsAbout.route -> "About NetMeter"
        currentRoute == Screen.AppInspector.route -> Screen.AppInspector.title
        currentRoute.startsWith("app_inspector_detail") -> Screen.AppInspectorDetail.title
        currentRoute == Screen.ApkAnalyzer.route -> Screen.ApkAnalyzer.title
        else -> (Screen.bottomNavScreens.find { it.route == currentRoute } ?: Screen.Dashboard).title
    }

    val canNavigateBack = currentRoute != Screen.Dashboard.route &&
            currentRoute != Screen.AppUsage.route &&
            currentRoute != Screen.SpeedTest.route &&
            currentRoute != Screen.Settings.route &&
            currentRoute != Screen.History.route

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarPadding)
                .height(48.dp)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (canNavigateBack) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate back",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = if (canNavigateBack) 48.dp else 16.dp)
            )

            if (actions != null) {
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}
