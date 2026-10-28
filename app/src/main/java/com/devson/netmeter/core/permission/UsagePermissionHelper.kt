package com.devson.netmeter.core.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object UsagePermissionHelper {

    fun createPackageSettingsIntent(packageName: String): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun createGenericSettingsIntent(): Intent {
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    fun openUsageAccessSettings(context: Context): Boolean {
        val packageIntent = createPackageSettingsIntent(context.packageName)
        val genericIntent = createGenericSettingsIntent()

        return try {
            context.startActivity(packageIntent)
            true
        } catch (_: Exception) {
            try {
                context.startActivity(genericIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
