package com.devson.netmeter.core.data.speedtest

import com.devson.netmeter.core.domain.model.LatencyMeasurement
import com.devson.netmeter.core.domain.model.SpeedTestConfig
import com.devson.netmeter.core.domain.model.SpeedTestErrorType
import com.devson.netmeter.core.domain.model.SpeedTestEvent
import com.devson.netmeter.core.domain.model.SpeedTestFinalReport
import com.devson.netmeter.core.domain.model.SpeedTestPhase
import com.devson.netmeter.core.domain.model.SpeedTestProgress
import com.devson.netmeter.core.util.SpeedTestCalculator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.UnknownHostException

class HttpUrlConnectionSpeedTestEngine : SpeedTestEngine {

    @Volatile
    private var activeConnection: HttpURLConnection? = null

    override fun executeSpeedTest(config: SpeedTestConfig): Flow<SpeedTestEvent> = flow {
        val overallStartNano = System.nanoTime()
        var totalBytesConsumed = 0L

        try {
            // 1. LATENCY PHASE
            emit(
                SpeedTestEvent.ProgressUpdate(
                    SpeedTestProgress(
                        phase = SpeedTestPhase.LATENCY,
                        progressFraction = 0.05f,
                        statusText = "Measuring HTTP round-trip latency..."
                    )
                )
            )

            val latencySamples = mutableListOf<Double>()
            for (i in 1..config.latencyPingsCount) {
                currentCoroutineContext().ensureActive()
                val sampleMs = measureSingleHttpLatency(config)
                if (sampleMs >= 0.0) {
                    latencySamples.add(sampleMs)
                    val currentAvg = latencySamples.average()
                    val currentJitter = SpeedTestCalculator.calculateJitterMs(latencySamples)
                    val fraction = 0.05f + (0.20f * (i.toFloat() / config.latencyPingsCount))

                    emit(
                        SpeedTestEvent.ProgressUpdate(
                            SpeedTestProgress(
                                phase = SpeedTestPhase.LATENCY,
                                progressFraction = fraction,
                                currentLatencyMs = sampleMs,
                                averageLatencyMs = currentAvg,
                                jitterMs = currentJitter,
                                statusText = "HTTP round-trip latency sample $i of ${config.latencyPingsCount}"
                            )
                        )
                    )
                }
            }

            val finalLatency = SpeedTestCalculator.computeLatency(latencySamples)

            // 2. DOWNLOAD PHASE
            emit(
                SpeedTestEvent.ProgressUpdate(
                    SpeedTestProgress(
                        phase = SpeedTestPhase.DOWNLOAD,
                        progressFraction = 0.25f,
                        averageLatencyMs = finalLatency.averageMs,
                        jitterMs = finalLatency.jitterMs,
                        statusText = "Testing download throughput..."
                    )
                )
            )

            var downloadBytes = 0L
            val downloadStartNano = System.nanoTime()
            var lastDownloadEmitNano = downloadStartNano

            openDownloadConnection(config).useConnection { connection ->
                activeConnection = connection
                val stream: InputStream = connection.inputStream
                val buffer = ByteArray(16384) // 16 KB buffer
                var bytesRead: Int

                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    currentCoroutineContext().ensureActive()
                    downloadBytes += bytesRead
                    totalBytesConsumed += bytesRead

                    val nowNano = System.nanoTime()
                    val elapsedPhaseMs = (nowNano - downloadStartNano) / 1_000_000L

                    // Emit progress updates at 150ms intervals
                    if (nowNano - lastDownloadEmitNano >= 150_000_000L) {
                        lastDownloadEmitNano = nowNano
                        val currentMbps = SpeedTestCalculator.calculateThroughputMbps(downloadBytes, elapsedPhaseMs)
                        val fraction = (0.25f + 0.35f * (downloadBytes.toFloat() / config.maxDownloadBytes))
                            .coerceIn(0.25f, 0.60f)

                        emit(
                            SpeedTestEvent.ProgressUpdate(
                                SpeedTestProgress(
                                    phase = SpeedTestPhase.DOWNLOAD,
                                    progressFraction = fraction,
                                    currentMbps = currentMbps,
                                    downloadSpeedMbps = currentMbps,
                                    averageLatencyMs = finalLatency.averageMs,
                                    jitterMs = finalLatency.jitterMs,
                                    bytesTransferred = downloadBytes,
                                    totalBytesConsumed = totalBytesConsumed,
                                    statusText = "Testing download throughput..."
                                )
                            )
                        )
                    }

                    if (downloadBytes >= config.maxDownloadBytes || elapsedPhaseMs >= config.phaseTimeLimitMs) {
                        break
                    }
                }
            }

            val downloadDurationMs = ((System.nanoTime() - downloadStartNano) / 1_000_000L).coerceAtLeast(1L)
            val finalDownloadMbps = SpeedTestCalculator.calculateThroughputMbps(downloadBytes, downloadDurationMs)

            // 3. UPLOAD PHASE
            emit(
                SpeedTestEvent.ProgressUpdate(
                    SpeedTestProgress(
                        phase = SpeedTestPhase.UPLOAD,
                        progressFraction = 0.60f,
                        downloadSpeedMbps = finalDownloadMbps,
                        averageLatencyMs = finalLatency.averageMs,
                        jitterMs = finalLatency.jitterMs,
                        totalBytesConsumed = totalBytesConsumed,
                        statusText = "Testing upload throughput..."
                    )
                )
            )

            var uploadBytes = 0L
            val uploadStartNano = System.nanoTime()
            var lastUploadEmitNano = uploadStartNano

            openUploadConnection(config).useConnection { connection ->
                activeConnection = connection
                val outStream: OutputStream = connection.outputStream
                val dummyBuffer = ByteArray(16384) // 16 KB payload chunk
                // Pre-fill with arbitrary non-sensitive static bytes
                for (idx in dummyBuffer.indices) {
                    dummyBuffer[idx] = (idx % 127).toByte()
                }

                while (uploadBytes < config.maxUploadBytes) {
                    currentCoroutineContext().ensureActive()
                    val toWrite = minOf(dummyBuffer.size.toLong(), config.maxUploadBytes - uploadBytes).toInt()
                    outStream.write(dummyBuffer, 0, toWrite)
                    uploadBytes += toWrite
                    totalBytesConsumed += toWrite

                    val nowNano = System.nanoTime()
                    val elapsedPhaseMs = (nowNano - uploadStartNano) / 1_000_000L

                    // Emit progress updates at 150ms intervals
                    if (nowNano - lastUploadEmitNano >= 150_000_000L) {
                        lastUploadEmitNano = nowNano
                        val currentMbps = SpeedTestCalculator.calculateThroughputMbps(uploadBytes, elapsedPhaseMs)
                        val fraction = (0.60f + 0.35f * (uploadBytes.toFloat() / config.maxUploadBytes))
                            .coerceIn(0.60f, 0.95f)

                        emit(
                            SpeedTestEvent.ProgressUpdate(
                                SpeedTestProgress(
                                    phase = SpeedTestPhase.UPLOAD,
                                    progressFraction = fraction,
                                    currentMbps = currentMbps,
                                    downloadSpeedMbps = finalDownloadMbps,
                                    uploadSpeedMbps = currentMbps,
                                    averageLatencyMs = finalLatency.averageMs,
                                    jitterMs = finalLatency.jitterMs,
                                    bytesTransferred = uploadBytes,
                                    totalBytesConsumed = totalBytesConsumed,
                                    statusText = "Testing upload throughput..."
                                )
                            )
                        )
                    }

                    if (elapsedPhaseMs >= config.phaseTimeLimitMs) {
                        break
                    }
                }
                outStream.flush()
                // Await response code to ensure transfer reached server
                connection.responseCode
            }

            val uploadDurationMs = ((System.nanoTime() - uploadStartNano) / 1_000_000L).coerceAtLeast(1L)
            val finalUploadMbps = SpeedTestCalculator.calculateThroughputMbps(uploadBytes, uploadDurationMs)
            val overallDurationMs = (System.nanoTime() - overallStartNano) / 1_000_000L

            val report = SpeedTestFinalReport(
                downloadMbps = finalDownloadMbps,
                uploadMbps = finalUploadMbps,
                latency = finalLatency,
                totalBytesConsumed = totalBytesConsumed,
                durationMillis = overallDurationMs,
                timestampMillis = System.currentTimeMillis(),
                endpointHost = config.hostDisplayName
            )

            emit(SpeedTestEvent.Completed(report))

        } catch (_: CancellationException) {
            disconnectActive()
            emit(SpeedTestEvent.Cancelled)
        } catch (e: UnknownHostException) {
            disconnectActive()
            emit(SpeedTestEvent.Failed(SpeedTestErrorType.NO_CONNECTIVITY, e.message ?: "Host unreachable"))
        } catch (e: ConnectException) {
            disconnectActive()
            emit(SpeedTestEvent.Failed(SpeedTestErrorType.NO_CONNECTIVITY, e.message ?: "Failed to connect to server"))
        } catch (e: SocketTimeoutException) {
            disconnectActive()
            emit(SpeedTestEvent.Failed(SpeedTestErrorType.TIMEOUT, e.message ?: "Connection or read timed out"))
        } catch (e: IOException) {
            disconnectActive()
            emit(SpeedTestEvent.Failed(SpeedTestErrorType.NETWORK_IO_ERROR, e.message ?: "Network I/O failure"))
        } catch (e: Exception) {
            disconnectActive()
            emit(SpeedTestEvent.Failed(SpeedTestErrorType.UNKNOWN, e.message ?: "Unexpected speed test failure"))
        } finally {
            disconnectActive()
        }
    }.flowOn(Dispatchers.IO)

    private fun measureSingleHttpLatency(config: SpeedTestConfig): Double {
        val targetUrl = config.latencyUrl
        val startNano = System.nanoTime()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URI(targetUrl).toURL().openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = config.connectTimeoutMs
                readTimeout = config.readTimeoutMs
                useCaches = false
                defaultUseCaches = false
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "NetMeter-SpeedTest/1.0")
            }
            conn.connect()
            val code = conn.responseCode
            val elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0
            if (code in 200..399) {
                elapsedMs
            } else {
                -1.0
            }
        } catch (_: Exception) {
            -1.0
        } finally {
            conn?.disconnect()
        }
    }

    private fun openDownloadConnection(config: SpeedTestConfig): HttpURLConnection {
        return (URI(config.downloadUrl).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = config.connectTimeoutMs
            readTimeout = config.readTimeoutMs
            useCaches = false
            defaultUseCaches = false
            setRequestProperty("Connection", "close")
            setRequestProperty("User-Agent", "NetMeter-SpeedTest/1.0")
        }
    }

    private fun openUploadConnection(config: SpeedTestConfig): HttpURLConnection {
        return (URI(config.uploadUrl).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = config.connectTimeoutMs
            readTimeout = config.readTimeoutMs
            useCaches = false
            defaultUseCaches = false
            doOutput = true
            setChunkedStreamingMode(16384)
            setRequestProperty("Content-Type", "application/octet-stream")
            setRequestProperty("Connection", "close")
            setRequestProperty("User-Agent", "NetMeter-SpeedTest/1.0")
        }
    }

    private inline fun HttpURLConnection.useConnection(block: (HttpURLConnection) -> Unit) {
        try {
            block(this)
        } finally {
            disconnect()
            activeConnection = null
        }
    }

    private fun disconnectActive() {
        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {
        } finally {
            activeConnection = null
        }
    }
}
