package com.devson.netmeter.features.appinspector

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.netmeter.core.data.repository.AndroidAppInfoRepository
import com.devson.netmeter.core.domain.model.InstalledApp
import com.devson.netmeter.core.domain.repository.AppInfoRepository
import com.devson.netmeter.core.util.AppActionsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AppInspectorViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: AppInfoRepository = AndroidAppInfoRepository(application)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppInspectorUiState())
    val uiState: StateFlow<AppInspectorUiState> = _uiState.asStateFlow()

    private var rawApps: List<InstalledApp> = emptyList()

    init {
        loadInstalledApps()
    }

    fun loadInstalledApps(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _uiState.update { it.copy(isRefreshing = true) }
            } else {
                _uiState.update { it.copy(listState = AppListContentState.Loading) }
            }

            repository.getInstalledApps().fold(
                onSuccess = { apps ->
                    rawApps = apps
                    _uiState.update { it.copy(isRefreshing = false) }
                    applyFiltersAndSort()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            listState = AppListContentState.Error(
                                error.message ?: "Failed to query installed applications."
                            )
                        )
                    }
                }
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFiltersAndSort()
    }

    fun updateFilterType(filterType: AppFilterType) {
        _uiState.update { it.copy(filterType = filterType) }
        applyFiltersAndSort()
    }

    fun updateSortOrder(sortOrder: AppSortOrder) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        applyFiltersAndSort()
    }

    private fun applyFiltersAndSort() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val filter = _uiState.value.filterType
        val sort = _uiState.value.sortOrder

        val filtered = rawApps.filter { app ->
            val matchesFilter = when (filter) {
                AppFilterType.ALL -> true
                AppFilterType.USER -> !app.isSystemApp
                AppFilterType.SYSTEM -> app.isSystemApp
                AppFilterType.LAUNCHABLE -> app.isLaunchable
            }
            val matchesQuery = if (query.isEmpty()) {
                true
            } else {
                app.appName.lowercase().contains(query) || app.packageName.lowercase().contains(query)
            }
            matchesFilter && matchesQuery
        }

        val sorted = when (sort) {
            AppSortOrder.NAME_ASC -> filtered.sortedBy { it.appName.lowercase() }
            AppSortOrder.NAME_DESC -> filtered.sortedByDescending { it.appName.lowercase() }
            AppSortOrder.PACKAGE_ASC -> filtered.sortedBy { it.packageName.lowercase() }
            AppSortOrder.UPDATED_DESC -> filtered.sortedByDescending { it.lastUpdateTime }
        }

        val state = if (sorted.isEmpty()) {
            AppListContentState.Empty
        } else {
            AppListContentState.Success(
                apps = sorted,
                totalCount = rawApps.size,
                filteredCount = sorted.size
            )
        }

        _uiState.update { it.copy(listState = state) }
    }

    fun loadAppDetails(packageName: String, isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!isRefresh) {
                _uiState.update { it.copy(detailState = AppDetailContentState.Loading) }
            }
            repository.getAppDetails(packageName).fold(
                onSuccess = { detail ->
                    _uiState.update { it.copy(detailState = AppDetailContentState.Success(detail)) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            detailState = AppDetailContentState.Error(
                                error.message ?: "Application metadata is unavailable for $packageName."
                            )
                        )
                    }
                }
            )
        }
    }

    fun clearDetailState() {
        _uiState.update { it.copy(detailState = AppDetailContentState.Idle) }
    }

    fun copyPackageName(context: Context, packageName: String) {
        val success = AppActionsHelper.copyPackageName(context, packageName)
        if (success) {
            _uiState.update { it.copy(userMessage = "Copied package: $packageName") }
        } else {
            _uiState.update { it.copy(userMessage = "Failed to copy package name to clipboard.") }
        }
    }

    fun launchApp(context: Context, packageName: String) {
        val success = AppActionsHelper.launchApp(context, packageName)
        if (!success) {
            _uiState.update { it.copy(userMessage = "Cannot launch app: no launcher activity found.") }
        }
    }

    fun openAppInfo(context: Context, packageName: String) {
        val success = AppActionsHelper.openAppInfo(context, packageName)
        if (!success) {
            _uiState.update { it.copy(userMessage = "Could not open system settings for this app.") }
        }
    }

    fun openPermissions(context: Context, packageName: String) {
        val success = AppActionsHelper.openManagePermissions(context, packageName)
        if (!success) {
            _uiState.update { it.copy(userMessage = "Could not open permission settings.") }
        }
    }

    fun openStorageSettings(context: Context, packageName: String) {
        val success = AppActionsHelper.openStorageSettings(context, packageName)
        if (!success) {
            _uiState.update { it.copy(userMessage = "Could not open storage settings.") }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }
}
