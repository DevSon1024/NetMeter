package com.devson.netmeter.core.domain.repository

import com.devson.netmeter.core.domain.model.AppUsage
import com.devson.netmeter.core.domain.model.DeviceUsage
import com.devson.netmeter.core.domain.model.NetworkType
import com.devson.netmeter.core.domain.model.UsageBucket
import com.devson.netmeter.core.domain.model.UsageQueryResult

interface NetworkStatsRepository {

    suspend fun getDeviceUsage(
        networkType: NetworkType,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): UsageQueryResult<DeviceUsage>

    suspend fun getAppUsage(
        networkType: NetworkType,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): UsageQueryResult<List<AppUsage>>

    suspend fun getTimeBuckets(
        networkType: NetworkType,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): UsageQueryResult<List<UsageBucket>>

    suspend fun getUidUsage(
        uid: Int,
        networkType: NetworkType,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): UsageQueryResult<AppUsage>
}
