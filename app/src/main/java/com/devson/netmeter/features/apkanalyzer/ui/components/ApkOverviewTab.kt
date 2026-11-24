package com.devson.netmeter.features.apkanalyzer.ui.components

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devson.netmeter.core.util.FormatUtils
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkMetadata

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkOverviewTab(
    metadata: ApkMetadata,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (metadata.isSplitApk) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Split APK Detected",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "This APK is a split package (${metadata.splitName ?: "config split"}). In Android App Bundles, multi-APK packages require the base APK and companion splits to provide full features.",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }

        item {
            ApkCard {
                SectionHeader(title = "Package Identity", icon = Icons.Default.Info)
                InfoRow(label = "Application Name", value = metadata.appName, canCopy = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(label = "Package Name", value = metadata.packageName, isMonospace = true, canCopy = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(label = "Version Name", value = metadata.versionName ?: "None", canCopy = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(label = "Version Code", value = metadata.versionCode.toString(), canCopy = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(label = "File Name", value = metadata.fileName, isMonospace = true, canCopy = true)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(label = "APK File Size", value = FormatUtils.formatBytes(metadata.fileSizeBytes))
            }
        }

        item {
            ApkCard {
                SectionHeader(title = "Target & SDK Requirements", icon = Icons.Default.Build)
                InfoRow(
                    label = "Minimum SDK",
                    value = metadata.minSdkVersion?.let { "API $it (${getAndroidVersionName(it)})" } ?: "Not specified"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = "Target SDK",
                    value = metadata.targetSdkVersion?.let { "API $it (${getAndroidVersionName(it)})" } ?: "Not specified",
                    trailingBadge = {
                        val target = metadata.targetSdkVersion ?: 0
                        if (target in 1..27) {
                            StatusBadge(text = "Legacy API", containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                        } else if (target in 28..30) {
                            StatusBadge(text = "Older API", containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        } else if (target >= 31) {
                            StatusBadge(text = "Modern API", containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = "Compile SDK",
                    value = metadata.compileSdkVersion?.let { "API $it" } ?: "Unavailable"
                )
            }
        }

        item {
            ApkCard {
                SectionHeader(title = "Application Flags & Architecture")
                InfoRow(
                    label = "Debuggable",
                    value = if (metadata.isDebuggable) "Enabled (True)" else "Disabled (False)",
                    trailingBadge = {
                        if (metadata.isDebuggable) {
                            StatusBadge(text = "High Risk", containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)
                        } else {
                            StatusBadge(text = "Secure", containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = "Allow Backup",
                    value = if (metadata.allowBackup) "Enabled (True)" else "Disabled (False)"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = "Cleartext Traffic",
                    value = when (metadata.usesCleartextTraffic) {
                        true -> "Allowed (Insecure)"
                        false -> "Blocked (HTTPS Only)"
                        null -> "Platform Default"
                    },
                    trailingBadge = {
                        if (metadata.usesCleartextTraffic == true) {
                            StatusBadge(text = "Insecure", containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error)
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = "Test-Only Package",
                    value = if (metadata.isTestOnly) "Yes" else "No"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = "Extract Native Libraries",
                    value = metadata.extractNativeLibs?.toString() ?: "Default"
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                InfoRow(
                    label = "Install Location",
                    value = metadata.installLocation
                )
                if (metadata.category != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    InfoRow(label = "Application Category", value = metadata.category)
                }
            }
        }

        if (metadata.supportedAbis.isNotEmpty()) {
            item {
                ApkCard {
                    SectionHeader(title = "Supported Architectures (ABIs)", icon = Icons.Default.Hardware)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        metadata.supportedAbis.forEach { abi ->
                            StatusBadge(
                                text = abi,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }

        if (metadata.hardwareFeatures.isNotEmpty()) {
            item {
                ApkCard {
                    SectionHeader(title = "Requested Hardware Features")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        metadata.hardwareFeatures.forEach { feature ->
                            val cleanName = feature.removePrefix("android.hardware.")
                            StatusBadge(
                                text = cleanName,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
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

private fun getAndroidVersionName(apiLevel: Int): String {
    return when (apiLevel) {
        26 -> "Android 8.0 Oreo"
        27 -> "Android 8.1 Oreo"
        28 -> "Android 9.0 Pie"
        29 -> "Android 10"
        30 -> "Android 11"
        31 -> "Android 12"
        32 -> "Android 12L"
        33 -> "Android 13"
        34 -> "Android 14"
        35 -> "Android 15"
        36, 37 -> "Android 16+"
        else -> if (apiLevel > 37) "Android Future" else "Android Older"
    }
}
