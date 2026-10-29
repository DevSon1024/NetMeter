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

    data class SpecialUidInfo(
        val label: String,
        val packageName: String? = null,
        val isSystemApp: Boolean = true
    )

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
            val special = getSpecialUidInfo(uid)
            val label = special?.label ?: if (uid in 0 until 10000) "System Service ($uid)" else "UID $uid"
            return AppUsage(
                uid = uid,
                packageName = special?.packageName,
                appName = label,
                rxBytes = rxBytes,
                txBytes = txBytes,
                networkType = networkType,
                isSystemApp = special?.isSystemApp ?: (uid in 0 until 10000)
            )
        }

        // 1. Direct package lookup
        var packages = try {
            packageManager.getPackagesForUid(uid)
        } catch (_: Exception) {
            null
        }

        var isMultiUserOrDual = false
        var userId = 0

        // 2. Multi-user / Dual / Cloned / Work Profile app resolution (UID = userId * 100000 + appId)
        if (packages.isNullOrEmpty() && uid >= 100000) {
            val baseAppId = uid % 100000
            userId = uid / 100000
            val dualPackages = try {
                packageManager.getPackagesForUid(baseAppId)
            } catch (_: Exception) {
                null
            }
            if (!dualPackages.isNullOrEmpty()) {
                packages = dualPackages
                isMultiUserOrDual = true
            }
        }

        // 3. If packages found, extract the best primary package and label
        if (!packages.isNullOrEmpty()) {
            val primaryPackage = selectBestPackage(packages, packageManager)
            val sharedList = packages.toList()

            return try {
                val appInfo = packageManager.getApplicationInfo(primaryPackage, 0)
                val baseAppName = packageManager.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val displayName = if (isMultiUserOrDual) {
                    if (userId == 999 || userId == 998) "$baseAppName (Cloned App)" else "$baseAppName (User $userId)"
                } else {
                    baseAppName
                }

                AppUsage(
                    uid = uid,
                    packageName = primaryPackage,
                    appName = displayName,
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
                    isSystemApp = uid in 0 until 10000,
                    sharedPackages = sharedList
                )
            }
        }

        // 4. Try official name associated with UID from PackageManager
        val uidName = try {
            packageManager.getNameForUid(uid)
        } catch (_: Exception) {
            null
        }

        val nameResolved = resolveNameForUid(uidName, packageManager)
        if (nameResolved != null) {
            return AppUsage(
                uid = uid,
                packageName = nameResolved.packageName,
                appName = nameResolved.label,
                rxBytes = rxBytes,
                txBytes = txBytes,
                networkType = networkType,
                isSystemApp = nameResolved.isSystemApp
            )
        }

        // 5. Check hardcoded standard Android system UIDs
        val special = getSpecialUidInfo(uid)
        if (special != null) {
            return AppUsage(
                uid = uid,
                packageName = special.packageName,
                appName = special.label,
                rxBytes = rxBytes,
                txBytes = txBytes,
                networkType = networkType,
                isSystemApp = special.isSystemApp
            )
        }

        // 6. Fallback: categorize cleanly
        val fallbackLabel = if (uid in 0 until 10000) {
            "System Service ($uid)"
        } else {
            "Uninstalled App ($uid)"
        }

        return AppUsage(
            uid = uid,
            packageName = null,
            appName = fallbackLabel,
            rxBytes = rxBytes,
            txBytes = txBytes,
            networkType = networkType,
            isSystemApp = uid in 0 until 10000
        )
    }

    private fun selectBestPackage(packages: Array<String>, packageManager: PackageManager): String {
        return packages.firstOrNull { pkg ->
            try {
                packageManager.getLaunchIntentForPackage(pkg) != null
            } catch (_: Exception) {
                false
            }
        } ?: packages.firstOrNull { pkg ->
            try {
                val info = packageManager.getApplicationInfo(pkg, 0)
                val label = packageManager.getApplicationLabel(info).toString()
                label != pkg && !label.startsWith("com.")
            } catch (_: Exception) {
                false
            }
        } ?: packages[0]
    }

    private fun resolveNameForUid(uidName: String?, packageManager: PackageManager): SpecialUidInfo? {
        if (uidName.isNullOrBlank()) return null
        val cleanName = uidName.substringBefore(':')

        if (cleanName.contains('.') && !cleanName.startsWith("android.uid.")) {
            try {
                val appInfo = packageManager.getApplicationInfo(cleanName, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                return SpecialUidInfo(label = label, packageName = cleanName, isSystemApp = isSystem)
            } catch (_: Exception) {
                // Ignore and continue
            }
        }

        return when {
            cleanName == "android.uid.system" -> SpecialUidInfo("Android System", "android", true)
            cleanName == "android.uid.phone" -> SpecialUidInfo("Phone & Telephony", "com.android.phone", true)
            cleanName == "android.uid.bluetooth" -> SpecialUidInfo("Bluetooth Services", "com.android.bluetooth", true)
            cleanName == "android.uid.nfc" -> SpecialUidInfo("NFC Services", "com.android.nfc", true)
            cleanName == "android.uid.networkstack" -> SpecialUidInfo("Network Stack", "com.android.networkstack", true)
            cleanName == "android.uid.shell" || cleanName == "shell" -> SpecialUidInfo("Shell / ADB", "com.android.shell", true)
            cleanName == "media" -> SpecialUidInfo("Media Server", null, true)
            cleanName == "audioserver" -> SpecialUidInfo("Audio Server", null, true)
            cleanName == "cameraserver" -> SpecialUidInfo("Camera Server", null, true)
            cleanName == "dnsmasq" -> SpecialUidInfo("DNS Proxy", null, true)
            cleanName == "root" -> SpecialUidInfo("Android OS (Kernel)", null, true)
            cleanName.startsWith("android.uid.") -> {
                val servicePart = cleanName.removePrefix("android.uid.").replaceFirstChar { it.uppercase() }
                SpecialUidInfo("Android $servicePart Service", null, true)
            }
            else -> null
        }
    }

    private fun getSpecialUidInfo(uid: Int): SpecialUidInfo? {
        return when (uid) {
            0 -> SpecialUidInfo(label = "Android OS (Kernel)", packageName = null, isSystemApp = true)
            Process.SYSTEM_UID -> SpecialUidInfo(label = "Android System", packageName = "android", isSystemApp = true)
            Process.PHONE_UID -> SpecialUidInfo(label = "Phone & Telephony", packageName = "com.android.phone", isSystemApp = true)
            Process.BLUETOOTH_UID -> SpecialUidInfo(label = "Bluetooth Services", packageName = "com.android.bluetooth", isSystemApp = true)
            1013 -> SpecialUidInfo(label = "Media Server", packageName = null, isSystemApp = true)
            1014 -> SpecialUidInfo(label = "DHCP Client", packageName = null, isSystemApp = true)
            1020 -> SpecialUidInfo(label = "DRM Server", packageName = null, isSystemApp = true)
            1021 -> SpecialUidInfo(label = "Media DRM", packageName = null, isSystemApp = true)
            1027 -> SpecialUidInfo(label = "Storage Manager", packageName = "com.android.storagemanager", isSystemApp = true)
            1037 -> SpecialUidInfo(label = "KeyStore Security", packageName = null, isSystemApp = true)
            1040 -> SpecialUidInfo(label = "NFC Services", packageName = "com.android.nfc", isSystemApp = true)
            1051 -> SpecialUidInfo(label = "Network Stack", packageName = "com.android.networkstack", isSystemApp = true)
            1068 -> SpecialUidInfo(label = "DNS Resolver", packageName = null, isSystemApp = true)
            1073 -> SpecialUidInfo(label = "StatsD System Daemon", packageName = null, isSystemApp = true)
            2000 -> SpecialUidInfo(label = "Shell / ADB", packageName = "com.android.shell", isSystemApp = true)
            9999 -> SpecialUidInfo(label = "Nobody (Daemon)", packageName = null, isSystemApp = true)
            -4 -> SpecialUidInfo(label = "Removed Apps", packageName = null, isSystemApp = false)
            -5 -> SpecialUidInfo(label = "Tethering & Hotspot", packageName = null, isSystemApp = false)
            else -> null
        }
    }
}
