package com.devson.netmeter.core.permission

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

interface UsagePermissionChecker {
    fun checkPermission(context: Context): UsagePermissionStatus
}

object PermissionLogic {
    fun decideStatusFromAppOpsMode(mode: Int): UsagePermissionStatus {
        return if (mode == AppOpsManager.MODE_ALLOWED) {
            UsagePermissionStatus.Granted
        } else {
            UsagePermissionStatus.Required
        }
    }
}

class DefaultUsagePermissionChecker : UsagePermissionChecker {
    @Suppress("DEPRECATION")
    override fun checkPermission(context: Context): UsagePermissionStatus {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return UsagePermissionStatus.Error("AppOpsManager service is not available on this device")

            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpRawNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }

            PermissionLogic.decideStatusFromAppOpsMode(mode)
        } catch (e: SecurityException) {
            UsagePermissionStatus.Error("Security exception checking usage permission: ${e.message}")
        } catch (e: Exception) {
            UsagePermissionStatus.Error("Unexpected error checking usage permission: ${e.message}")
        }
    }
}
