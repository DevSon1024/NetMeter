package com.devson.netmeter.features.appinspector

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.netmeter.core.domain.model.AppDetailInfo
import com.devson.netmeter.core.domain.model.AppPermission
import com.devson.netmeter.core.domain.model.AppStorageResult
import com.devson.netmeter.core.domain.model.PermissionType
import com.devson.netmeter.core.permission.UsagePermissionHelper
import com.devson.netmeter.core.util.FormatUtils

enum class PermissionFilter(val label: String) {
    ALL("All"),
    MAJOR("Major"),
    GRANTED("Granted"),
    DENIED("Denied"),
    RUNTIME("Runtime / Dangerous")
}

@Composable
fun AppDetailScreen(
    packageName: String,
    modifier: Modifier = Modifier,
    viewModel: AppInspectorViewModel = viewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(packageName) {
        viewModel.loadAppDetails(packageName)
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val detailState = uiState.detailState) {
            is AppDetailContentState.Loading, AppDetailContentState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Loading application details...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is AppDetailContentState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Unable to Inspect Application",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = detailState.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedButton(onClick = onNavigateBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Back")
                                }
                                Button(onClick = { viewModel.loadAppDetails(packageName, isRefresh = true) }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
            }

            is AppDetailContentState.Success -> {
                AppDetailContent(
                    detail = detailState.detail,
                    onNavigateBack = onNavigateBack,
                    onRefresh = { viewModel.loadAppDetails(packageName, isRefresh = true) },
                    onCopyPackage = { viewModel.copyPackageName(context, detailState.detail.packageName) },
                    onLaunchApp = { viewModel.launchApp(context, detailState.detail.packageName) },
                    onOpenAppInfo = { viewModel.openAppInfo(context, detailState.detail.packageName) },
                    onOpenPermissions = { viewModel.openPermissions(context, detailState.detail.packageName) },
                    onOpenStorage = { viewModel.openStorageSettings(context, detailState.detail.packageName) },
                    onGrantUsageAccess = { UsagePermissionHelper.openUsageAccessSettings(context) }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun AppDetailContent(
    detail: AppDetailInfo,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onCopyPackage: () -> Unit,
    onLaunchApp: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenStorage: () -> Unit,
    onGrantUsageAccess: () -> Unit
) {
    var selectedPermFilter by remember { mutableStateOf(PermissionFilter.ALL) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 0.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Header Card
        item {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        InspectorAppIcon(
                            packageName = detail.packageName,
                            modifier = Modifier.size(56.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = detail.appName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = detail.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                IconButton(
                                    onClick = onCopyPackage,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy package name",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Chips for System/User and Launchable
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(if (detail.isSystemApp) "System Application" else "User Installed") }
                        )
                        SuggestionChip(
                            onClick = {},
                            label = { Text(if (detail.isLaunchable) "Launchable" else "No Launcher Activity") }
                        )
                    }
                }
            }
        }

        // Action Buttons Row
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Android Management Actions",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Primary Action: Open App (if launchable)
                    if (detail.isLaunchable) {
                        Button(
                            onClick = onLaunchApp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Application",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Settings Actions: Adaptive layout based on screen width
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val isWide = maxWidth >= 400.dp
                        if (isWide) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onOpenAppInfo,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Settings,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "App Info",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = onOpenPermissions,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Permissions",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = onOpenStorage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Manage Storage & Cache",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        } else {
                            // Compact phone portrait: full width stacked buttons so text is NEVER cramped
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = onOpenAppInfo,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "App Info in Settings",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = onOpenPermissions,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Permission Settings",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = onOpenStorage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Storage,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Manage Storage & Cache",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // App Metadata Overview Card
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Package Metadata",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    MetadataRow("Version Name", detail.versionName ?: "Not specified")
                    MetadataRow("Version Code", detail.versionCode.toString())
                    MetadataRow("Target SDK", detail.targetSdkVersion?.let { "API $it" } ?: "Unavailable")
                    MetadataRow("Minimum SDK", detail.minSdkVersion?.let { "API $it" } ?: "Unavailable")
                    MetadataRow("First Installed", FormatUtils.formatDateTime(detail.firstInstallTime))
                    MetadataRow("Last Updated", FormatUtils.formatDateTime(detail.lastUpdateTime))
                    MetadataRow("Installer / Source", detail.installerPackageName ?: "Direct Install / System")
                }
            }
        }

        // Storage Statistics Card
        item {
            StorageStatsSection(
                storageResult = detail.storageResult,
                onOpenStorage = onOpenStorage,
                onGrantUsageAccess = onGrantUsageAccess
            )
        }

        // Permissions Section Header & Filter
        item {
            val totalPerms = detail.permissions.size
            val majorCount = detail.permissions.count { it.isMajor }
            val grantedCount = detail.permissions.count { it.isGranted }
            val runtimeCount = detail.permissions.count { it.type == PermissionType.RUNTIME }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Declared Permissions ($totalPerms)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "$majorCount major • $grantedCount granted • ${totalPerms - grantedCount} denied • $runtimeCount runtime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Permissions Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PermissionFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedPermFilter == filter,
                            onClick = { selectedPermFilter = filter },
                            label = { Text(filter.label) }
                        )
                    }
                }

                Text(
                    text = "Reports permissions declared in the manifest via PackageManager. Major device capabilities (Camera, Storage, Vibration, etc.) are highlighted at the top.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // Permissions List
        val filteredPerms = detail.permissions.filter { perm ->
            when (selectedPermFilter) {
                PermissionFilter.ALL -> true
                PermissionFilter.MAJOR -> perm.isMajor
                PermissionFilter.GRANTED -> perm.isGranted
                PermissionFilter.DENIED -> !perm.isGranted
                PermissionFilter.RUNTIME -> perm.type == PermissionType.RUNTIME
            }
        }

        if (filteredPerms.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No permissions match the selected filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(filteredPerms, key = { it.name }) { perm ->
                PermissionItemCard(permission = perm)
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StorageStatsSection(
    storageResult: AppStorageResult,
    onOpenStorage: () -> Unit,
    onGrantUsageAccess: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Storage Information",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            when (storageResult) {
                is AppStorageResult.Success -> {
                    Text(
                        text = "Total Storage: ${FormatUtils.formatBytes(storageResult.totalBytes)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    MetadataRow("Application Code (APK)", FormatUtils.formatBytes(storageResult.appBytes))
                    MetadataRow("User Data", FormatUtils.formatBytes(storageResult.dataBytes))
                    MetadataRow("Cache", FormatUtils.formatBytes(storageResult.cacheBytes))

                    Spacer(modifier = Modifier.height(4.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Cache Management Notice",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Android security prohibits third-party applications from clearing other applications' cache directly. You can manually tap Clear cache in system settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(
                                onClick = onOpenStorage,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Storage Settings to Clear Cache")
                            }
                        }
                    }
                }

                is AppStorageResult.PermissionRequired -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Usage Access Required for Storage Statistics",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = "Android requires Usage Access permission to read package storage statistics. Storage values are not fabricated.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(
                                onClick = onGrantUsageAccess,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Usage Access")
                            }
                        }
                    }
                }

                is AppStorageResult.Unavailable -> {
                    Text(
                        text = "Storage statistics unavailable: ${storageResult.reason}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItemCard(permission: AppPermission) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (permission.isMajor) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (permission.isMajor) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        } else null
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (permission.isMajor) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Major Capability",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = permission.label ?: permission.name.substringAfterLast('.'),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (permission.isGranted) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Granted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Granted",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Not Granted",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Denied",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Text(
                text = permission.name,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.outline
            )

            if (!permission.description.isNullOrBlank()) {
                Text(
                    text = permission.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = permission.type.label,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                if (permission.isMajor) {
                    SuggestionChip(
                        onClick = {},
                        label = {
                            Text(
                                text = "Major Capability",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        border = null
                    )
                }
            }
        }
    }
}
