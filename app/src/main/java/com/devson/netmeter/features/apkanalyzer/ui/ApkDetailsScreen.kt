package com.devson.netmeter.features.apkanalyzer.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.netmeter.core.util.FormatUtils
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkAnalysisResult
import com.devson.netmeter.features.apkanalyzer.ui.components.ApkComponentsTab
import com.devson.netmeter.features.apkanalyzer.ui.components.ApkFilesTab
import com.devson.netmeter.features.apkanalyzer.ui.components.ApkManifestTab
import com.devson.netmeter.features.apkanalyzer.ui.components.ApkOverviewTab
import com.devson.netmeter.features.apkanalyzer.ui.components.ApkPermissionsTab
import com.devson.netmeter.features.apkanalyzer.ui.components.ApkSecurityTab
import com.devson.netmeter.features.apkanalyzer.ui.components.ApkSignatureTab
import com.devson.netmeter.features.apkanalyzer.viewmodel.ApkAnalyzerUiState
import com.devson.netmeter.features.apkanalyzer.viewmodel.ApkAnalyzerViewModel
import com.devson.netmeter.features.apkanalyzer.viewmodel.ApkDetailTab

@Composable
fun ApkDetailsScreen(
    result: ApkAnalysisResult,
    uiState: ApkAnalyzerUiState,
    viewModel: ApkAnalyzerViewModel,
    onSelectAnotherApk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val meta = result.metadata

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportReport(uri)
        }
    }

    val iconBitmap = remember(meta.appIconBytes) {
        meta.appIconBytes?.let { bytes ->
            try {
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Hero App Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (iconBitmap != null) {
                            Image(
                                bitmap = iconBitmap,
                                contentDescription = meta.appName,
                                modifier = Modifier.fillMaxSize().padding(4.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Android,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = meta.appName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = meta.packageName,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v${meta.versionName ?: "1.0"} (${meta.versionCode}) • ${FormatUtils.formatBytes(meta.fileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val defaultExportName = "${meta.packageName}_analysis_report.json"
                            exportLauncher.launch(defaultExportName)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export JSON", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                    }

                    OutlinedButton(
                        onClick = onSelectAnotherApk,
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Another", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp))
                    }
                }
            }
        }

        // Tabs
        SecondaryScrollableTabRow(
            selectedTabIndex = uiState.selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 12.dp,
            divider = {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        ) {
            ApkDetailTab.entries.forEach { tab ->
                val countBadge = when (tab) {
                    ApkDetailTab.PERMISSIONS -> "(${result.permissionSummary.totalCount})"
                    ApkDetailTab.COMPONENTS -> "(${result.components.size})"
                    ApkDetailTab.SECURITY -> "(${result.security.findings.size})"
                    ApkDetailTab.FILES -> "(${result.archive.dexFiles.size} DEX)"
                    else -> ""
                }
                Tab(
                    selected = uiState.selectedTab == tab,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Text(
                            text = "${tab.title} $countBadge".trim(),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            when (uiState.selectedTab) {
                ApkDetailTab.OVERVIEW -> {
                    ApkOverviewTab(metadata = result.metadata)
                }
                ApkDetailTab.PERMISSIONS -> {
                    ApkPermissionsTab(
                        permissions = result.permissions,
                        summary = result.permissionSummary,
                        searchQuery = uiState.permissionSearchQuery,
                        onSearchQueryChange = { viewModel.setPermissionSearchQuery(it) },
                        selectedCategory = uiState.selectedPermissionCategory,
                        onCategorySelected = { viewModel.setPermissionCategory(it) },
                        onlyDangerous = uiState.showOnlyDangerousPermissions,
                        onToggleDangerousOnly = { viewModel.toggleDangerousPermissionsOnly() }
                    )
                }
                ApkDetailTab.COMPONENTS -> {
                    ApkComponentsTab(
                        components = result.components,
                        summary = result.componentsSummary,
                        searchQuery = uiState.componentSearchQuery,
                        onSearchQueryChange = { viewModel.setComponentSearchQuery(it) },
                        selectedType = uiState.selectedComponentType,
                        onTypeSelected = { viewModel.setComponentType(it) },
                        onlyExported = uiState.showOnlyExportedComponents,
                        onToggleExportedOnly = { viewModel.toggleExportedComponentsOnly() }
                    )
                }
                ApkDetailTab.MANIFEST -> {
                    ApkManifestTab(manifestData = result.manifest)
                }
                ApkDetailTab.SIGNATURE -> {
                    ApkSignatureTab(signatureInfo = result.signature)
                }
                ApkDetailTab.FILES -> {
                    ApkFilesTab(archiveInfo = result.archive)
                }
                ApkDetailTab.SECURITY -> {
                    ApkSecurityTab(
                        securityReport = result.security,
                        selectedSeverity = uiState.selectedSecuritySeverity,
                        onSeveritySelected = { viewModel.setSecuritySeverity(it) }
                    )
                }
            }
        }
    }
}
