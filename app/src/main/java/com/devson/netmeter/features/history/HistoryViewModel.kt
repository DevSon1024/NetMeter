package com.devson.netmeter.features.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devson.netmeter.core.data.repository.AndroidNetworkStatsRepository
import com.devson.netmeter.core.data.snapshot.HistorySnapshotStore
import com.devson.netmeter.core.data.snapshot.SqliteHistorySnapshotStore
import com.devson.netmeter.core.domain.model.DailyDataStatus
import com.devson.netmeter.core.domain.model.DailyUsageRecord
import com.devson.netmeter.core.domain.model.HistoryDataSource
import com.devson.netmeter.core.domain.model.HistoryPeriod
import com.devson.netmeter.core.domain.model.NetworkType
import com.devson.netmeter.core.domain.model.UsageQueryResult
import com.devson.netmeter.core.domain.repository.NetworkStatsRepository
import com.devson.netmeter.core.permission.DefaultUsagePermissionChecker
import com.devson.netmeter.core.permission.UsagePermissionChecker
import com.devson.netmeter.core.permission.UsagePermissionStatus
import com.devson.netmeter.core.util.FormatUtils
import com.devson.netmeter.core.util.HistoryDateHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ChartType {
    BAR,
    LINE
}

data class HistoryUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isPermissionGranted: Boolean = true,
    val selectedPeriod: HistoryPeriod = HistoryPeriod.LAST_7_DAYS,
    val customStartDateMillis: Long? = null,
    val customEndDateMillis: Long? = null,
    val dateRangeDisplay: String = "",
    val dailyRecords: List<DailyUsageRecord> = emptyList(),
    val totalRangeRxBytes: Long = 0L,
    val totalRangeTxBytes: Long = 0L,
    val totalRangeBytes: Long = 0L,
    val totalWifiBytes: Long = 0L,
    val totalMobileBytes: Long = 0L,
    val selectedRecordIndex: Int? = null,
    val chartType: ChartType = ChartType.BAR,
    val errorMessage: String? = null,
    val showCustomDatePicker: Boolean = false
) {
    val selectedRecord: DailyUsageRecord?
        get() = selectedRecordIndex?.let { dailyRecords.getOrNull(it) }
}

class HistoryViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: NetworkStatsRepository = AndroidNetworkStatsRepository(application.applicationContext),
    private val permissionChecker: UsagePermissionChecker = DefaultUsagePermissionChecker(),
    private val snapshotStore: HistorySnapshotStore = SqliteHistorySnapshotStore(application.applicationContext)
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        loadHistory(isRefresh = false)
    }

    fun refresh() {
        loadHistory(isRefresh = true)
    }

    fun selectPeriod(period: HistoryPeriod) {
        if (period == HistoryPeriod.CUSTOM) {
            _uiState.update { it.copy(showCustomDatePicker = true) }
        } else {
            if (_uiState.value.selectedPeriod != period) {
                _uiState.update { it.copy(selectedPeriod = period) }
                loadHistory()
            }
        }
    }

    fun setCustomDateRange(startMillis: Long, endMillis: Long) {
        _uiState.update {
            it.copy(
                selectedPeriod = HistoryPeriod.CUSTOM,
                customStartDateMillis = minOf(startMillis, endMillis),
                customEndDateMillis = maxOf(startMillis, endMillis),
                showCustomDatePicker = false
            )
        }
        loadHistory()
    }

    fun dismissDatePicker() {
        _uiState.update { it.copy(showCustomDatePicker = false) }
    }

    fun selectRecord(index: Int) {
        if (index in _uiState.value.dailyRecords.indices) {
            _uiState.update { it.copy(selectedRecordIndex = index) }
        }
    }

    fun toggleChartType() {
        val nextType = if (_uiState.value.chartType == ChartType.BAR) ChartType.LINE else ChartType.BAR
        _uiState.update { it.copy(chartType = nextType) }
    }

    fun loadHistory(isRefresh: Boolean = false) {
        val permStatus = permissionChecker.checkPermission(getApplication())
        val hasPermission = permStatus is UsagePermissionStatus.Granted

        if (!hasPermission) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isPermissionGranted = false,
                    dailyRecords = emptyList(),
                    totalRangeRxBytes = 0L,
                    totalRangeTxBytes = 0L,
                    totalRangeBytes = 0L,
                    totalWifiBytes = 0L,
                    totalMobileBytes = 0L,
                    selectedRecordIndex = null,
                    errorMessage = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = !isRefresh && it.dailyRecords.isEmpty(),
                isRefreshing = isRefresh,
                isPermissionGranted = true,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val nowMillis = System.currentTimeMillis()
                val current = _uiState.value
                val dayBoundaries = HistoryDateHelper.calculateDaysForPeriod(
                    period = current.selectedPeriod,
                    nowMillis = nowMillis,
                    customStartMillis = current.customStartDateMillis,
                    customEndMillis = current.customEndDateMillis
                )

                val records = mutableListOf<DailyUsageRecord>()

                for (day in dayBoundaries) {
                    val cachedSnapshot = if (!day.isToday && !isRefresh) {
                        snapshotStore.getSnapshot(day.dateString)
                    } else {
                        null
                    }

                    if (cachedSnapshot != null) {
                        records.add(cachedSnapshot)
                    } else {
                        val wifiResult = repository.getDeviceUsage(NetworkType.WIFI, day.startMillis, day.endMillis)
                        val mobileResult = repository.getDeviceUsage(NetworkType.MOBILE, day.startMillis, day.endMillis)

                        val wifiRx = if (wifiResult is UsageQueryResult.Success) wifiResult.data.rxBytes else 0L
                        val wifiTx = if (wifiResult is UsageQueryResult.Success) wifiResult.data.txBytes else 0L
                        val mobileRx = if (mobileResult is UsageQueryResult.Success) mobileResult.data.rxBytes else 0L
                        val mobileTx = if (mobileResult is UsageQueryResult.Success) mobileResult.data.txBytes else 0L

                        val rx = wifiRx + mobileRx
                        val tx = wifiTx + mobileTx
                        val total = rx + tx

                        val status = when {
                            wifiResult is UsageQueryResult.Error && mobileResult is UsageQueryResult.Error -> DailyDataStatus.UNAVAILABLE
                            day.isToday -> DailyDataStatus.PARTIAL
                            total == 0L -> DailyDataStatus.EMPTY
                            else -> DailyDataStatus.COMPLETE
                        }

                        val record = DailyUsageRecord(
                            dateString = day.dateString,
                            displayLabel = day.displayLabel,
                            shortLabel = day.shortLabel,
                            startMillis = day.startMillis,
                            endMillis = day.endMillis,
                            rxBytes = rx,
                            txBytes = tx,
                            wifiRxBytes = wifiRx,
                            wifiTxBytes = wifiTx,
                            mobileRxBytes = mobileRx,
                            mobileTxBytes = mobileTx,
                            status = status,
                            dataSource = HistoryDataSource.ANDROID_SYSTEM
                        )

                        records.add(record)

                        // Cache completed past days into persistent snapshot store
                        if (!day.isToday && status == DailyDataStatus.COMPLETE) {
                            snapshotStore.saveSnapshot(record)
                        }
                    }
                }

                val totalRx = records.sumOf { it.rxBytes }
                val totalTx = records.sumOf { it.txBytes }
                val totalAll = records.sumOf { it.totalBytes }
                val totalWifi = records.sumOf { it.wifiTotalBytes }
                val totalMobile = records.sumOf { it.mobileTotalBytes }

                val rangeDisplay = if (records.isNotEmpty()) {
                    val first = records.first()
                    val last = records.last()
                    if (records.size == 1) {
                        first.displayLabel
                    } else {
                        "${first.dateString} to ${last.dateString}"
                    }
                } else {
                    ""
                }

                val newSelectedIndex = records.lastIndex.coerceAtLeast(0)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isPermissionGranted = true,
                        dailyRecords = records,
                        totalRangeRxBytes = totalRx,
                        totalRangeTxBytes = totalTx,
                        totalRangeBytes = totalAll,
                        totalWifiBytes = totalWifi,
                        totalMobileBytes = totalMobile,
                        selectedRecordIndex = newSelectedIndex,
                        dateRangeDisplay = rangeDisplay,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = e.message ?: "Failed to aggregate historical network records"
                    )
                }
            }
        }
    }
}
