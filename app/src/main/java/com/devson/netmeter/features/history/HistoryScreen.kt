package com.devson.netmeter.features.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devson.netmeter.core.domain.model.DailyDataStatus
import com.devson.netmeter.core.domain.model.DailyUsageRecord
import com.devson.netmeter.core.domain.model.HistoryDataSource
import com.devson.netmeter.core.domain.model.HistoryPeriod
import com.devson.netmeter.core.permission.UsagePermissionViewModel
import com.devson.netmeter.core.ui.UsagePermissionSection
import com.devson.netmeter.core.util.FormatUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = viewModel(),
    permissionViewModel: UsagePermissionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionStatus by permissionViewModel.permissionStatus.collectAsStateWithLifecycle()

    LaunchedEffect(permissionStatus) {
        viewModel.loadHistory(isRefresh = false)
    }

    if (uiState.showCustomDatePicker) {
        CustomRangeQuickDialog(
            onDismiss = { viewModel.dismissDatePicker() },
            onRangeSelected = { start, end ->
                viewModel.setCustomDateRange(start, end)
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            HistoryHeader(
                dateRangeDisplay = uiState.dateRangeDisplay,
                isRefreshing = uiState.isRefreshing,
                onRefresh = { viewModel.refresh() }
            )
        }

        // Usage Access Permission Banner
        item {
            UsagePermissionSection(viewModel = permissionViewModel)
        }

        // Period Selection Filter Chips
        item {
            PeriodFilterChipsRow(
                selectedPeriod = uiState.selectedPeriod,
                onSelectPeriod = { viewModel.selectPeriod(it) }
            )
        }

        if (!uiState.isPermissionGranted) {
            item {
                PermissionRequiredCard(
                    onGrantPermission = { permissionViewModel.openExplanationDialog() }
                )
            }
        } else if (uiState.isLoading && uiState.dailyRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Aggregating historical records...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Range Overview Hero Card
            item {
                RangeOverviewCard(
                    totalBytes = uiState.totalRangeBytes,
                    rxBytes = uiState.totalRangeRxBytes,
                    txBytes = uiState.totalRangeTxBytes,
                    wifiBytes = uiState.totalWifiBytes,
                    mobileBytes = uiState.totalMobileBytes,
                    period = uiState.selectedPeriod,
                    recordsCount = uiState.dailyRecords.size
                )
            }

            // Interactive Bar / Line Chart
            item {
                HistoryChartCard(
                    records = uiState.dailyRecords,
                    selectedIndex = uiState.selectedRecordIndex,
                    chartType = uiState.chartType,
                    onToggleChartType = { viewModel.toggleChartType() },
                    onSelectDay = { viewModel.selectRecord(it) }
                )
            }

            // Selected Day Details Inspector
            item {
                uiState.selectedRecord?.let { record ->
                    SelectedDayInspectorCard(record = record)
                }
            }

            // Daily History List Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${uiState.dailyRecords.size} Days Logged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Daily List Items
            itemsIndexed(uiState.dailyRecords.reversed()) { reverseIndex, record ->
                val originalIndex = uiState.dailyRecords.size - 1 - reverseIndex
                val isSelected = originalIndex == uiState.selectedRecordIndex
                DailyRecordCard(
                    record = record,
                    isSelected = isSelected,
                    onClick = { viewModel.selectRecord(originalIndex) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HistoryHeader(
    dateRangeDisplay: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Network History & Trends",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (dateRangeDisplay.isNotBlank()) {
                Text(
                    text = dateRangeDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh history logs",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun PeriodFilterChipsRow(
    selectedPeriod: HistoryPeriod,
    onSelectPeriod: (HistoryPeriod) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryPeriod.entries.forEach { period ->
            val isSelected = period == selectedPeriod
            FilterChip(
                selected = isSelected,
                onClick = { onSelectPeriod(period) },
                label = { Text(period.label) },
                leadingIcon = if (period == HistoryPeriod.CUSTOM) {
                    {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun RangeOverviewCard(
    totalBytes: Long,
    rxBytes: Long,
    txBytes: Long,
    wifiBytes: Long,
    mobileBytes: Long,
    period: HistoryPeriod,
    recordsCount: Int
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total Range Traffic",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "$recordsCount Days",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = FormatUtils.formatBytes(totalBytes),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Download vs Upload Distribution Bar
            val total = totalBytes.coerceAtLeast(1L)
            val rxRatio = (rxBytes.toFloat() / total).coerceIn(0f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(rxRatio.coerceAtLeast(0.01f))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight((1f - rxRatio).coerceAtLeast(0.01f))
                        .background(MaterialTheme.colorScheme.tertiary)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub-breakdowns
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricSnippet(
                    label = "Download (Rx)",
                    value = FormatUtils.formatBytes(rxBytes),
                    icon = Icons.Default.ArrowDownward,
                    color = MaterialTheme.colorScheme.primary
                )
                MetricSnippet(
                    label = "Upload (Tx)",
                    value = FormatUtils.formatBytes(txBytes),
                    icon = Icons.Default.ArrowUpward,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricSnippet(
                    label = "Wi-Fi Data",
                    value = FormatUtils.formatBytes(wifiBytes),
                    icon = Icons.Default.Wifi,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MetricSnippet(
                    label = "Mobile Data",
                    value = FormatUtils.formatBytes(mobileBytes),
                    icon = Icons.Default.CellTower,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricSnippet(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HistoryChartCard(
    records: List<DailyUsageRecord>,
    selectedIndex: Int?,
    chartType: ChartType,
    onToggleChartType: () -> Unit,
    onSelectDay: (Int) -> Unit
) {
    val totalDownload = records.sumOf { it.rxBytes }
    val totalUpload = records.sumOf { it.txBytes }
    val accessibleDesc = "Historical network activity chart with ${records.size} days. Download ${FormatUtils.formatBytes(totalDownload)}, upload ${FormatUtils.formatBytes(totalUpload)}."

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = accessibleDesc
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (chartType == ChartType.BAR) Icons.Default.BarChart else Icons.AutoMirrored.Filled.ShowChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (chartType == ChartType.BAR) "Daily Bar Chart" else "Traffic Trend Line",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Chart mode toggle
                    IconButton(
                        onClick = onToggleChartType,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (chartType == ChartType.BAR) Icons.AutoMirrored.Filled.ShowChart else Icons.Default.BarChart,
                            contentDescription = "Switch to ${if (chartType == ChartType.BAR) "Line Chart" else "Bar Chart"}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Legend Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download (Rx)", style = MaterialTheme.typography.labelSmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Upload (Tx)", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No records available for selected period", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                if (chartType == ChartType.BAR) {
                    CanvasBarChart(
                        records = records,
                        selectedIndex = selectedIndex,
                        onSelectDay = onSelectDay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                } else {
                    CanvasLineChart(
                        records = records,
                        selectedIndex = selectedIndex,
                        onSelectDay = onSelectDay,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tap any day on the chart to inspect granular metrics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CanvasBarChart(
    records: List<DailyUsageRecord>,
    selectedIndex: Int?,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)
    val baselineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val maxTotal = records.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L

    Canvas(
        modifier = modifier.pointerInput(records) {
            detectTapGestures { offset ->
                val barSlotWidth = size.width / records.size
                val tappedIndex = (offset.x / barSlotWidth).toInt().coerceIn(0, records.size - 1)
                onSelectDay(tappedIndex)
            }
        }
    ) {
        val totalBars = records.size
        val barSlotWidth = size.width / totalBars
        val barWidth = (barSlotWidth * 0.6f).coerceIn(8f, 36f)
        val maxChartHeight = size.height - 20f

        // Draw baseline
        drawLine(
            color = baselineColor,
            start = Offset(0f, maxChartHeight),
            end = Offset(size.width, maxChartHeight),
            strokeWidth = 2f
        )

        records.forEachIndexed { index, record ->
            val slotCenterX = barSlotWidth * index + barSlotWidth / 2f
            val isSelected = index == selectedIndex

            if (isSelected) {
                drawRoundRect(
                    color = highlightColor,
                    topLeft = Offset(barSlotWidth * index + 2f, 0f),
                    size = Size(barSlotWidth - 4f, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                )
            }

            val totalFraction = (record.totalBytes.toFloat() / maxTotal).coerceIn(0f, 1f)
            val barTotalHeight = (maxChartHeight * totalFraction).coerceAtLeast(4f)

            val rxFraction = if (record.totalBytes > 0L) (record.rxBytes.toFloat() / record.totalBytes) else 0.5f
            val rxHeight = barTotalHeight * rxFraction
            val txHeight = barTotalHeight - rxHeight

            val barLeft = slotCenterX - barWidth / 2f
            val barBottom = maxChartHeight

            // Draw Rx (Download) bottom segment
            drawRoundRect(
                color = if (record.totalBytes > 0L) primaryColor else baselineColor,
                topLeft = Offset(barLeft, barBottom - rxHeight),
                size = Size(barWidth, rxHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )

            // Draw Tx (Upload) top segment
            if (txHeight > 0f) {
                drawRoundRect(
                    color = tertiaryColor,
                    topLeft = Offset(barLeft, barBottom - rxHeight - txHeight),
                    size = Size(barWidth, txHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }
    }
}

@Composable
private fun CanvasLineChart(
    records: List<DailyUsageRecord>,
    selectedIndex: Int?,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val highlightColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)

    val maxTotal = records.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L

    Canvas(
        modifier = modifier.pointerInput(records) {
            detectTapGestures { offset ->
                val stepX = size.width / (records.size - 1).coerceAtLeast(1)
                val tappedIndex = ((offset.x + stepX / 2f) / stepX).toInt().coerceIn(0, records.size - 1)
                onSelectDay(tappedIndex)
            }
        }
    ) {
        val count = records.size
        val stepX = if (count > 1) size.width / (count - 1) else size.width
        val maxChartHeight = size.height - 20f

        // Draw baseline
        drawLine(
            color = baselineColor,
            start = Offset(0f, maxChartHeight),
            end = Offset(size.width, maxChartHeight),
            strokeWidth = 2f
        )

        // Draw selected column highlight
        selectedIndex?.let { sel ->
            val selX = if (count > 1) stepX * sel else size.width / 2f
            drawLine(
                color = highlightColor,
                start = Offset(selX, 0f),
                end = Offset(selX, size.height),
                strokeWidth = 16f,
                cap = StrokeCap.Round
            )
        }

        if (count < 2) {
            records.firstOrNull()?.let { rec ->
                val frac = (rec.totalBytes.toFloat() / maxTotal).coerceIn(0f, 1f)
                val y = maxChartHeight - (maxChartHeight * frac)
                drawCircle(color = primaryColor, radius = 6f, center = Offset(size.width / 2f, y))
            }
            return@Canvas
        }

        val rxPath = Path()
        val txPath = Path()

        records.forEachIndexed { i, record ->
            val x = stepX * i
            val rxFrac = (record.rxBytes.toFloat() / maxTotal).coerceIn(0f, 1f)
            val txFrac = (record.txBytes.toFloat() / maxTotal).coerceIn(0f, 1f)

            val yRx = maxChartHeight - (maxChartHeight * rxFrac)
            val yTx = maxChartHeight - (maxChartHeight * txFrac)

            if (i == 0) {
                rxPath.moveTo(x, yRx)
                txPath.moveTo(x, yTx)
            } else {
                rxPath.lineTo(x, yRx)
                txPath.lineTo(x, yTx)
            }

            drawCircle(color = primaryColor, radius = 4f, center = Offset(x, yRx))
            drawCircle(color = tertiaryColor, radius = 4f, center = Offset(x, yTx))
        }

        drawPath(path = rxPath, color = primaryColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
        drawPath(path = txPath, color = tertiaryColor, style = Stroke(width = 3f, cap = StrokeCap.Round))
    }
}

@Composable
private fun SelectedDayInspectorCard(
    record: DailyUsageRecord
) {
    val statusColor = when (record.status) {
        DailyDataStatus.COMPLETE -> MaterialTheme.colorScheme.primaryContainer
        DailyDataStatus.PARTIAL -> MaterialTheme.colorScheme.secondaryContainer
        DailyDataStatus.EMPTY -> MaterialTheme.colorScheme.surfaceVariant
        DailyDataStatus.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
    }

    val statusTextColor = when (record.status) {
        DailyDataStatus.COMPLETE -> MaterialTheme.colorScheme.onPrimaryContainer
        DailyDataStatus.PARTIAL -> MaterialTheme.colorScheme.onSecondaryContainer
        DailyDataStatus.EMPTY -> MaterialTheme.colorScheme.onSurfaceVariant
        DailyDataStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onErrorContainer
    }

    val statusText = when (record.status) {
        DailyDataStatus.COMPLETE -> "Complete Day"
        DailyDataStatus.PARTIAL -> "Ongoing Today"
        DailyDataStatus.EMPTY -> "No Traffic"
        DailyDataStatus.UNAVAILABLE -> "Unavailable"
    }

    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.displayLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = statusColor
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusTextColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Day Data", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        FormatUtils.formatBytes(record.totalBytes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (record.dataSource == HistoryDataSource.ANDROID_SYSTEM) Icons.Default.Info else Icons.Default.Storage,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (record.dataSource == HistoryDataSource.ANDROID_SYSTEM) "System Netstats" else "Saved Snapshot",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricSnippet(
                    label = "Download",
                    value = FormatUtils.formatBytes(record.rxBytes),
                    icon = Icons.Default.ArrowDownward,
                    color = MaterialTheme.colorScheme.primary
                )
                MetricSnippet(
                    label = "Upload",
                    value = FormatUtils.formatBytes(record.txBytes),
                    icon = Icons.Default.ArrowUpward,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricSnippet(
                    label = "Wi-Fi",
                    value = FormatUtils.formatBytes(record.wifiTotalBytes),
                    icon = Icons.Default.Wifi,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MetricSnippet(
                    label = "Mobile",
                    value = FormatUtils.formatBytes(record.mobileTotalBytes),
                    icon = Icons.Default.CellTower,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DailyRecordCard(
    record: DailyUsageRecord,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.displayLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (record.status == DailyDataStatus.PARTIAL) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(8.dp)
                        ) {}
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "↓ ${FormatUtils.formatBytes(record.rxBytes)}  ↑ ${FormatUtils.formatBytes(record.txBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = FormatUtils.formatBytes(record.totalBytes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (record.totalBytes > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Wi-Fi: ${FormatUtils.formatBytes(record.wifiTotalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredCard(
    onGrantPermission: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Usage Access Required for History",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "To track and visualize past network traffic records across days, NetMeter requires Android Usage Access permission.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onGrantPermission
            ) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
private fun CustomRangeQuickDialog(
    onDismiss: () -> Unit,
    onRangeSelected: (Long, Long) -> Unit
) {
    val now = System.currentTimeMillis()
    var selectedDaysBack by remember { mutableIntStateOf(14) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Select Custom Range", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Choose past duration to query native network statistics:",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(14 to "14 Days", 21 to "21 Days", 45 to "45 Days", 60 to "60 Days").forEach { (days, label) ->
                        FilterChip(
                            selected = selectedDaysBack == days,
                            onClick = { selectedDaysBack = days },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = now - (selectedDaysBack.toLong() * 24 * 3600 * 1000)
                    onRangeSelected(start, now)
                }
            ) {
                Text("Apply Range")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
