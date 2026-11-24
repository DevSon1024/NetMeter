package com.devson.netmeter.features.apkanalyzer.domain.model

data class ApkDirectoryEntry(
    val name: String,
    val fileCount: Int,
    val uncompressedSizeBytes: Long,
    val compressedSizeBytes: Long
)

data class DexInfo(
    val fileName: String,
    val version: String,
    val sizeBytes: Long,
    val classCount: Int,
    val methodCount: Int,
    val fieldCount: Int,
    val sampleClasses: List<String> = emptyList()
)

data class ApkArchiveInfo(
    val totalEntries: Int,
    val totalUncompressedBytes: Long,
    val totalCompressedBytes: Long,
    val dexFiles: List<DexInfo>,
    val totalClasses: Int,
    val totalMethods: Int,
    val nativeLibraries: List<String>,
    val supportedAbis: List<String>,
    val directoryBreakdown: List<ApkDirectoryEntry>,
    val hasManifest: Boolean,
    val hasResourcesArsc: Boolean
)
