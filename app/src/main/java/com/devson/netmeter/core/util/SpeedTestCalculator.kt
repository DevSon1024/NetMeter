package com.devson.netmeter.core.util

import com.devson.netmeter.core.domain.model.LatencyMeasurement
import com.devson.netmeter.core.domain.model.ThroughputMeasurement
import kotlin.math.abs

object SpeedTestCalculator {

    /**
     * Calculates throughput in Megabits per second (Mbps).
     * Formula: (bytesTransferred * 8 bits) / (durationMs * 1000)
     */
    fun calculateThroughputMbps(bytesTransferred: Long, durationMs: Long): Double {
        if (bytesTransferred <= 0L || durationMs <= 0L) {
            return 0.0
        }
        val bits = bytesTransferred.toDouble() * 8.0
        val seconds = durationMs.toDouble() / 1000.0
        val mbps = bits / (seconds * 1_000_000.0)
        return if (mbps.isFinite() && mbps >= 0.0) {
            mbps
        } else {
            0.0
        }
    }

    /**
     * Creates a ThroughputMeasurement given bytes and duration.
     */
    fun computeThroughput(bytesTransferred: Long, durationMs: Long): ThroughputMeasurement {
        val mbps = calculateThroughputMbps(bytesTransferred, durationMs)
        return ThroughputMeasurement(
            bytesTransferred = bytesTransferred.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            mbps = mbps
        )
    }

    /**
     * Calculates statistical jitter according to RFC 3550:
     * Mean absolute deviation of successive latency samples.
     * J = sum(|D(i) - D(i-1)|) / (N - 1)
     */
    fun calculateJitterMs(samplesMs: List<Double>): Double {
        if (samplesMs.size < 2) return 0.0
        var totalDeviation = 0.0
        for (i in 1 until samplesMs.size) {
            totalDeviation += abs(samplesMs[i] - samplesMs[i - 1])
        }
        val jitter = totalDeviation / (samplesMs.size - 1)
        return if (jitter.isFinite() && jitter >= 0.0) jitter else 0.0
    }

    /**
     * Aggregates latency measurements into LatencyMeasurement.
     */
    fun computeLatency(samplesMs: List<Double>): LatencyMeasurement {
        val validSamples = samplesMs.filter { it.isFinite() && it >= 0.0 }
        if (validSamples.isEmpty()) {
            return LatencyMeasurement.ZERO
        }
        val average = validSamples.average()
        val min = validSamples.minOrNull() ?: 0.0
        val max = validSamples.maxOrNull() ?: 0.0
        val jitter = calculateJitterMs(validSamples)

        return LatencyMeasurement(
            samplesMs = validSamples,
            averageMs = if (average.isFinite()) average else 0.0,
            minMs = min,
            maxMs = max,
            jitterMs = jitter
        )
    }
}
