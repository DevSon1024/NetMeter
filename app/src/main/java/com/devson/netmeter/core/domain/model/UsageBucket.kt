package com.devson.netmeter.core.domain.model

data class UsageBucket(
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val rxBytes: Long,
    val txBytes: Long,
    val networkType: NetworkType,
    val uid: Int = -1,
    val state: Int = -1
) {
    val totalBytes: Long
        get() = rxBytes + txBytes
}
