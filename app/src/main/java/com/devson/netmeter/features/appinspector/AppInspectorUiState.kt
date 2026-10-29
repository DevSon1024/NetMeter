package com.devson.netmeter.features.appinspector

import com.devson.netmeter.core.domain.model.AppDetailInfo
import com.devson.netmeter.core.domain.model.InstalledApp

enum class AppFilterType(val label: String) {
    ALL("All Apps"),
    USER("User Apps"),
    SYSTEM("System Apps"),
    LAUNCHABLE("Launchable")
}

enum class AppSortOrder(val label: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    PACKAGE_ASC("Package (A to Z)"),
    UPDATED_DESC("Recently Updated")
}

sealed interface AppListContentState {
    data object Loading : AppListContentState
    data object Empty : AppListContentState
    data class Error(val message: String) : AppListContentState
    data class Success(
        val apps: List<InstalledApp>,
        val totalCount: Int,
        val filteredCount: Int
    ) : AppListContentState
}

sealed interface AppDetailContentState {
    data object Idle : AppDetailContentState
    data object Loading : AppDetailContentState
    data class Error(val message: String) : AppDetailContentState
    data class Success(val detail: AppDetailInfo) : AppDetailContentState
}

data class AppInspectorUiState(
    val listState: AppListContentState = AppListContentState.Loading,
    val detailState: AppDetailContentState = AppDetailContentState.Idle,
    val searchQuery: String = "",
    val filterType: AppFilterType = AppFilterType.ALL,
    val sortOrder: AppSortOrder = AppSortOrder.NAME_ASC,
    val isRefreshing: Boolean = false,
    val userMessage: String? = null
)
