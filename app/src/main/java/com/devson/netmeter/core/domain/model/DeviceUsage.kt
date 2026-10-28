package com.devson.netmeter.core.domain.model

data class DeviceUsage(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val networkType: NetworkType
) {
    val totalBytes: Long
        get() = rxBytes + txBytes
}
