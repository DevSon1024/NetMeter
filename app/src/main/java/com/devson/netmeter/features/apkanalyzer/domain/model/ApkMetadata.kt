package com.devson.netmeter.features.apkanalyzer.domain.model

data class ApkMetadata(
    val fileName: String,
    val fileSizeBytes: Long,
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdkVersion: Int?,
    val targetSdkVersion: Int?,
    val compileSdkVersion: Int?,
    val appLabel: String,
    val description: String?,
    val installLocation: String,
    val isDebuggable: Boolean,
    val allowBackup: Boolean,
    val isTestOnly: Boolean,
    val extractNativeLibs: Boolean?,
    val category: String?,
    val usesCleartextTraffic: Boolean?,
    val hardwareFeatures: List<String>,
    val supportedAbis: List<String>,
    val isSplitApk: Boolean,
    val splitName: String?,
    val appIconBytes: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ApkMetadata

        if (fileName != other.fileName) return false
        if (packageName != other.packageName) return false
        if (versionCode != other.versionCode) return false
        if (fileSizeBytes != other.fileSizeBytes) return false
        return true
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + packageName.hashCode()
        result = 31 * result + versionCode.hashCode()
        result = 31 * result + fileSizeBytes.hashCode()
        return result
    }
}
