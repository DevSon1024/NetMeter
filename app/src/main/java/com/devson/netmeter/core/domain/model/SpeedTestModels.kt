package com.devson.netmeter.core.domain.model

import java.net.URI

enum class SpeedTestPhase(val label: String) {
    IDLE("Idle"),
    LATENCY("Testing Latency"),
    DOWNLOAD("Testing Download"),
    UPLOAD("Testing Upload"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    ERROR("Failed")
}

enum class SpeedTestErrorType(val userMessage: String) {
    NO_CONNECTIVITY("No internet connection available. Check your Wi-Fi or mobile network."),
    TIMEOUT("Speed test timed out. The test server took too long to respond."),
    SERVER_ERROR("Speed test server returned an error response."),
    NETWORK_IO_ERROR("Network connection was interrupted during the test."),
    CANCELLED("Speed test was cancelled by user."),
    UNKNOWN("An unexpected error occurred during the speed test.")
}

data class SpeedTestConfig(
    val downloadUrl: String = DEFAULT_DOWNLOAD_URL,
    val uploadUrl: String = DEFAULT_UPLOAD_URL,
    val latencyUrl: String = DEFAULT_LATENCY_URL,
    val maxDownloadBytes: Long = DEFAULT_MAX_DOWNLOAD_BYTES,
    val maxUploadBytes: Long = DEFAULT_MAX_UPLOAD_BYTES,
    val latencyPingsCount: Int = DEFAULT_LATENCY_PINGS,
    val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    val phaseTimeLimitMs: Long = DEFAULT_PHASE_TIME_LIMIT_MS
) {
    val hostDisplayName: String
        get() = try {
            URI(downloadUrl).host ?: "speed.cloudflare.com"
        } catch (_: Exception) {
            "speed.cloudflare.com"
        }

    companion object {
        const val DEFAULT_DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=10485760"
        const val DEFAULT_UPLOAD_URL = "https://speed.cloudflare.com/__up"
        const val DEFAULT_LATENCY_URL = "https://speed.cloudflare.com/__down?bytes=0"
        const val DEFAULT_MAX_DOWNLOAD_BYTES = 10L * 1024 * 1024 // 10 MB payload ceiling
        const val DEFAULT_MAX_UPLOAD_BYTES = 5L * 1024 * 1024   // 5 MB payload ceiling
        const val DEFAULT_LATENCY_PINGS = 4
        const val DEFAULT_CONNECT_TIMEOUT_MS = 8000
        const val DEFAULT_READ_TIMEOUT_MS = 12000
        const val DEFAULT_PHASE_TIME_LIMIT_MS = 10000L // 10 seconds timeout per phase

        val DEFAULT = SpeedTestConfig()
    }
}

data class LatencyMeasurement(
    val samplesMs: List<Double>,
    val averageMs: Double,
    val minMs: Double,
    val maxMs: Double,
    val jitterMs: Double
) {
    companion object {
        val ZERO = LatencyMeasurement(
            samplesMs = emptyList(),
            averageMs = 0.0,
            minMs = 0.0,
            maxMs = 0.0,
            jitterMs = 0.0
        )
    }
}

data class ThroughputMeasurement(
    val bytesTransferred: Long,
    val durationMs: Long,
    val mbps: Double
) {
    companion object {
        val ZERO = ThroughputMeasurement(0L, 0L, 0.0)
    }
}

data class SpeedTestProgress(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val progressFraction: Float = 0f,
    val currentMbps: Double = 0.0,
    val downloadSpeedMbps: Double = 0.0,
    val uploadSpeedMbps: Double = 0.0,
    val currentLatencyMs: Double = 0.0,
    val averageLatencyMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val bytesTransferred: Long = 0L,
    val totalBytesConsumed: Long = 0L,
    val statusText: String = "Tap start to test connection speed",
    val error: SpeedTestErrorType? = null
)

data class SpeedTestFinalReport(
    val downloadMbps: Double,
    val uploadMbps: Double,
    val latency: LatencyMeasurement,
    val totalBytesConsumed: Long,
    val durationMillis: Long,
    val timestampMillis: Long,
    val endpointHost: String
)

sealed interface SpeedTestEvent {
    data class ProgressUpdate(val progress: SpeedTestProgress) : SpeedTestEvent
    data class Completed(val report: SpeedTestFinalReport) : SpeedTestEvent
    data class Failed(val errorType: SpeedTestErrorType, val message: String) : SpeedTestEvent
    data object Cancelled : SpeedTestEvent
}
