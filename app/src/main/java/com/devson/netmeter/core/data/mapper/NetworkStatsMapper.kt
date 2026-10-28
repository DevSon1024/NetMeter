package com.devson.netmeter.core.data.mapper

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.RemoteException
import com.devson.netmeter.core.domain.model.AppUsage
import com.devson.netmeter.core.domain.model.NetworkType
import com.devson.netmeter.core.domain.model.UsageErrorType
import com.devson.netmeter.core.domain.model.UsageQueryResult

data class UidByteAggregate(
    val uid: Int,
    var rxBytes: Long = 0L,
    var txBytes: Long = 0L
) {
    fun addBytes(rx: Long, tx: Long) {
        if (rx > 0L) rxBytes += rx
        if (tx > 0L) txBytes += tx
    }

    val totalBytes: Long
        get() = rxBytes + txBytes
}

object NetworkStatsMapper {

    fun mapExceptionToResult(throwable: Throwable): UsageQueryResult<Nothing> {
        return when (throwable) {
            is SecurityException -> UsageQueryResult.PermissionRequired
            is RemoteException -> UsageQueryResult.Error(
                errorType = UsageErrorType.REMOTE_SERVICE_ERROR,
                message = "Remote system service failure: ${throwable.message ?: "Unknown remote error"}",
                cause = throwable
            )
            is IllegalArgumentException -> UsageQueryResult.Error(
                errorType = UsageErrorType.INVALID_TIME_RANGE,
                message = "Invalid query parameters: ${throwable.message ?: "Invalid arguments"}",
                cause = throwable
            )
            is IllegalStateException -> UsageQueryResult.Error(
                errorType = UsageErrorType.SERVICE_UNAVAILABLE,
                message = "Network stats service is unavailable: ${throwable.message ?: "Service error"}",
                cause = throwable
            )
            else -> UsageQueryResult.Error(
                errorType = UsageErrorType.UNKNOWN_ERROR,
                message = throwable.message ?: "Unexpected error during network statistics query",
                cause = throwable
            )
        }
    }

    fun aggregateUidBytes(
        aggregates: Map<Int, UidByteAggregate>,
        networkType: NetworkType,
        packageManager: PackageManager?
    ): List<AppUsage> {
        return aggregates.values
            .filter { it.totalBytes > 0L }
            .map { agg ->
                resolveAppUsage(
                    uid = agg.uid,
                    rxBytes = agg.rxBytes,
                    txBytes = agg.txBytes,
                    networkType = networkType,
                    packageManager = packageManager
                )
            }
            .sortedByDescending { it.totalBytes }
    }

    fun resolveAppUsage(
        uid: Int,
        rxBytes: Long,
        txBytes: Long,
        networkType: NetworkType,
        packageManager: PackageManager?
    ): AppUsage {
        if (packageManager == null) {
            val label = getSpecialUidLabel(uid) ?: "UID $uid"
            return AppUsage(
                uid = uid,
                packageName = null,
                appName = label,
                rxBytes = rxBytes,
                txBytes = txBytes,
                networkType = networkType,
                isSystemApp = uid in 0 until 10000
            )
        }

        val packages = try {
            packageManager.getPackagesForUid(uid)
        } catch (_: Exception) {
            null
        }

        if (packages.isNullOrEmpty()) {
            val label = getSpecialUidLabel(uid) ?: "UID $uid"
            return AppUsage(
                uid = uid,
                packageName = null,
                appName = label,
                rxBytes = rxBytes,
                txBytes = txBytes,
                networkType = networkType,
                isSystemApp = uid in 0 until 10000
            )
        }

        val primaryPackage = packages[0]
        val sharedList = packages.toList()
        return try {
            val appInfo = packageManager.getApplicationInfo(primaryPackage, 0)
            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppUsage(
                uid = uid,
                packageName = primaryPackage,
                appName = appName,
                rxBytes = rxBytes,
                txBytes = txBytes,
                networkType = networkType,
                isSystemApp = isSystem,
                sharedPackages = sharedList
            )
        } catch (_: Exception) {
            AppUsage(
                uid = uid,
                packageName = primaryPackage,
                appName = primaryPackage,
                rxBytes = rxBytes,
                txBytes = txBytes,
                networkType = networkType,
                isSystemApp = false,
                sharedPackages = sharedList
            )
        }
    }

    private fun getSpecialUidLabel(uid: Int): String? {
        return when (uid) {
            0 -> "Android OS (Kernel)"
            Process.SYSTEM_UID -> "Android System"
            -4 -> "Removed Apps"
            -5 -> "Tethering & Hotspot"
            else -> null
        }
    }
}
