package com.devson.netmeter.features.apkanalyzer.data.parser

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkDeclaredPermission
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkPermissionCategory
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkPermissionProtection
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkPermissionSummary

/**
 * Categorizes and classifies APK requested permissions without assuming runtime grant state.
 */
class ApkPermissionClassifier(
    private val context: Context? = null
) {

    companion object {
        private val DANGEROUS_PERMISSIONS = setOf(
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.CAMERA",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.GET_ACCOUNTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.CALL_PHONE",
            "android.permission.ANSWER_PHONE_CALLS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.ADD_VOICEMAIL",
            "android.permission.USE_SIP",
            "android.permission.PROCESS_OUTGOING_CALLS",
            "android.permission.BODY_SENSORS",
            "android.permission.BODY_SENSORS_BACKGROUND",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.RECEIVE_WAP_PUSH",
            "android.permission.RECEIVE_MMS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.ACCESS_MEDIA_LOCATION",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.NEARBY_WIFI_DEVICES",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.ACTIVITY_RECOGNITION"
        )

        private val NORMAL_PERMISSIONS = setOf(
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_NOTIFICATION_POLICY",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
            "android.permission.BROADCAST_STICKY",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_MULTICAST_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.DISABLE_KEYGUARD",
            "android.permission.EXPAND_STATUS_BAR",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.GET_PACKAGE_SIZE",
            "android.permission.INTERNET",
            "android.permission.KILL_BACKGROUND_PROCESSES",
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.NFC",
            "android.permission.READ_SYNC_SETTINGS",
            "android.permission.READ_SYNC_STATS",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.REORDER_TASKS",
            "android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND",
            "android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND",
            "android.permission.REQUEST_DELETE_PACKAGES",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
            "android.permission.SET_ALARM",
            "android.permission.SET_WALLPAPER",
            "android.permission.SET_WALLPAPER_HINTS",
            "android.permission.TRANSMIT_IR",
            "android.permission.USE_BIOMETRIC",
            "android.permission.USE_FINGERPRINT",
            "android.permission.VIBRATE",
            "android.permission.WAKE_LOCK",
            "android.permission.WRITE_SYNC_SETTINGS"
        )

        private val STANDARD_DESCRIPTIONS = mapOf(
            "android.permission.INTERNET" to ("Full Network Access" to "Allows the app to create network sockets and use custom network protocols."),
            "android.permission.ACCESS_NETWORK_STATE" to ("View Network Connections" to "Allows the app to view information about network connections such as which networks exist and are connected."),
            "android.permission.ACCESS_WIFI_STATE" to ("View Wi-Fi Connections" to "Allows the app to view information about Wi-Fi networking."),
            "android.permission.CHANGE_WIFI_STATE" to ("Connect & Disconnect Wi-Fi" to "Allows the app to connect to and disconnect from Wi-Fi access points."),
            "android.permission.READ_EXTERNAL_STORAGE" to ("Read Storage" to "Allows the app to read files and content from device storage."),
            "android.permission.WRITE_EXTERNAL_STORAGE" to ("Modify/Delete Storage" to "Allows the app to write and modify files on device storage."),
            "android.permission.MANAGE_EXTERNAL_STORAGE" to ("All Files Access" to "Grants broad access to external storage across directories."),
            "android.permission.CAMERA" to ("Take Pictures & Videos" to "Allows the app to capture photos and videos using the camera."),
            "android.permission.RECORD_AUDIO" to ("Record Audio" to "Allows the app to record sound using the microphone."),
            "android.permission.ACCESS_FINE_LOCATION" to ("Precise Location" to "Allows the app to obtain precise GPS and network location."),
            "android.permission.ACCESS_COARSE_LOCATION" to ("Approximate Location" to "Allows the app to determine approximate location using cell towers and Wi-Fi."),
            "android.permission.ACCESS_BACKGROUND_LOCATION" to ("Location in Background" to "Allows access to device location while the app is running in the background."),
            "android.permission.READ_CONTACTS" to ("Read Contacts" to "Allows the app to read data about contacts stored on device."),
            "android.permission.WRITE_CONTACTS" to ("Modify Contacts" to "Allows the app to add, modify, or delete contacts."),
            "android.permission.READ_CALL_LOG" to ("Read Call Log" to "Allows the app to read phone call history."),
            "android.permission.READ_PHONE_STATE" to ("Read Phone Status" to "Allows access to phone features such as phone number and cellular network state."),
            "android.permission.CALL_PHONE" to ("Directly Call Numbers" to "Allows calling phone numbers without user confirmation."),
            "android.permission.SEND_SMS" to ("Send SMS Messages" to "Allows sending SMS messages which may incur carrier costs."),
            "android.permission.RECEIVE_SMS" to ("Receive SMS Messages" to "Allows monitoring and processing incoming SMS messages."),
            "android.permission.READ_SMS" to ("Read SMS Messages" to "Allows reading SMS messages stored on the device or SIM card."),
            "android.permission.POST_NOTIFICATIONS" to ("Post Notifications" to "Allows displaying notifications to the user."),
            "android.permission.RECEIVE_BOOT_COMPLETED" to ("Run at Startup" to "Allows the app to start services as soon as the system finishes booting."),
            "android.permission.WAKE_LOCK" to ("Prevent Device Sleeping" to "Allows the app to prevent the processor from sleeping or screen from dimming."),
            "android.permission.SYSTEM_ALERT_WINDOW" to ("Display Over Other Apps" to "Allows drawing windows or overlays on top of other running applications."),
            "android.permission.PACKAGE_USAGE_STATS" to ("Package Usage Stats" to "Allows querying component and network usage statistics across apps."),
            "android.permission.REQUEST_INSTALL_PACKAGES" to ("Request Package Installs" to "Allows requesting installation of package archives.")
        )
    }

    fun classifyAll(permissionNames: List<String>): Pair<List<ApkDeclaredPermission>, ApkPermissionSummary> {
        val pm = context?.packageManager

        val declaredList = permissionNames.distinct().map { permName ->
            val category = resolveCategory(permName)
            val isCustom = !permName.startsWith("android.permission.") && !permName.startsWith("com.android.")

            var label: String? = null
            var description: String? = null
            var protection = resolveProtection(permName, isCustom)

            // 1. Try standard dictionary
            val dictEntry = STANDARD_DESCRIPTIONS[permName]
            if (dictEntry != null) {
                label = dictEntry.first
                description = dictEntry.second
            }

            // 2. Query system PackageManager if available
            if (pm != null && (label == null || description == null)) {
                try {
                    val info = pm.getPermissionInfo(permName, 0)
                    if (label == null) label = info.loadLabel(pm)?.toString()
                    if (description == null) description = info.loadDescription(pm)?.toString()

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        protection = when (info.protection) {
                            PermissionInfo.PROTECTION_DANGEROUS -> ApkPermissionProtection.DANGEROUS_RUNTIME
                            PermissionInfo.PROTECTION_SIGNATURE -> ApkPermissionProtection.SIGNATURE_PRIVILEGED
                            PermissionInfo.PROTECTION_NORMAL -> ApkPermissionProtection.NORMAL_INSTALL
                            else -> protection
                        }
                    }
                } catch (_: Exception) {
                    // Permission not registered on host system
                }
            }

            if (label == null) {
                label = permName.substringAfterLast('.')
                    .replace('_', ' ')
                    .lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }

            if (description == null) {
                description = if (isCustom) {
                    "Custom or third-party permission declared by application components."
                } else {
                    "Declared Android platform permission."
                }
            }

            ApkDeclaredPermission(
                name = permName,
                label = label,
                description = description,
                category = category,
                protection = protection,
                isCustom = isCustom
            )
        }.sortedWith(
            compareByDescending<ApkDeclaredPermission> { it.protection == ApkPermissionProtection.DANGEROUS_RUNTIME }
                .thenBy { it.category.ordinal }
                .thenBy { it.name }
        )

        val dangerousCount = declaredList.count { it.protection == ApkPermissionProtection.DANGEROUS_RUNTIME }
        val normalCount = declaredList.count { it.protection == ApkPermissionProtection.NORMAL_INSTALL }
        val signatureCount = declaredList.count { it.protection == ApkPermissionProtection.SIGNATURE_PRIVILEGED }
        val customCount = declaredList.count { it.protection == ApkPermissionProtection.CUSTOM_UNKNOWN }

        val byCategory = declaredList.groupBy { it.category }.mapValues { it.value.size }

        val summary = ApkPermissionSummary(
            totalCount = declaredList.size,
            dangerousCount = dangerousCount,
            normalCount = normalCount,
            signatureCount = signatureCount,
            customCount = customCount,
            byCategory = byCategory
        )

        return Pair(declaredList, summary)
    }

    private fun resolveCategory(permissionName: String): ApkPermissionCategory {
        val upper = permissionName.uppercase()
        return when {
            upper.contains("INTERNET") || upper.contains("NETWORK") || upper.contains("WIFI") ->
                ApkPermissionCategory.NETWORK

            upper.contains("STORAGE") || upper.contains("MEDIA") || upper.contains("MANAGE_DOCUMENTS") ->
                ApkPermissionCategory.STORAGE

            upper.contains("CAMERA") || upper.contains("AUDIO") || upper.contains("MICROPHONE") ->
                ApkPermissionCategory.CAMERA_MIC

            upper.contains("LOCATION") || upper.contains("GPS") ->
                ApkPermissionCategory.LOCATION

            upper.contains("CONTACTS") || upper.contains("CALENDAR") || upper.contains("ACCOUNTS") ->
                ApkPermissionCategory.CONTACTS_CALENDAR

            upper.contains("PHONE") || upper.contains("CALL") || upper.contains("SMS") || upper.contains("MMS") || upper.contains("SIP") || upper.contains("VOICEMAIL") ->
                ApkPermissionCategory.PHONE_SMS

            upper.contains("NOTIFICATION") ->
                ApkPermissionCategory.NOTIFICATIONS

            upper.contains("BOOT_COMPLETED") || upper.contains("WAKE_LOCK") || upper.contains("FOREGROUND_SERVICE") || upper.contains("BATTERY") || upper.contains("ALARM") ->
                ApkPermissionCategory.BACKGROUND

            upper.contains("SYSTEM_ALERT_WINDOW") || upper.contains("WRITE_SETTINGS") || upper.contains("USAGE_STATS") || upper.contains("INSTALL") || upper.contains("DEVICE_ADMIN") || upper.contains("ACCESSIBILITY") || upper.contains("VPN") || upper.contains("QUERY_ALL_PACKAGES") ->
                ApkPermissionCategory.SYSTEM_SPECIAL

            else -> ApkPermissionCategory.OTHER
        }
    }

    private fun resolveProtection(permName: String, isCustom: Boolean): ApkPermissionProtection {
        return when {
            permName in DANGEROUS_PERMISSIONS -> ApkPermissionProtection.DANGEROUS_RUNTIME
            permName in NORMAL_PERMISSIONS -> ApkPermissionProtection.NORMAL_INSTALL
            permName.startsWith("android.permission.") && (
                    permName.contains("BIND_") || permName.contains("PACKAGE_USAGE") ||
                            permName.contains("INTERNAL") || permName.contains("WRITE_SECURE_SETTINGS") ||
                            permName.contains("MANAGE_USERS") || permName.contains("MASTER_CLEAR")
                    ) -> ApkPermissionProtection.SIGNATURE_PRIVILEGED
            isCustom -> ApkPermissionProtection.CUSTOM_UNKNOWN
            else -> ApkPermissionProtection.NORMAL_INSTALL
        }
    }
}
