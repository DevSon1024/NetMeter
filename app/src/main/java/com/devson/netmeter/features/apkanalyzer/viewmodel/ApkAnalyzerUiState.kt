package com.devson.netmeter.features.apkanalyzer.viewmodel

import com.devson.netmeter.features.apkanalyzer.domain.model.ApkAnalysisResult
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkComponentType
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkHistoryItem
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkPermissionCategory
import com.devson.netmeter.features.apkanalyzer.domain.model.SecuritySeverity

enum class ApkDetailTab(val title: String) {
    OVERVIEW("Overview"),
    PERMISSIONS("Permissions"),
    COMPONENTS("Components"),
    MANIFEST("Manifest"),
    SIGNATURE("Signature"),
    FILES("Files & DEX"),
    SECURITY("Security")
}

data class ApkAnalyzerUiState(
    val isLoading: Boolean = false,
    val loadingStep: String = "",
    val loadingProgress: Float = 0f,
    val analysisResult: ApkAnalysisResult? = null,
    val errorMessage: String? = null,
    val historyItems: List<ApkHistoryItem> = emptyList(),
    val selectedTab: ApkDetailTab = ApkDetailTab.OVERVIEW,
    val permissionSearchQuery: String = "",
    val selectedPermissionCategory: ApkPermissionCategory? = null,
    val showOnlyDangerousPermissions: Boolean = false,
    val componentSearchQuery: String = "",
    val selectedComponentType: ApkComponentType? = null,
    val showOnlyExportedComponents: Boolean = false,
    val selectedSecuritySeverity: SecuritySeverity? = null,
    val exportStatusMessage: String? = null
)
