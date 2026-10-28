package com.devson.netmeter.features.appusage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.netmeter.core.data.repository.AndroidNetworkStatsRepository
import com.devson.netmeter.core.domain.model.AppUsage
import com.devson.netmeter.core.domain.model.NetworkType
import com.devson.netmeter.core.domain.model.UsageQueryResult
import com.devson.netmeter.core.domain.repository.NetworkStatsRepository
import com.devson.netmeter.core.permission.DefaultUsagePermissionChecker
import com.devson.netmeter.core.permission.UsagePermissionChecker
import com.devson.netmeter.core.permission.UsagePermissionStatus
import com.devson.netmeter.core.util.AppUsagePeriod
import com.devson.netmeter.core.util.FormatUtils
import com.devson.netmeter.core.util.TimeRangeHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppUsageSortOrder(val label: String) {
    TOTAL_DESC("Total Data"),
    DOWNLOAD_DESC("Download"),
    UPLOAD_DESC("Upload"),
    NAME_ASC("App Name")
}

data class AppUsageDisplayItem(
    val uid: Int,
    val packageName: String?,
    val appName: String,
    val rxBytes: Long,
    val txBytes: Long,
    val totalBytes: Long,
    val rxFormatted: String,
    val txFormatted: String,
    val totalFormatted: String,
    val networkType: NetworkType,
    val percentageOfTotal: Float,
    val isSystemApp: Boolean,
    val sharedPackages: List<String> = emptyList()
)

sealed interface AppUsageContentState {
    data object Loading : AppUsageContentState
    data object PermissionRequired : AppUsageContentState
    data object Empty : AppUsageContentState
    data class Error(val message: String) : AppUsageContentState
    data class Success(
        val items: List<AppUsageDisplayItem>,
        val totalRxBytes: Long,
        val totalTxBytes: Long,
        val totalBytes: Long,
        val totalRxFormatted: String,
        val totalTxFormatted: String,
        val totalFormatted: String,
        val appCount: Int
    ) : AppUsageContentState
}

data class AppUsageUiState(
    val contentState: AppUsageContentState = AppUsageContentState.Loading,
    val selectedPeriod: AppUsagePeriod = AppUsagePeriod.TODAY,
    val selectedNetworkType: NetworkType = NetworkType.ALL,
    val sortOrder: AppUsageSortOrder = AppUsageSortOrder.TOTAL_DESC,
    val searchQuery: String = "",
    val isRefreshing: Boolean = false
)

class AppUsageViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: NetworkStatsRepository = AndroidNetworkStatsRepository(application),
    private val permissionChecker: UsagePermissionChecker = DefaultUsagePermissionChecker()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AppUsageUiState())
    val uiState: StateFlow<AppUsageUiState> = _uiState.asStateFlow()

    private var rawAppList: List<AppUsage> = emptyList()

    init {
        loadAppUsage()
    }

    fun loadAppUsage(isPullToRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isPullToRefresh) {
                _uiState.update { it.copy(isRefreshing = true) }
            } else {
                _uiState.update { it.copy(contentState = AppUsageContentState.Loading) }
            }

            val permStatus = permissionChecker.checkPermission(getApplication())
            if (permStatus != UsagePermissionStatus.Granted) {
                rawAppList = emptyList()
                _uiState.update {
                    it.copy(
                        contentState = AppUsageContentState.PermissionRequired,
                        isRefreshing = false
                    )
                }
                return@launch
            }

            val (startTime, endTime) = TimeRangeHelper.getTimeRange(
                period = _uiState.value.selectedPeriod
            )

            when (val result = repository.getAppUsage(_uiState.value.selectedNetworkType, startTime, endTime)) {
                is UsageQueryResult.PermissionRequired -> {
                    rawAppList = emptyList()
                    _uiState.update {
                        it.copy(
                            contentState = AppUsageContentState.PermissionRequired,
                            isRefreshing = false
                        )
                    }
                }
                is UsageQueryResult.Empty -> {
                    rawAppList = emptyList()
                    _uiState.update {
                        it.copy(
                            contentState = AppUsageContentState.Empty,
                            isRefreshing = false
                        )
                    }
                }
                is UsageQueryResult.Error -> {
                    rawAppList = emptyList()
                    _uiState.update {
                        it.copy(
                            contentState = AppUsageContentState.Error(result.message),
                            isRefreshing = false
                        )
                    }
                }
                is UsageQueryResult.Success -> {
                    rawAppList = result.data
                    if (rawAppList.isEmpty()) {
                        _uiState.update {
                            it.copy(
                                contentState = AppUsageContentState.Empty,
                                isRefreshing = false
                            )
                        }
                    } else {
                        updateContentState()
                    }
                }
            }
        }
    }

    fun selectPeriod(period: AppUsagePeriod) {
        if (_uiState.value.selectedPeriod != period) {
            _uiState.update { it.copy(selectedPeriod = period) }
            loadAppUsage()
        }
    }

    fun selectNetworkType(networkType: NetworkType) {
        if (_uiState.value.selectedNetworkType != networkType) {
            _uiState.update { it.copy(selectedNetworkType = networkType) }
            loadAppUsage()
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        updateContentState()
    }

    fun setSortOrder(sortOrder: AppUsageSortOrder) {
        _uiState.update { it.copy(sortOrder = sortOrder) }
        updateContentState()
    }

    private fun updateContentState() {
        val current = _uiState.value
        val processedState = processApps(
            rawApps = rawAppList,
            searchQuery = current.searchQuery,
            sortOrder = current.sortOrder,
            networkType = current.selectedNetworkType
        )
        _uiState.update {
            it.copy(
                contentState = processedState,
                isRefreshing = false
            )
        }
    }

    companion object {
        fun processApps(
            rawApps: List<AppUsage>,
            searchQuery: String,
            sortOrder: AppUsageSortOrder,
            networkType: NetworkType
        ): AppUsageContentState {
            if (rawApps.isEmpty()) {
                return AppUsageContentState.Empty
            }

            val grandTotalBytes = rawApps.sumOf { it.totalBytes }
            val grandTotalRx = rawApps.sumOf { it.rxBytes }
            val grandTotalTx = rawApps.sumOf { it.txBytes }

            val filtered = if (searchQuery.isBlank()) {
                rawApps
            } else {
                val query = searchQuery.trim()
                rawApps.filter { app ->
                    (app.appName?.contains(query, ignoreCase = true) == true) ||
                        (app.packageName?.contains(query, ignoreCase = true) == true) ||
                        app.sharedPackages.any { it.contains(query, ignoreCase = true) }
                }
            }

            if (filtered.isEmpty()) {
                return AppUsageContentState.Empty
            }

            val sorted = when (sortOrder) {
                AppUsageSortOrder.TOTAL_DESC -> filtered.sortedByDescending { it.totalBytes }
                AppUsageSortOrder.DOWNLOAD_DESC -> filtered.sortedByDescending { it.rxBytes }
                AppUsageSortOrder.UPLOAD_DESC -> filtered.sortedByDescending { it.txBytes }
                AppUsageSortOrder.NAME_ASC -> filtered.sortedBy { (it.appName ?: "").lowercase() }
            }

            val maxTotal = maxOf(1L, grandTotalBytes)
            val displayItems = sorted.map { app ->
                val ratio = (app.totalBytes.toFloat() / maxTotal).coerceIn(0f, 1f)
                AppUsageDisplayItem(
                    uid = app.uid,
                    packageName = app.packageName,
                    appName = app.appName ?: "Unknown App (UID ${app.uid})",
                    rxBytes = app.rxBytes,
                    txBytes = app.txBytes,
                    totalBytes = app.totalBytes,
                    rxFormatted = FormatUtils.formatBytes(app.rxBytes),
                    txFormatted = FormatUtils.formatBytes(app.txBytes),
                    totalFormatted = FormatUtils.formatBytes(app.totalBytes),
                    networkType = networkType,
                    percentageOfTotal = ratio,
                    isSystemApp = app.isSystemApp,
                    sharedPackages = app.sharedPackages
                )
            }

            return AppUsageContentState.Success(
                items = displayItems,
                totalRxBytes = grandTotalRx,
                totalTxBytes = grandTotalTx,
                totalBytes = grandTotalBytes,
                totalRxFormatted = FormatUtils.formatBytes(grandTotalRx),
                totalTxFormatted = FormatUtils.formatBytes(grandTotalTx),
                totalFormatted = FormatUtils.formatBytes(grandTotalBytes),
                appCount = displayItems.size
            )
        }
    }
}
