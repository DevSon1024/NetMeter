package com.devson.netmeter.core.data.repository

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import com.devson.netmeter.core.data.mapper.NetworkStatsMapper
import com.devson.netmeter.core.data.mapper.UidByteAggregate
import com.devson.netmeter.core.domain.model.AppUsage
import com.devson.netmeter.core.domain.model.DeviceUsage
import com.devson.netmeter.core.domain.model.NetworkType
import com.devson.netmeter.core.domain.model.UsageBucket
import com.devson.netmeter.core.domain.model.UsageErrorType
import com.devson.netmeter.core.domain.model.UsageQueryResult
import com.devson.netmeter.core.domain.repository.NetworkStatsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
class AndroidNetworkStatsRepository(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : NetworkStatsRepository {

    private val networkStatsManager: NetworkStatsManager?
        get() = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager

    private val packageManager
        get() = context.packageManager

    override suspend fun getDeviceUsage(
        networkType: NetworkType,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): UsageQueryResult<DeviceUsage> = withContext(dispatcher) {
        if (startTimeMillis > endTimeMillis) {
            return@withContext UsageQueryResult.Error(
                errorType = UsageErrorType.INVALID_TIME_RANGE,
                message = "Start time ($startTimeMillis) cannot exceed end time ($endTimeMillis)"
            )
        }

        val manager = networkStatsManager
            ?: return@withContext UsageQueryResult.Error(
                errorType = UsageErrorType.SERVICE_UNAVAILABLE,
                message = "NetworkStatsManager service is not available on this device"
            )

        try {
            when (networkType) {
                NetworkType.WIFI -> {
                    val bucket = manager.querySummaryForDevice(
                        ConnectivityManager.TYPE_WIFI,
                        null,
                        startTimeMillis,
                        endTimeMillis
                    )
                    val rx = bucket?.rxBytes?.coerceAtLeast(0L) ?: 0L
                    val tx = bucket?.txBytes?.coerceAtLeast(0L) ?: 0L
                    UsageQueryResult.Success(
                        DeviceUsage(startTimeMillis, endTimeMillis, rx, tx, NetworkType.WIFI)
                    )
                }
                NetworkType.MOBILE -> {
                    val bucket = manager.querySummaryForDevice(
                        ConnectivityManager.TYPE_MOBILE,
                        null,
                        startTimeMillis,
                        endTimeMillis
                    )
                    val rx = bucket?.rxBytes?.coerceAtLeast(0L) ?: 0L
                    val tx = bucket?.txBytes?.coerceAtLeast(0L) ?: 0L
                    UsageQueryResult.Success(
                        DeviceUsage(startTimeMillis, endTimeMillis, rx, tx, NetworkType.MOBILE)
                    )
                }
                NetworkType.ALL -> {
                    var totalRx = 0L
                    var totalTx = 0L

                    try {
                        val wifiBucket = manager.querySummaryForDevice(
                            ConnectivityManager.TYPE_WIFI,
                            null,
                            startTimeMillis,
                            endTimeMillis
                        )
                        totalRx += wifiBucket?.rxBytes?.coerceAtLeast(0L) ?: 0L
                        totalTx += wifiBucket?.txBytes?.coerceAtLeast(0L) ?: 0L
                    } catch (_: Exception) {
                        // Tolerate Wi-Fi hardware absence on specialized hardware
                    }

                    try {
                        val mobileBucket = manager.querySummaryForDevice(
                            ConnectivityManager.TYPE_MOBILE,
                            null,
                            startTimeMillis,
                            endTimeMillis
                        )
                        totalRx += mobileBucket?.rxBytes?.coerceAtLeast(0L) ?: 0L
                        totalTx += mobileBucket?.txBytes?.coerceAtLeast(0L) ?: 0L
                    } catch (_: Exception) {
                        // Tolerate Mobile data absence on Wi-Fi-only devices
                    }

                    UsageQueryResult.Success(
                        DeviceUsage(startTimeMillis, endTimeMillis, totalRx, totalTx, NetworkType.ALL)
                    )
                }
            }
        } catch (e: Exception) {
            NetworkStatsMapper.mapExceptionToResult(e)
        }
    }

    override suspend fun getAppUsage(
        networkType: NetworkType,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): UsageQueryResult<List<AppUsage>> = withContext(dispatcher) {
        if (startTimeMillis > endTimeMillis) {
            return@withContext UsageQueryResult.Error(
                errorType = UsageErrorType.INVALID_TIME_RANGE,
                message = "Start time ($startTimeMillis) cannot exceed end time ($endTimeMillis)"
            )
        }

        val manager = networkStatsManager
            ?: return@withContext UsageQueryResult.Error(
                errorType = UsageErrorType.SERVICE_UNAVAILABLE,
                message = "NetworkStatsManager service is not available on this device"
            )

        try {
            val aggregates = mutableMapOf<Int, UidByteAggregate>()

            if (networkType == NetworkType.WIFI || networkType == NetworkType.ALL) {
                collectNetworkStatsSummary(
                    manager = manager,
                    networkTypeInt = ConnectivityManager.TYPE_WIFI,
                    startTimeMillis = startTimeMillis,
                    endTimeMillis = endTimeMillis,
                    aggregates = aggregates
                )
            }

            if (networkType == NetworkType.MOBILE || networkType == NetworkType.ALL) {
                collectNetworkStatsSummary(
                    manager = manager,
                    networkTypeInt = ConnectivityManager.TYPE_MOBILE,
                    startTimeMillis = startTimeMillis,
                    endTimeMillis = endTimeMillis,
                    aggregates = aggregates
                )
            }

            val apps = NetworkStatsMapper.aggregateUidBytes(
                aggregates = aggregates,
                networkType = networkType,
                packageManager = packageManager
            )

            if (apps.isEmpty()) {
                UsageQueryResult.Empty
            } else {
                UsageQueryResult.Success(apps)
            }
        } catch (e: Exception) {
            NetworkStatsMapper.mapExceptionToResult(e)
        }
    }

    override suspend fun getTimeBuckets(
        networkType: NetworkType,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): UsageQueryResult<List<UsageBucket>> = withContext(dispatcher) {
        if (startTimeMillis > endTimeMillis) {
            return@withContext UsageQueryResult.Error(
                errorType = UsageErrorType.INVALID_TIME_RANGE,
                message = "Start time ($startTimeMillis) cannot exceed end time ($endTimeMillis)"
            )
        }

        val manager = networkStatsManager
            ?: return@withContext UsageQueryResult.Error(
                errorType = UsageErrorType.SERVICE_UNAVAILABLE,
                message = "NetworkStatsManager service is not available on this device"
            )

        try {
            val buckets = mutableListOf<UsageBucket>()

            if (networkType == NetworkType.WIFI || networkType == NetworkType.ALL) {
                collectDetailBuckets(
                    manager = manager,
                    networkTypeInt = ConnectivityManager.TYPE_WIFI,
                    domainType = NetworkType.WIFI,
                    startTimeMillis = startTimeMillis,
                    endTimeMillis = endTimeMillis,
                    destination = buckets
                )
            }

            if (networkType == NetworkType.MOBILE || networkType == NetworkType.ALL) {
                collectDetailBuckets(
                    manager = manager,
                    networkTypeInt = ConnectivityManager.TYPE_MOBILE,
                    domainType = NetworkType.MOBILE,
                    startTimeMillis = startTimeMillis,
                    endTimeMillis = endTimeMillis,
                    destination = buckets
                )
            }

            if (buckets.isEmpty()) {
                UsageQueryResult.Empty
            } else {
                UsageQueryResult.Success(buckets.sortedBy { it.startTimeMillis })
            }
        } catch (e: Exception) {
            NetworkStatsMapper.mapExceptionToResult(e)
        }
    }

    override suspend fun getUidUsage(
        uid: Int,
        networkType: NetworkType,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): UsageQueryResult<AppUsage> = withContext(dispatcher) {
        if (startTimeMillis > endTimeMillis) {
            return@withContext UsageQueryResult.Error(
                errorType = UsageErrorType.INVALID_TIME_RANGE,
                message = "Start time ($startTimeMillis) cannot exceed end time ($endTimeMillis)"
            )
        }

        val manager = networkStatsManager
            ?: return@withContext UsageQueryResult.Error(
                errorType = UsageErrorType.SERVICE_UNAVAILABLE,
                message = "NetworkStatsManager service is not available on this device"
            )

        try {
            var rxBytes = 0L
            var txBytes = 0L

            if (networkType == NetworkType.WIFI || networkType == NetworkType.ALL) {
                val pair = queryUidBytes(manager, ConnectivityManager.TYPE_WIFI, uid, startTimeMillis, endTimeMillis)
                rxBytes += pair.first
                txBytes += pair.second
            }

            if (networkType == NetworkType.MOBILE || networkType == NetworkType.ALL) {
                val pair = queryUidBytes(manager, ConnectivityManager.TYPE_MOBILE, uid, startTimeMillis, endTimeMillis)
                rxBytes += pair.first
                txBytes += pair.second
            }

            val app = NetworkStatsMapper.resolveAppUsage(
                uid = uid,
                rxBytes = rxBytes,
                txBytes = txBytes,
                networkType = networkType,
                packageManager = packageManager
            )

            UsageQueryResult.Success(app)
        } catch (e: Exception) {
            NetworkStatsMapper.mapExceptionToResult(e)
        }
    }

    private fun collectNetworkStatsSummary(
        manager: NetworkStatsManager,
        networkTypeInt: Int,
        startTimeMillis: Long,
        endTimeMillis: Long,
        aggregates: MutableMap<Int, UidByteAggregate>
    ) {
        val stats = manager.querySummary(networkTypeInt, null, startTimeMillis, endTimeMillis)
        stats?.use { networkStats ->
            val bucket = NetworkStats.Bucket()
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                val uid = bucket.uid
                val rx = bucket.rxBytes.coerceAtLeast(0L)
                val tx = bucket.txBytes.coerceAtLeast(0L)

                if (rx > 0L || tx > 0L) {
                    val agg = aggregates.getOrPut(uid) { UidByteAggregate(uid) }
                    agg.addBytes(rx, tx)
                }
            }
        }
    }

    private fun collectDetailBuckets(
        manager: NetworkStatsManager,
        networkTypeInt: Int,
        domainType: NetworkType,
        startTimeMillis: Long,
        endTimeMillis: Long,
        destination: MutableList<UsageBucket>
    ) {
        val stats = manager.queryDetails(networkTypeInt, null, startTimeMillis, endTimeMillis)
        stats?.use { networkStats ->
            val bucket = NetworkStats.Bucket()
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                val rx = bucket.rxBytes.coerceAtLeast(0L)
                val tx = bucket.txBytes.coerceAtLeast(0L)
                if (rx > 0L || tx > 0L) {
                    destination.add(
                        UsageBucket(
                            startTimeMillis = bucket.startTimeStamp,
                            endTimeMillis = bucket.endTimeStamp,
                            rxBytes = rx,
                            txBytes = tx,
                            networkType = domainType,
                            uid = bucket.uid,
                            state = bucket.state
                        )
                    )
                }
            }
        }
    }

    private fun queryUidBytes(
        manager: NetworkStatsManager,
        networkTypeInt: Int,
        uid: Int,
        startTimeMillis: Long,
        endTimeMillis: Long
    ): Pair<Long, Long> {
        var rx = 0L
        var tx = 0L
        val stats = manager.queryDetailsForUid(networkTypeInt, null, startTimeMillis, endTimeMillis, uid)
        stats?.use { networkStats ->
            val bucket = NetworkStats.Bucket()
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                rx += bucket.rxBytes.coerceAtLeast(0L)
                tx += bucket.txBytes.coerceAtLeast(0L)
            }
        }
        return Pair(rx, tx)
    }
}
