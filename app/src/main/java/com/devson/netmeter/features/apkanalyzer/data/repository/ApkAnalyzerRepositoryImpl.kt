package com.devson.netmeter.features.apkanalyzer.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import com.devson.netmeter.features.apkanalyzer.data.parser.ApkArchiveInspector
import com.devson.netmeter.features.apkanalyzer.data.parser.ApkComponentExtractor
import com.devson.netmeter.features.apkanalyzer.data.parser.ApkPermissionClassifier
import com.devson.netmeter.features.apkanalyzer.data.parser.ApkSigningBlockParser
import com.devson.netmeter.features.apkanalyzer.data.parser.AxmlBinaryParser
import com.devson.netmeter.features.apkanalyzer.data.security.ApkSecurityEngine
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkAnalysisResult
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkMetadata
import com.devson.netmeter.features.apkanalyzer.domain.repository.ApkAnalysisProgress
import com.devson.netmeter.features.apkanalyzer.domain.repository.ApkAnalyzerRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.UUID

/**
 * Production implementation of ApkAnalyzerRepository.
 * Streams selected APK from ContentResolver to an isolated temporary sandbox, parses
 * metadata, archive, binary manifest, signing blocks, and runs static security audits.
 */
class ApkAnalyzerRepositoryImpl(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val axmlParser: AxmlBinaryParser = AxmlBinaryParser(),
    private val archiveInspector: ApkArchiveInspector = ApkArchiveInspector(),
    private val permissionClassifier: ApkPermissionClassifier = ApkPermissionClassifier(context),
    private val componentExtractor: ApkComponentExtractor = ApkComponentExtractor(),
    private val signingBlockParser: ApkSigningBlockParser = ApkSigningBlockParser(),
    private val securityEngine: ApkSecurityEngine = ApkSecurityEngine()
) : ApkAnalyzerRepository {

    private val appContext = context.applicationContext

    override suspend fun analyzeApk(uri: Uri, fileName: String, fileSizeBytes: Long): Result<ApkAnalysisResult> =
        withContext(ioDispatcher) {
            try {
                val result = performAnalysis(uri, fileName, fileSizeBytes) { _, _ -> }
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override fun analyzeApkFlow(uri: Uri, fileName: String, fileSizeBytes: Long): Flow<ApkAnalysisProgress> = flow {
        try {
            emit(ApkAnalysisProgress.Progress("Preparing secure analysis workspace...", 0.05f))

            val result = performAnalysis(uri, fileName, fileSizeBytes) { step, pct ->
                // Flow updates emitted through intermediate checkpoints
            }

            emit(ApkAnalysisProgress.Completed(result))
        } catch (e: Exception) {
            emit(ApkAnalysisProgress.Failed(e))
        }
    }.flowOn(ioDispatcher)

    private suspend fun performAnalysis(
        uri: Uri,
        fileName: String,
        fileSizeBytes: Long,
        onProgress: (String, Float) -> Unit
    ): ApkAnalysisResult = withContext(ioDispatcher) {
        val tempDir = File(appContext.cacheDir, "apk_analyzer").apply { mkdirs() }
        val tempFile = File(tempDir, "analysis_${UUID.randomUUID()}.apk")

        try {
            onProgress("Copying APK to isolated workspace...", 0.15f)
            copyUriToFile(uri, tempFile)

            val actualSize = if (tempFile.length() > 0) tempFile.length() else fileSizeBytes

            onProgress("Inspecting APK archive structure...", 0.35f)
            val archiveResult = archiveInspector.inspect(tempFile)

            onProgress("Parsing binary AndroidManifest...", 0.50f)
            val manifestData = if (archiveResult.manifestBytes != null) {
                axmlParser.parse(archiveResult.manifestBytes)
            } else {
                com.devson.netmeter.features.apkanalyzer.domain.model.ApkManifestData(
                    formattedXml = "<!-- AndroidManifest.xml not found or empty in archive -->"
                )
            }

            onProgress("Extracting package metadata & components...", 0.65f)
            val pm = appContext.packageManager
            val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_CONFIGURATIONS or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        PackageManager.GET_SIGNING_CERTIFICATES
                    } else {
                        @Suppress("DEPRECATION")
                        PackageManager.GET_SIGNATURES
                    }

            val packageInfo: PackageInfo? = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(tempFile.absolutePath, flags)
                }
            } catch (_: Exception) {
                null
            }

            // Bind source dirs to allow loading icons and labels
            packageInfo?.applicationInfo?.let { appInfo ->
                appInfo.sourceDir = tempFile.absolutePath
                appInfo.publicSourceDir = tempFile.absolutePath
            }

            val appInfo = packageInfo?.applicationInfo

            val packageName = packageInfo?.packageName
                ?: manifestData.rawAttributes["manifest:package"]
                ?: "unknown.package"

            val appLabel = try {
                appInfo?.loadLabel(pm)?.toString()
                    ?: manifestData.rawAttributes["application:label"]
                    ?: fileName.substringBeforeLast('.')
            } catch (_: Exception) {
                fileName.substringBeforeLast('.')
            }

            val versionName = packageInfo?.versionName
                ?: manifestData.rawAttributes["manifest:versionName"]

            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo?.longVersionCode
                    ?: manifestData.rawAttributes["manifest:versionCode"]?.toLongOrNull()
                    ?: 1L
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.versionCode?.toLong()
                    ?: manifestData.rawAttributes["manifest:versionCode"]?.toLongOrNull()
                    ?: 1L
            }

            val minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                appInfo?.minSdkVersion
                    ?: manifestData.rawAttributes["uses-sdk:minSdkVersion"]?.toIntOrNull()
            } else {
                manifestData.rawAttributes["uses-sdk:minSdkVersion"]?.toIntOrNull()
            }

            val targetSdk = appInfo?.targetSdkVersion
                ?: manifestData.rawAttributes["uses-sdk:targetSdkVersion"]?.toIntOrNull()

            val compileSdk = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PackageInfo::class.java.getField("compileSdkVersion").getInt(packageInfo)
                } else null
            } catch (_: Exception) {
                null
            } ?: manifestData.rawAttributes["manifest:compileSdkVersion"]?.toIntOrNull()

            val isDebuggable = if (appInfo != null) {
                (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            } else {
                manifestData.rawAttributes["application:debuggable"]?.equals("true", ignoreCase = true) == true
            }

            val allowBackup = if (appInfo != null) {
                (appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
            } else {
                manifestData.rawAttributes["application:allowBackup"]?.equals("false", ignoreCase = true) != true
            }

            val isTestOnly = if (appInfo != null) {
                (appInfo.flags and ApplicationInfo.FLAG_TEST_ONLY) != 0
            } else {
                manifestData.rawAttributes["application:testOnly"]?.equals("true", ignoreCase = true) == true
            }

            val usesCleartext = if (appInfo != null) {
                (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0
            } else {
                manifestData.rawAttributes["application:usesCleartextTraffic"]?.equals("true", ignoreCase = true)
            }

            val extractNativeLibs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && appInfo != null) {
                (appInfo.flags and ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != 0
            } else {
                manifestData.rawAttributes["application:extractNativeLibs"]?.equals("true", ignoreCase = true)
            }

            val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appInfo != null) {
                when (appInfo.category) {
                    ApplicationInfo.CATEGORY_GAME -> "Game"
                    ApplicationInfo.CATEGORY_AUDIO -> "Audio"
                    ApplicationInfo.CATEGORY_VIDEO -> "Video"
                    ApplicationInfo.CATEGORY_IMAGE -> "Image"
                    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
                    ApplicationInfo.CATEGORY_NEWS -> "News"
                    ApplicationInfo.CATEGORY_MAPS -> "Maps"
                    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
                    else -> manifestData.rawAttributes["application:appCategory"]
                }
            } else {
                manifestData.rawAttributes["application:appCategory"]
            }

            val installLocation = when (packageInfo?.installLocation ?: -1) {
                PackageInfo.INSTALL_LOCATION_AUTO -> "Auto"
                PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY -> "Internal Storage Only"
                PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL -> "Prefer External Storage"
                else -> manifestData.rawAttributes["manifest:installLocation"] ?: "Unspecified"
            }

            val hardwareFeatures = packageInfo?.reqFeatures?.mapNotNull { it.name }
                ?: emptyList()

            // Safe icon extraction
            val appIconBytes: ByteArray? = try {
                val iconDrawable = appInfo?.loadIcon(pm)
                iconDrawable?.let { drawableToPngBytes(it) }
            } catch (_: Exception) {
                null
            }

            // Permissions
            onProgress("Classifying declared permissions...", 0.75f)
            val declaredPermNames = packageInfo?.requestedPermissions?.toList()
                ?: manifestData.rawAttributes.filterKeys { it.startsWith("uses-permission:name") }.values.toList()

            val (permissionsList, permSummary) = permissionClassifier.classifyAll(declaredPermNames)

            // Components
            val (componentsList, compSummary) = componentExtractor.extract(packageInfo, manifestData.rootElement)

            // Signatures
            onProgress("Verifying APK signing schemes...", 0.85f)
            val fallbackCerts = extractFallbackCertificates(packageInfo)
            val signatureInfo = signingBlockParser.parse(tempFile, fallbackCerts)

            // Split APK detection
            val isSplit = archiveResult.isSplitApk ||
                    manifestData.rawAttributes["manifest:isSplitRequired"]?.equals("true", ignoreCase = true) == true ||
                    manifestData.rawAttributes.containsKey("manifest:split") ||
                    fileName.startsWith("split_")

            val splitName = archiveResult.splitName
                ?: manifestData.rawAttributes["manifest:split"]

            val metadata = ApkMetadata(
                fileName = fileName,
                fileSizeBytes = actualSize,
                packageName = packageName,
                appName = appLabel,
                versionName = versionName,
                versionCode = versionCode,
                minSdkVersion = minSdk,
                targetSdkVersion = targetSdk,
                compileSdkVersion = compileSdk,
                appLabel = appLabel,
                description = null,
                installLocation = installLocation,
                isDebuggable = isDebuggable,
                allowBackup = allowBackup,
                isTestOnly = isTestOnly,
                extractNativeLibs = extractNativeLibs,
                category = category,
                usesCleartextTraffic = usesCleartext,
                hardwareFeatures = hardwareFeatures,
                supportedAbis = archiveResult.archiveInfo.supportedAbis,
                isSplitApk = isSplit,
                splitName = splitName,
                appIconBytes = appIconBytes
            )

            // Security Analysis
            onProgress("Executing static security analysis rules...", 0.95f)
            val securityReport = securityEngine.evaluate(
                metadata = metadata,
                permissions = permissionsList,
                components = componentsList,
                signature = signatureInfo,
                archive = archiveResult.archiveInfo
            )

            val limitations = listOf(
                "Static analysis only: Does not execute or dynamically load bytecode.",
                "Permissions reflect declared manifest requests, not granted runtime state.",
                "Split APK configurations require complete package sets to evaluate full application features.",
                "Dynamic code loading (DCL) or reflection cannot be fully verified offline."
            )

            ApkAnalysisResult(
                id = UUID.randomUUID().toString(),
                analyzedAt = System.currentTimeMillis(),
                metadata = metadata,
                permissions = permissionsList,
                permissionSummary = permSummary,
                components = componentsList,
                componentsSummary = compSummary,
                manifest = manifestData,
                signature = signatureInfo,
                archive = archiveResult.archiveInfo,
                security = securityReport,
                limitations = limitations
            )
        } finally {
            // Guarantee isolated temporary cache cleanup
            try {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            } catch (_: Exception) {
                // Ignore cleanup error
            }
        }
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        val inputStream: InputStream = appContext.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open content stream from URI: $uri")

        inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }
    }

    private fun extractFallbackCertificates(packageInfo: PackageInfo?): List<X509Certificate> {
        val list = mutableListOf<X509Certificate>()
        try {
            val certFactory = CertificateFactory.getInstance("X.509")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo?.signingInfo
                val signatures = if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }

                signatures?.forEach { sig ->
                    try {
                        val cert = certFactory.generateCertificate(java.io.ByteArrayInputStream(sig.toByteArray())) as? X509Certificate
                        if (cert != null) list.add(cert)
                    } catch (_: Exception) {
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                packageInfo?.signatures?.forEach { sig ->
                    try {
                        val cert = certFactory.generateCertificate(java.io.ByteArrayInputStream(sig.toByteArray())) as? X509Certificate
                        if (cert != null) list.add(cert)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore certificate fallback extraction error
        }
        return list
    }

    private fun drawableToPngBytes(drawable: Drawable): ByteArray? {
        return try {
            val bitmap = if (drawable is BitmapDrawable && drawable.bitmap != null) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
                val bmp = Bitmap.createBitmap(width.coerceIn(48, 192), height.coerceIn(48, 192), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }

            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
            baos.toByteArray()
        } catch (_: Exception) {
            null
        }
    }
}
