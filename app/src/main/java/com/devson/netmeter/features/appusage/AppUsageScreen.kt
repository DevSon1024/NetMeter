package com.devson.netmeter.features.appusage

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.netmeter.core.domain.model.NetworkType
import com.devson.netmeter.core.permission.UsagePermissionStatus
import com.devson.netmeter.core.permission.UsagePermissionViewModel
import com.devson.netmeter.core.ui.UsagePermissionSection
import com.devson.netmeter.core.util.AppUsagePeriod

@Composable
fun AppUsageScreen(
    modifier: Modifier = Modifier,
    viewModel: AppUsageViewModel = viewModel(),
    permissionViewModel: UsagePermissionViewModel = viewModel(),
    onInspectApp: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionStatus by permissionViewModel.permissionStatus.collectAsStateWithLifecycle()
    var unresolvableItemForDialog by remember { mutableStateOf<AppUsageDisplayItem?>(null) }

    LaunchedEffect(permissionStatus) {
        if (permissionStatus == UsagePermissionStatus.Granted) {
            viewModel.loadAppUsage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 0.dp)
    ) {
        if (permissionStatus != UsagePermissionStatus.Granted) {
            UsagePermissionSection(viewModel = permissionViewModel)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Search Bar (Curvy Cylinder design)
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "Search apps or packages...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.updateSearchQuery("") },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(percent = 50),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            ),
            textStyle = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Three Dropdown Filters: Time Period, Network Type, Data Metric
        AppUsageFilterSection(
            selectedPeriod = uiState.selectedPeriod,
            onPeriodSelected = { viewModel.selectPeriod(it) },
            selectedNetwork = uiState.selectedNetworkType,
            onNetworkSelected = { viewModel.selectNetworkType(it) },
            selectedMetric = uiState.sortOrder,
            onMetricSelected = { viewModel.setSortOrder(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Main Content Area based on Content State
        when (val state = uiState.contentState) {
            is AppUsageContentState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Querying network statistics...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is AppUsageContentState.PermissionRequired -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Usage Access Required",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "To monitor per-application network data, grant Usage Access permission in system Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { permissionViewModel.openExplanationDialog() }
                            ) {
                                Text("Grant Permission")
                            }
                        }
                    }
                }
            }

            is AppUsageContentState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No Usage Data Recorded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotEmpty())
                                "No applications match '${uiState.searchQuery}'"
                            else
                                "No network traffic recorded for ${uiState.selectedPeriod.label.lowercase()} on selected network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            is AppUsageContentState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = "Error Loading Statistics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.loadAppUsage() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text("Retry Query")
                            }
                        }
                    }
                }
            }

            is AppUsageContentState.Success -> {
                // Header Total Summary Card
                val (primaryHeaderTitle, primaryHeaderValue) = when (uiState.sortOrder) {
                    AppUsageSortOrder.DOWNLOAD_DESC -> Pair("Total Download (${state.appCount} apps)", state.totalRxFormatted)
                    AppUsageSortOrder.UPLOAD_DESC -> Pair("Total Upload (${state.appCount} apps)", state.totalTxFormatted)
                    else -> Pair("Total Traffic (${state.appCount} apps)", state.totalFormatted)
                }

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = primaryHeaderTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = primaryHeaderValue,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = "Dl: ${state.totalRxFormatted}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text(
                                    text = "Up: ${state.totalTxFormatted}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (uiState.sortOrder != AppUsageSortOrder.TOTAL_DESC) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SwapVert,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.outline
                                    )
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(
                                        text = "Total: ${state.totalFormatted}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // App Usage List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.items, key = { it.uid }) { app ->
                        AppUsageItemCard(
                            app = app,
                            metric = uiState.sortOrder,
                            onInspectApp = onInspectApp,
                            onShowTrafficInfo = { unresolvableItemForDialog = it }
                        )
                    }
                }
            }
        }

        if (unresolvableItemForDialog != null) {
            val item = unresolvableItemForDialog!!
            AlertDialog(
                onDismissRequest = { unresolvableItemForDialog = null },
                icon = {
                    Icon(
                        imageVector = if (item.isSystemApp) Icons.Default.Android else Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val description = when (item.uid) {
                            0 -> "Android OS Kernel traffic includes low-level network routing, firewall rules, and core OS socket communication."
                            -4 -> "Network traffic consumed by applications that were uninstalled from this device during the selected time period."
                            -5 -> "Data used while sharing your network connection via Wi-Fi hotspot, USB, or Bluetooth tethering."
                            in 0 until 10000 -> "Internal Android system daemon or service (UID ${item.uid}) that runs without a standalone user application."
                            else -> "Data consumed by an application or process (UID ${item.uid}) that has been uninstalled or has restricted package access."
                        }
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total Traffic", style = MaterialTheme.typography.labelMedium)
                            Text(item.totalFormatted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Downloaded", style = MaterialTheme.typography.labelMedium)
                            Text(item.rxFormatted, style = MaterialTheme.typography.labelMedium)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Uploaded", style = MaterialTheme.typography.labelMedium)
                            Text(item.txFormatted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { unresolvableItemForDialog = null }) {
                        Text("Close")
                    }
                }
            )
        }
    }
}

@Composable
private fun AppUsageItemCard(
    app: AppUsageDisplayItem,
    metric: AppUsageSortOrder,
    onInspectApp: (String) -> Unit,
    onShowTrafficInfo: (AppUsageDisplayItem) -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (app.packageName != null) {
                    onInspectApp(app.packageName)
                } else {
                    onShowTrafficInfo(app)
                }
            },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 1.dp,
            pressedElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIcon(
                    packageName = app.packageName,
                    isSystemApp = app.isSystemApp,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (app.sharedPackages.size > 1) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Shared UID",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Text(
                                text = "Shared UID (${app.sharedPackages.size} pkgs)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        } else if (app.packageName != null) {
                            Text(
                                text = app.packageName,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                text = "UID ${app.uid}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(horizontalAlignment = Alignment.End) {
                    val primaryValue = when (metric) {
                        AppUsageSortOrder.DOWNLOAD_DESC -> app.rxFormatted
                        AppUsageSortOrder.UPLOAD_DESC -> app.txFormatted
                        else -> app.totalFormatted
                    }

                    Text(
                        text = primaryValue,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = when (metric) {
                            AppUsageSortOrder.DOWNLOAD_DESC -> MaterialTheme.colorScheme.primary
                            AppUsageSortOrder.UPLOAD_DESC -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when (metric) {
                            AppUsageSortOrder.DOWNLOAD_DESC -> {
                                Text(
                                    text = "Total: ${app.totalFormatted} • ↑ ${app.txFormatted}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            AppUsageSortOrder.UPLOAD_DESC -> {
                                Text(
                                    text = "Total: ${app.totalFormatted} • ↓ ${app.rxFormatted}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            else -> {
                                Text(
                                    text = "↓ ${app.rxFormatted}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "↑ ${app.txFormatted}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open details",
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { app.percentageOfTotal },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

@Composable
private fun AppIcon(
    packageName: String?,
    isSystemApp: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageBitmap = remember(packageName) {
        if (packageName != null) {
            try {
                val drawable = context.packageManager.getApplicationIcon(packageName)
                val width = drawable.intrinsicWidth.coerceAtLeast(48).coerceAtMost(128)
                val height = drawable.intrinsicHeight.coerceAtLeast(48).coerceAtMost(128)
                val bitmap = createBitmap(width, height)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Icon(
                    imageVector = if (isSystemApp) Icons.Default.Android else Icons.Default.Apps,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun AppUsageFilterSection(
    selectedPeriod: AppUsagePeriod,
    onPeriodSelected: (AppUsagePeriod) -> Unit,
    selectedNetwork: NetworkType,
    onNetworkSelected: (NetworkType) -> Unit,
    selectedMetric: AppUsageSortOrder,
    onMetricSelected: (AppUsageSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    val networkOptions = listOf(NetworkType.ALL, NetworkType.MOBILE, NetworkType.WIFI)
    val metricOptions = listOf(
        AppUsageSortOrder.TOTAL_DESC,
        AppUsageSortOrder.DOWNLOAD_DESC,
        AppUsageSortOrder.UPLOAD_DESC
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PeriodDropdown(
            selectedPeriod = selectedPeriod,
            onPeriodSelected = onPeriodSelected,
            modifier = Modifier.weight(1f)
        )
        NetworkDropdown(
            selectedNetwork = selectedNetwork,
            networkOptions = networkOptions,
            onNetworkSelected = onNetworkSelected,
            modifier = Modifier.weight(1f)
        )
        MetricDropdown(
            selectedMetric = selectedMetric,
            metricOptions = metricOptions,
            onMetricSelected = onMetricSelected,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PeriodDropdown(
    selectedPeriod: AppUsagePeriod,
    onPeriodSelected: (AppUsagePeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    AppUsageExposedDropdown(
        label = "Period",
        selectedValueText = selectedPeriod.label,
        selectedItem = selectedPeriod,
        items = AppUsagePeriod.entries,
        itemLabel = { it.label },
        onItemSelected = onPeriodSelected,
        modifier = modifier
    )
}

@Composable
private fun NetworkDropdown(
    selectedNetwork: NetworkType,
    networkOptions: List<NetworkType>,
    onNetworkSelected: (NetworkType) -> Unit,
    modifier: Modifier = Modifier
) {
    fun displayLabel(type: NetworkType): String = when (type) {
        NetworkType.ALL -> "All"
        NetworkType.MOBILE -> "Mobile"
        NetworkType.WIFI -> "Wi-Fi"
    }

    fun menuLabel(type: NetworkType): String = when (type) {
        NetworkType.ALL -> "All Networks"
        NetworkType.MOBILE -> "Mobile"
        NetworkType.WIFI -> "Wi-Fi"
    }

    fun networkIcon(type: NetworkType): ImageVector = when (type) {
        NetworkType.ALL -> Icons.Default.Public
        NetworkType.MOBILE -> Icons.Default.CellTower
        NetworkType.WIFI -> Icons.Default.Wifi
    }

    AppUsageExposedDropdown(
        label = "Network",
        selectedValueText = displayLabel(selectedNetwork),
        selectedItem = selectedNetwork,
        items = networkOptions,
        itemLabel = ::menuLabel,
        onItemSelected = onNetworkSelected,
        itemIcon = ::networkIcon,
        modifier = modifier
    )
}

@Composable
private fun MetricDropdown(
    selectedMetric: AppUsageSortOrder,
    metricOptions: List<AppUsageSortOrder>,
    onMetricSelected: (AppUsageSortOrder) -> Unit,
    modifier: Modifier = Modifier
) {
    fun displayLabel(order: AppUsageSortOrder): String = when (order) {
        AppUsageSortOrder.TOTAL_DESC -> "Total"
        AppUsageSortOrder.DOWNLOAD_DESC -> "Download"
        AppUsageSortOrder.UPLOAD_DESC -> "Upload"
        AppUsageSortOrder.NAME_ASC -> "Name"
    }

    fun menuLabel(order: AppUsageSortOrder): String = when (order) {
        AppUsageSortOrder.TOTAL_DESC -> "Total Data"
        AppUsageSortOrder.DOWNLOAD_DESC -> "Download"
        AppUsageSortOrder.UPLOAD_DESC -> "Upload"
        AppUsageSortOrder.NAME_ASC -> "App Name"
    }

    fun metricIcon(order: AppUsageSortOrder): ImageVector = when (order) {
        AppUsageSortOrder.TOTAL_DESC -> Icons.Default.SwapVert
        AppUsageSortOrder.DOWNLOAD_DESC -> Icons.Default.ArrowDownward
        AppUsageSortOrder.UPLOAD_DESC -> Icons.Default.ArrowUpward
        AppUsageSortOrder.NAME_ASC -> Icons.Default.SwapVert
    }

    AppUsageExposedDropdown(
        label = "Metric",
        selectedValueText = displayLabel(selectedMetric),
        selectedItem = selectedMetric,
        items = metricOptions,
        itemLabel = ::menuLabel,
        onItemSelected = onMetricSelected,
        itemIcon = ::metricIcon,
        modifier = modifier
    )
}

@Composable
private fun <T> AppUsageExposedDropdown(
    label: String,
    selectedValueText: String,
    selectedItem: T,
    items: List<T>,
    itemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    itemIcon: ((T) -> ImageVector)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            ),
            color = if (expanded) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = selectedValueText,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            items.forEach { item ->
                val isSelected = item == selectedItem
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemLabel(item),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                    leadingIcon = itemIcon?.let { iconProvider ->
                        {
                            Icon(
                                imageVector = iconProvider(item),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            } else Modifier
                        ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
