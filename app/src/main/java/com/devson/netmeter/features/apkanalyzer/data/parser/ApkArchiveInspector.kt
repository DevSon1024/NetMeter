package com.devson.netmeter.features.apkanalyzer.data.parser

import com.devson.netmeter.features.apkanalyzer.domain.model.ApkArchiveInfo
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkDirectoryEntry
import com.devson.netmeter.features.apkanalyzer.domain.model.DexInfo
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Robust ZIP archive inspector for APK files.
 * Provides zip bomb safeguards, path traversal validation, native library detection,
 * and passes internal DEX/manifest streams to respective parsers.
 */
class ApkArchiveInspector(
    private val dexParser: DexFileParser = DexFileParser()
) {

    companion object {
        private const val MAX_ARCHIVE_ENTRIES = 50_000
        private const val MAX_UNCOMPRESSED_CEILING_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB
    }

    data class InspectionResult(
        val archiveInfo: ApkArchiveInfo,
        val manifestBytes: ByteArray?,
        val isSplitApk: Boolean,
        val splitName: String?
    )

    fun inspect(apkFile: File): InspectionResult {
        var totalEntries = 0
        var totalCompressed = 0L
        var totalUncompressed = 0L
        var hasManifest = false
        var hasResourcesArsc = false
        var manifestBytes: ByteArray? = null

        val dexList = mutableListOf<DexInfo>()
        val nativeLibs = mutableListOf<String>()
        val abis = mutableSetOf<String>()

        val dirCounts = mutableMapOf<String, Int>()
        val dirUncompressed = mutableMapOf<String, Long>()
        val dirCompressed = mutableMapOf<String, Long>()

        var isSplitApk = apkFile.name.startsWith("split_") || apkFile.name.contains(".split.")
        var splitName: String? = if (isSplitApk) apkFile.name.substringBefore(".apk") else null

        ZipFile(apkFile).use { zip ->
            val entries = zip.entries()

            while (entries.hasMoreElements()) {
                val entry: ZipEntry = entries.nextElement()
                totalEntries++

                if (totalEntries > MAX_ARCHIVE_ENTRIES) {
                    break // Prevent zip bomb processing
                }

                val name = entry.name
                // Validate path traversal
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    continue
                }

                val size = entry.size.coerceAtLeast(0)
                val cSize = entry.compressedSize.coerceAtLeast(0)

                totalUncompressed += size
                totalCompressed += cSize

                if (totalUncompressed > MAX_UNCOMPRESSED_CEILING_BYTES) {
                    break // Prevent decompression bombs
                }

                // Check split indicator
                if (name.startsWith("split_config.") || name.contains(".split.")) {
                    isSplitApk = true
                    splitName = name.substringBefore(".apk")
                }

                // Track directory breakdown
                val topLevelDir = when {
                    name.startsWith("res/") -> "res/"
                    name.startsWith("assets/") -> "assets/"
                    name.startsWith("lib/") -> "lib/"
                    name.startsWith("META-INF/") -> "META-INF/"
                    name.startsWith("kotlin/") -> "kotlin/"
                    name.contains("/") -> name.substringBefore("/") + "/"
                    else -> "root/"
                }

                dirCounts[topLevelDir] = (dirCounts[topLevelDir] ?: 0) + 1
                dirUncompressed[topLevelDir] = (dirUncompressed[topLevelDir] ?: 0L) + size
                dirCompressed[topLevelDir] = (dirCompressed[topLevelDir] ?: 0L) + cSize

                // Special files
                if (name == "AndroidManifest.xml") {
                    hasManifest = true
                    if (manifestBytes == null && size in 1..(10 * 1024 * 1024)) {
                        try {
                            manifestBytes = zip.getInputStream(entry).use { it.readBytes() }
                        } catch (_: Exception) {
                            // Ignore read error
                        }
                    }
                } else if (name == "resources.arsc") {
                    hasResourcesArsc = true
                } else if (name.endsWith(".dex") && !name.contains("/")) {
                    try {
                        zip.getInputStream(entry).use { stream ->
                            val dexInfo = dexParser.parse(name, stream, size)
                            dexList.add(dexInfo)
                        }
                    } catch (_: Exception) {
                        dexList.add(
                            DexInfo(
                                fileName = name,
                                version = "Error",
                                sizeBytes = size,
                                classCount = 0,
                                methodCount = 0,
                                fieldCount = 0
                            )
                        )
                    }
                } else if (name.startsWith("lib/") && name.endsWith(".so")) {
                    val parts = name.split('/')
                    if (parts.size >= 3) {
                        val abi = parts[1]
                        val libName = parts.last()
                        abis.add(abi)
                        nativeLibs.add(libName)
                    }
                }
            }
        }

        dexList.sortBy { it.fileName }

        val directoryBreakdown = dirCounts.keys.sorted().map { dir ->
            ApkDirectoryEntry(
                name = dir,
                fileCount = dirCounts[dir] ?: 0,
                uncompressedSizeBytes = dirUncompressed[dir] ?: 0L,
                compressedSizeBytes = dirCompressed[dir] ?: 0L
            )
        }

        val totalClasses = dexList.sumOf { it.classCount }
        val totalMethods = dexList.sumOf { it.methodCount }

        val archiveInfo = ApkArchiveInfo(
            totalEntries = totalEntries,
            totalUncompressedBytes = totalUncompressed,
            totalCompressedBytes = totalCompressed,
            dexFiles = dexList,
            totalClasses = totalClasses,
            totalMethods = totalMethods,
            nativeLibraries = nativeLibs.distinct().sorted(),
            supportedAbis = abis.sorted(),
            directoryBreakdown = directoryBreakdown,
            hasManifest = hasManifest,
            hasResourcesArsc = hasResourcesArsc
        )

        return InspectionResult(
            archiveInfo = archiveInfo,
            manifestBytes = manifestBytes,
            isSplitApk = isSplitApk,
            splitName = splitName
        )
    }
}
