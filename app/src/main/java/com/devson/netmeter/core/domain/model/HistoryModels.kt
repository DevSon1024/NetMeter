package com.devson.netmeter.core.domain.model

enum class HistoryPeriod(val label: String) {
    TODAY("Today"),
    LAST_7_DAYS("7 Days"),
    LAST_30_DAYS("30 Days"),
    CUSTOM("Custom")
}

enum class DailyDataStatus {
    COMPLETE,
    PARTIAL,
    EMPTY,
    UNAVAILABLE
}

enum class HistoryDataSource {
    ANDROID_SYSTEM,
    LOCAL_SNAPSHOT
}

data class DayBoundary(
    val dateString: String,
    val displayLabel: String,
    val shortLabel: String,
    val startMillis: Long,
    val endMillis: Long,
    val isToday: Boolean
)

data class DailyUsageRecord(
    val dateString: String,
    val displayLabel: String,
    val shortLabel: String,
    val startMillis: Long,
    val endMillis: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val wifiRxBytes: Long,
    val wifiTxBytes: Long,
    val mobileRxBytes: Long,
    val mobileTxBytes: Long,
    val status: DailyDataStatus,
    val dataSource: HistoryDataSource = HistoryDataSource.ANDROID_SYSTEM
) {
    val totalBytes: Long
        get() = rxBytes + txBytes

    val wifiTotalBytes: Long
        get() = wifiRxBytes + wifiTxBytes

    val mobileTotalBytes: Long
        get() = mobileRxBytes + mobileTxBytes
}
