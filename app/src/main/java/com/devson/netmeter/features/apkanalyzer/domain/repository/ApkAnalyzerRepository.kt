package com.devson.netmeter.features.apkanalyzer.domain.repository

import android.net.Uri
import com.devson.netmeter.features.apkanalyzer.domain.model.ApkAnalysisResult
import kotlinx.coroutines.flow.Flow

sealed interface ApkAnalysisProgress {
    data class Progress(val step: String, val percentage: Float) : ApkAnalysisProgress
    data class Completed(val result: ApkAnalysisResult) : ApkAnalysisProgress
    data class Failed(val error: Throwable) : ApkAnalysisProgress
}

interface ApkAnalyzerRepository {
    suspend fun analyzeApk(uri: Uri, fileName: String, fileSizeBytes: Long): Result<ApkAnalysisResult>
    fun analyzeApkFlow(uri: Uri, fileName: String, fileSizeBytes: Long): Flow<ApkAnalysisProgress>
}
