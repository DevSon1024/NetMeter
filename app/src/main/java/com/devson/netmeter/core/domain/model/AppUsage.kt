package com.devson.netmeter.core.domain.model

data class AppUsage(
    val uid: Int,
    val packageName: String? = null,
    val appName: String? = null,
    val rxBytes: Long,
    val txBytes: Long,
    val networkType: NetworkType,
    val isSystemApp: Boolean = false
) {
    val totalBytes: Long
        get() = rxBytes + txBytes
}
