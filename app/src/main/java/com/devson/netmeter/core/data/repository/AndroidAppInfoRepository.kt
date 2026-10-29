package com.devson.netmeter.core.data.repository

import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import com.devson.netmeter.core.domain.model.AppDetailInfo
import com.devson.netmeter.core.domain.model.AppPermission
import com.devson.netmeter.core.domain.model.AppStorageResult
import com.devson.netmeter.core.domain.model.InstalledApp
import com.devson.netmeter.core.domain.model.PermissionType
import com.devson.netmeter.core.domain.repository.AppInfoRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidAppInfoRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AppInfoRepository {

    private val appContext = context.applicationContext

    override suspend fun getInstalledApps(): Result<List<InstalledApp>> = withContext(ioDispatcher) {
        try {
            val pm = appContext.packageManager
            val packageInfos = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledPackages(0)
                }
            } catch (_: Exception) {
                // Fallback for large package lists or security limitations: query launcher activities
                try {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.queryIntentActivities(intent, 0)
                    }
                    resolveInfos.mapNotNull { it.activityInfo?.packageName }
                        .distinct()
                        .mapNotNull { pkg ->
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getPackageInfo(pkg, 0)
                                }
                            } catch (_: Exception) {
                                null
                            }
                        }
                } catch (fallbackEx: Exception) {
                    return@withContext Result.failure(fallbackEx)
                }
            }

            val apps = packageInfos.mapNotNull { pkgInfo ->
                val packageName = pkgInfo.packageName ?: return@mapNotNull null
                val appInfo = pkgInfo.applicationInfo
                val appName = try {
                    appInfo?.loadLabel(pm)?.toString() ?: packageName
                } catch (_: Exception) {
                    packageName
                }
                val isSystem = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
                val isLaunchable = try {
                    pm.getLaunchIntentForPackage(packageName) != null
                } catch (_: Exception) {
                    false
                }
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                val versionName = pkgInfo.versionName
                val targetSdk = appInfo?.targetSdkVersion
                val minSdk = appInfo?.minSdkVersion
                val firstInstall = pkgInfo.firstInstallTime
                val lastUpdate = pkgInfo.lastUpdateTime

                InstalledApp(
                    packageName = packageName,
                    appName = appName,
                    isSystemApp = isSystem,
                    versionName = versionName,
                    versionCode = versionCode,
                    isLaunchable = isLaunchable,
                    targetSdkVersion = targetSdk,
                    minSdkVersion = minSdk,
                    firstInstallTime = firstInstall,
                    lastUpdateTime = lastUpdate
                )
            }.sortedBy { it.appName.lowercase() }

            Result.success(apps)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getAppDetails(packageName: String): Result<AppDetailInfo> = withContext(ioDispatcher) {
        try {
            val pm = appContext.packageManager
            val pkgInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                return@withContext Result.failure(e)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }

            val appInfo = pkgInfo.applicationInfo
            val appName = try {
                appInfo?.loadLabel(pm)?.toString() ?: packageName
            } catch (_: Exception) {
                packageName
            }
            val isSystem = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
            val isLaunchable = try {
                pm.getLaunchIntentForPackage(packageName) != null
            } catch (_: Exception) {
                false
            }
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            val versionName = pkgInfo.versionName
            val targetSdk = appInfo?.targetSdkVersion
            val minSdk = appInfo?.minSdkVersion
            val firstInstall = pkgInfo.firstInstallTime
            val lastUpdate = pkgInfo.lastUpdateTime

            // Installer Source
            val installerPackageName: String? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val installSourceInfo = pm.getInstallSourceInfo(packageName)
                    installSourceInfo.installingPackageName ?: installSourceInfo.initiatingPackageName
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstallerPackageName(packageName)
                }
            } catch (_: Exception) {
                null
            }

            // Permissions
            val requestedPerms = pkgInfo.requestedPermissions ?: emptyArray()
            val requestedFlags = pkgInfo.requestedPermissionsFlags ?: intArrayOf()
            val permissions = requestedPerms.mapIndexed { index, permName ->
                val isGranted = if (index < requestedFlags.size) {
                    (requestedFlags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                } else {
                    false
                }
                val permInfo = try {
                    pm.getPermissionInfo(permName, 0)
                } catch (_: Exception) {
                    null
                }
                val label = permInfo?.loadLabel(pm)?.toString()
                val description = permInfo?.loadDescription(pm)?.toString()
                @Suppress("DEPRECATION")
                val type = if (permInfo == null) {
                    PermissionType.UNKNOWN
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    when (permInfo.protection) {
                        PermissionInfo.PROTECTION_DANGEROUS -> PermissionType.RUNTIME
                        PermissionInfo.PROTECTION_SIGNATURE -> PermissionType.SIGNATURE
                        PermissionInfo.PROTECTION_NORMAL -> PermissionType.NORMAL
                        else -> {
                            if ((permInfo.protectionFlags and PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0 ||
                                (permInfo.protectionFlags and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
                                PermissionType.SPECIAL
                            } else {
                                PermissionType.UNKNOWN
                            }
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    when (permInfo.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE) {
                        PermissionInfo.PROTECTION_DANGEROUS -> PermissionType.RUNTIME
                        PermissionInfo.PROTECTION_SIGNATURE -> PermissionType.SIGNATURE
                        PermissionInfo.PROTECTION_NORMAL -> PermissionType.NORMAL
                        else -> {
                            if ((permInfo.protectionLevel and PermissionInfo.PROTECTION_FLAG_SYSTEM) != 0 ||
                                (permInfo.protectionLevel and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
                                PermissionType.SPECIAL
                            } else {
                                PermissionType.UNKNOWN
                            }
                        }
                    }
                }
                val isMajor = isMajorPermission(permName)
                AppPermission(
                    name = permName,
                    label = label,
                    description = description,
                    isGranted = isGranted,
                    type = type,
                    isMajor = isMajor
                )
            }.sortedWith(
                compareByDescending<AppPermission> { it.isMajor }
                    .thenByDescending { it.type == PermissionType.RUNTIME }
                    .thenByDescending { it.isGranted }
                    .thenBy { it.name }
            )

            // Storage Stats
            val storageResult: AppStorageResult = try {
                val storageStatsManager = appContext.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                if (storageStatsManager == null || appInfo == null) {
                    AppStorageResult.Unavailable("Storage statistics service is unavailable")
                } else {
                    val uuid = appInfo.storageUuid ?: StorageManager.UUID_DEFAULT
                    val stats = storageStatsManager.queryStatsForPackage(uuid, packageName, Process.myUserHandle())
                    AppStorageResult.Success(
                        appBytes = stats.appBytes,
                        dataBytes = stats.dataBytes,
                        cacheBytes = stats.cacheBytes,
                        totalBytes = stats.appBytes + stats.dataBytes + stats.cacheBytes
                    )
                }
            } catch (_: SecurityException) {
                AppStorageResult.PermissionRequired
            } catch (e: Exception) {
                AppStorageResult.Unavailable(e.message ?: "Failed to retrieve storage statistics")
            }

            Result.success(
                AppDetailInfo(
                    packageName = packageName,
                    appName = appName,
                    versionName = versionName,
                    versionCode = versionCode,
                    minSdkVersion = minSdk,
                    targetSdkVersion = targetSdk,
                    isSystemApp = isSystem,
                    isLaunchable = isLaunchable,
                    firstInstallTime = firstInstall,
                    lastUpdateTime = lastUpdate,
                    installerPackageName = installerPackageName,
                    permissions = permissions,
                    storageResult = storageResult
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isMajorPermission(permName: String): Boolean {
        val upper = permName.uppercase()
        return upper.contains("CAMERA") ||
            upper.contains("STORAGE") ||
            upper.contains("MEDIA") ||
            upper.contains("VIBRAT") ||
            upper.contains("LOCATION") ||
            upper.contains("RECORD_AUDIO") ||
            upper.contains("MICROPHONE") ||
            upper.contains("PHONE") ||
            upper.contains("CALL") ||
            upper.contains("SMS") ||
            upper.contains("CONTACT") ||
            upper.contains("CALENDAR") ||
            upper.contains("NOTIFICATION") ||
            upper.contains("BLUETOOTH") ||
            upper.contains("SENSOR") ||
            upper.contains("ACTIVITY_RECOGNITION") ||
            upper.contains("BIOMETRIC") ||
            upper.contains("FINGERPRINT") ||
            upper.contains("SYSTEM_ALERT_WINDOW") ||
            upper.contains("INSTALL_PACKAGES") ||
            upper.contains("USAGE_STATS")
    }
}
