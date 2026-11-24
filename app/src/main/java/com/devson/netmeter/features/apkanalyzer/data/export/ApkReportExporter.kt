package com.devson.netmeter.features.apkanalyzer.data.export

import com.devson.netmeter.features.apkanalyzer.domain.model.ApkAnalysisResult
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serializes APK analysis results into formatted JSON reports for document export.
 */
class ApkReportExporter {

    fun exportToJson(result: ApkAnalysisResult, outputStream: OutputStream) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
        val analyzedAtIso = dateFormat.format(Date(result.analyzedAt))

        val writer = OutputStreamWriter(outputStream, StandardCharsets.UTF_8)
        val sb = StringBuilder()

        sb.append("{\n")
        sb.append("  \"reportVersion\": \"1.0.0\",\n")
        sb.append("  \"tool\": \"NetMeter APK Analyzer\",\n")
        sb.append("  \"analysisTimestamp\": \"$analyzedAtIso\",\n")

        // Metadata
        val meta = result.metadata
        sb.append("  \"application\": {\n")
        sb.append("    \"fileName\": \"${escapeJson(meta.fileName)}\",\n")
        sb.append("    \"packageName\": \"${escapeJson(meta.packageName)}\",\n")
        sb.append("    \"appName\": \"${escapeJson(meta.appName)}\",\n")
        sb.append("    \"versionName\": ${meta.versionName?.let { "\"${escapeJson(it)}\"" } ?: "null"},\n")
        sb.append("    \"versionCode\": ${meta.versionCode},\n")
        sb.append("    \"fileSizeBytes\": ${meta.fileSizeBytes},\n")
        sb.append("    \"minSdkVersion\": ${meta.minSdkVersion ?: "null"},\n")
        sb.append("    \"targetSdkVersion\": ${meta.targetSdkVersion ?: "null"},\n")
        sb.append("    \"compileSdkVersion\": ${meta.compileSdkVersion ?: "null"},\n")
        sb.append("    \"installLocation\": \"${escapeJson(meta.installLocation)}\",\n")
        sb.append("    \"isDebuggable\": ${meta.isDebuggable},\n")
        sb.append("    \"allowBackup\": ${meta.allowBackup},\n")
        sb.append("    \"isTestOnly\": ${meta.isTestOnly},\n")
        sb.append("    \"extractNativeLibs\": ${meta.extractNativeLibs ?: "null"},\n")
        sb.append("    \"usesCleartextTraffic\": ${meta.usesCleartextTraffic ?: "null"},\n")
        sb.append("    \"isSplitApk\": ${meta.isSplitApk},\n")
        sb.append("    \"splitName\": ${meta.splitName?.let { "\"${escapeJson(it)}\"" } ?: "null"},\n")
        sb.append("    \"supportedAbis\": [${meta.supportedAbis.joinToString { "\"${escapeJson(it)}\"" }}],\n")
        sb.append("    \"hardwareFeatures\": [${meta.hardwareFeatures.joinToString { "\"${escapeJson(it)}\"" }}]\n")
        sb.append("  },\n")

        // Permissions
        val permSum = result.permissionSummary
        sb.append("  \"permissions\": {\n")
        sb.append("    \"total\": ${permSum.totalCount},\n")
        sb.append("    \"dangerous\": ${permSum.dangerousCount},\n")
        sb.append("    \"normal\": ${permSum.normalCount},\n")
        sb.append("    \"signature\": ${permSum.signatureCount},\n")
        sb.append("    \"custom\": ${permSum.customCount},\n")
        sb.append("    \"declaredList\": [\n")
        result.permissions.forEachIndexed { i, p ->
            sb.append("      {\n")
            sb.append("        \"name\": \"${escapeJson(p.name)}\",\n")
            sb.append("        \"label\": \"${escapeJson(p.label.orEmpty())}\",\n")
            sb.append("        \"category\": \"${p.category.name}\",\n")
            sb.append("        \"protection\": \"${p.protection.name}\",\n")
            sb.append("        \"isCustom\": ${p.isCustom}\n")
            sb.append("      }${if (i < result.permissions.lastIndex) "," else ""}\n")
        }
        sb.append("    ]\n")
        sb.append("  },\n")

        // Components
        val compSum = result.componentsSummary
        sb.append("  \"components\": {\n")
        sb.append("    \"activitiesCount\": ${compSum.activitiesCount},\n")
        sb.append("    \"servicesCount\": ${compSum.servicesCount},\n")
        sb.append("    \"receiversCount\": ${compSum.receiversCount},\n")
        sb.append("    \"providersCount\": ${compSum.providersCount},\n")
        sb.append("    \"exportedCount\": ${compSum.exportedCount},\n")
        sb.append("    \"exportedWithoutPermission\": ${compSum.exportedWithoutPermissionCount},\n")
        sb.append("    \"list\": [\n")
        result.components.forEachIndexed { i, c ->
            sb.append("      {\n")
            sb.append("        \"name\": \"${escapeJson(c.name)}\",\n")
            sb.append("        \"className\": \"${escapeJson(c.className)}\",\n")
            sb.append("        \"type\": \"${c.type.name}\",\n")
            sb.append("        \"isExported\": ${c.isExported},\n")
            sb.append("        \"isEnabled\": ${c.isEnabled},\n")
            sb.append("        \"permission\": ${c.permission?.let { "\"${escapeJson(it)}\"" } ?: "null"},\n")
            sb.append("        \"process\": ${c.process?.let { "\"${escapeJson(it)}\"" } ?: "null"},\n")
            sb.append("        \"directBootAware\": ${c.isDirectBootAware}\n")
            sb.append("      }${if (i < result.components.lastIndex) "," else ""}\n")
        }
        sb.append("    ]\n")
        sb.append("  },\n")

        // Signature
        val sig = result.signature
        sb.append("  \"signature\": {\n")
        sb.append("    \"isSigned\": ${sig.isSigned},\n")
        sb.append("    \"isValid\": ${sig.isSignatureValid},\n")
        sb.append("    \"schemes\": [${sig.supportedSchemes.joinToString { "\"${it.name}\"" }}],\n")
        sb.append("    \"certificates\": [\n")
        sig.certificates.forEachIndexed { i, cert ->
            sb.append("      {\n")
            sb.append("        \"subject\": \"${escapeJson(cert.subjectDn)}\",\n")
            sb.append("        \"issuer\": \"${escapeJson(cert.issuerDn)}\",\n")
            sb.append("        \"serialNumber\": \"${escapeJson(cert.serialNumber)}\",\n")
            sb.append("        \"sha256\": \"${cert.sha256Fingerprint}\",\n")
            sb.append("        \"sha1\": \"${cert.sha1Fingerprint}\",\n")
            sb.append("        \"validFrom\": \"${dateFormat.format(Date(cert.validFrom))}\",\n")
            sb.append("        \"validTo\": \"${dateFormat.format(Date(cert.validTo))}\",\n")
            sb.append("        \"isExpired\": ${cert.isExpired},\n")
            sb.append("        \"isSelfSigned\": ${cert.isSelfSigned},\n")
            sb.append("        \"algorithm\": \"${escapeJson(cert.publicKeyAlgorithm)}\",\n")
            sb.append("        \"keySize\": ${cert.publicKeySize ?: "null"}\n")
            sb.append("      }${if (i < sig.certificates.lastIndex) "," else ""}\n")
        }
        sb.append("    ]\n")
        sb.append("  },\n")

        // Archive and DEX
        val arch = result.archive
        sb.append("  \"archive\": {\n")
        sb.append("    \"totalEntries\": ${arch.totalEntries},\n")
        sb.append("    \"uncompressedBytes\": ${arch.totalUncompressedBytes},\n")
        sb.append("    \"compressedBytes\": ${arch.totalCompressedBytes},\n")
        sb.append("    \"dexCount\": ${arch.dexFiles.size},\n")
        sb.append("    \"totalClasses\": ${arch.totalClasses},\n")
        sb.append("    \"totalMethods\": ${arch.totalMethods},\n")
        sb.append("    \"nativeLibraries\": [${arch.nativeLibraries.joinToString { "\"${escapeJson(it)}\"" }}],\n")
        sb.append("    \"supportedAbis\": [${arch.supportedAbis.joinToString { "\"${escapeJson(it)}\"" }}]\n")
        sb.append("  },\n")

        // Security Findings
        val sec = result.security
        sb.append("  \"security\": {\n")
        sb.append("    \"highRiskCount\": ${sec.highRiskCount},\n")
        sb.append("    \"warningCount\": ${sec.warningCount},\n")
        sb.append("    \"infoCount\": ${sec.infoCount},\n")
        sb.append("    \"disclaimer\": \"${escapeJson(sec.disclaimer)}\",\n")
        sb.append("    \"findings\": [\n")
        sec.findings.forEachIndexed { i, f ->
            sb.append("      {\n")
            sb.append("        \"id\": \"${f.id}\",\n")
            sb.append("        \"title\": \"${escapeJson(f.title)}\",\n")
            sb.append("        \"severity\": \"${f.severity.name}\",\n")
            sb.append("        \"explanation\": \"${escapeJson(f.explanation)}\",\n")
            sb.append("        \"whyItMatters\": \"${escapeJson(f.whyItMatters)}\",\n")
            sb.append("        \"relevantEntry\": \"${escapeJson(f.relevantEntry)}\",\n")
            sb.append("        \"recommendation\": \"${escapeJson(f.recommendation)}\"\n")
            sb.append("      }${if (i < sec.findings.lastIndex) "," else ""}\n")
        }
        sb.append("    ]\n")
        sb.append("  },\n")

        // Limitations
        sb.append("  \"limitations\": [\n")
        result.limitations.forEachIndexed { i, lim ->
            sb.append("    \"${escapeJson(lim)}\"${if (i < result.limitations.lastIndex) "," else ""}\n")
        }
        sb.append("  ]\n")

        sb.append("}\n")

        writer.write(sb.toString())
        writer.flush()
    }

    private fun escapeJson(str: String): String {
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\u000C", "\\f")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
