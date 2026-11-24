package com.devson.netmeter.features.apkanalyzer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.netmeter.core.util.FormatUtils
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkArchiveInfo
import com.devson.netmeter.features.apkanalyzer.domain.model.DexInfo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkFilesTab(
    archiveInfo: ApkArchiveInfo,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Archive Summary
        item {
            ApkCard {
                SectionHeader(title = "ZIP Archive Structure", icon = Icons.Default.FolderZip)
                InfoRow(label = "Total Archive Entries", value = "${archiveInfo.totalEntries} entries")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(label = "Uncompressed Size", value = FormatUtils.formatBytes(archiveInfo.totalUncompressedBytes))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(label = "Compressed Size", value = FormatUtils.formatBytes(archiveInfo.totalCompressedBytes))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                val ratio = if (archiveInfo.totalUncompressedBytes > 0) {
                    ((1.0 - (archiveInfo.totalCompressedBytes.toDouble() / archiveInfo.totalUncompressedBytes)) * 100).coerceAtLeast(0.0)
                } else 0.0
                InfoRow(label = "Compression Savings", value = "%.1f%%".format(ratio))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = "Core Assets",
                    value = "AndroidManifest.xml (${if (archiveInfo.hasManifest) "Present" else "Missing"}), resources.arsc (${if (archiveInfo.hasResourcesArsc) "Present" else "Missing"})"
                )
            }
        }

        // DEX Summary
        item {
            ApkCard {
                SectionHeader(title = "DEX Bytecode (${archiveInfo.dexFiles.size} files)", icon = Icons.Default.Memory)
                Text(
                    text = "Total classes: ${archiveInfo.totalClasses} | Total methods: ${archiveInfo.totalMethods}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        items(archiveInfo.dexFiles) { dex ->
            DexItemCard(dex = dex)
        }

        // Native Libraries
        if (archiveInfo.nativeLibraries.isNotEmpty()) {
            item {
                ApkCard {
                    SectionHeader(
                        title = "Native Libraries (${archiveInfo.nativeLibraries.size} .so files)",
                        icon = Icons.Default.Hardware
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Supported ABIs: ${archiveInfo.supportedAbis.joinToString()}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        archiveInfo.nativeLibraries.forEach { lib ->
                            StatusBadge(
                                text = lib,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Directory Breakdown
        if (archiveInfo.directoryBreakdown.isNotEmpty()) {
            item {
                ApkCard {
                    SectionHeader(title = "Directory Breakdown")
                    Spacer(modifier = Modifier.height(4.dp))
                    archiveInfo.directoryBreakdown.forEachIndexed { i, dir ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dir.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${dir.fileCount} files (${FormatUtils.formatBytes(dir.uncompressedSizeBytes)})",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DexItemCard(dex: DexInfo) {
    var expanded by remember { mutableStateOf(false) }

    ApkCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dex.fileName,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            StatusBadge(
                text = "DEX v${dex.version}",
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Size: ${FormatUtils.formatBytes(dex.sizeBytes)}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Classes: ${dex.classCount} | Methods: ${dex.methodCount}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (dex.sampleClasses.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Hide Sample Classes" else "Show Sample Classes (${dex.sampleClasses.size})",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    dex.sampleClasses.take(25).forEach { className ->
                        Text(
                            text = className,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                    if (dex.sampleClasses.size > 25) {
                        Text(
                            text = "... and ${dex.sampleClasses.size - 25} more",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
