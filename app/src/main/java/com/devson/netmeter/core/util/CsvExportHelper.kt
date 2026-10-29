package com.devson.netmeter.core.util

import com.devson.netmeter.core.domain.model.DailyUsageRecord
import com.devson.netmeter.core.domain.model.SpeedTestRecord
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Standard RFC 4180 compliant CSV export utility.
 */
object CsvExportHelper {

    private const val CRLF = "\r\n"

    /**
     * Escapes a single CSV cell value according to RFC 4180 standards.
     * If the cell contains commas, quotes, or newlines, it is enclosed in double quotes
     * and any existing double quotes are doubled.
     */
    fun escapeCell(value: String): String {
        val requiresQuotes = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')

        return if (requiresQuotes) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }

    /**
     * Formats a row of cells into a comma-separated line.
     */
    fun formatRow(cells: List<String>): String {
        return cells.joinToString(separator = ",") { escapeCell(it) }
    }

    /**
     * Generates RFC 4180 CSV text for historical daily usage records.
     */
    fun buildHistoryCsv(records: List<DailyUsageRecord>): String {
        val builder = StringBuilder()

        val headers = listOf(
            "Date",
            "Period Label",
            "Wi-Fi Received (Bytes)",
            "Wi-Fi Transmitted (Bytes)",
            "Wi-Fi Total (Bytes)",
            "Mobile Received (Bytes)",
            "Mobile Transmitted (Bytes)",
            "Mobile Total (Bytes)",
            "Total Received (Bytes)",
            "Total Transmitted (Bytes)",
            "Grand Total (Bytes)",
            "Grand Total (Formatted)",
            "Daily Data Status",
            "Data Source"
        )
        builder.append(formatRow(headers)).append(CRLF)

        for (record in records) {
            val row = listOf(
                record.dateString,
                record.displayLabel,
                record.wifiRxBytes.toString(),
                record.wifiTxBytes.toString(),
                record.wifiTotalBytes.toString(),
                record.mobileRxBytes.toString(),
                record.mobileTxBytes.toString(),
                record.mobileTotalBytes.toString(),
                record.rxBytes.toString(),
                record.txBytes.toString(),
                record.totalBytes.toString(),
                FormatUtils.formatBytes(record.totalBytes),
                record.status.name,
                record.dataSource.name
            )
            builder.append(formatRow(row)).append(CRLF)
        }

        return builder.toString()
    }

    /**
     * Generates RFC 4180 CSV text for saved speed test history records.
     */
    fun buildSpeedTestCsv(records: List<SpeedTestRecord>): String {
        val builder = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val headers = listOf(
            "Test ID",
            "Timestamp (Epoch Millis)",
            "Date Time (Local)",
            "Active Network Transport",
            "Download Speed (Mbps)",
            "Upload Speed (Mbps)",
            "HTTP Round-Trip Latency (ms)",
            "HTTP Jitter RFC 3550 (ms)",
            "Transfer Volume (Bytes)",
            "Transfer Volume (Formatted)",
            "Server Host"
        )
        builder.append(formatRow(headers)).append(CRLF)

        for (record in records) {
            val dateStr = dateFormat.format(Date(record.timestampMillis))
            val row = listOf(
                record.id.toString(),
                record.timestampMillis.toString(),
                dateStr,
                record.networkType,
                String.format(Locale.US, "%.1f", record.downloadMbps),
                String.format(Locale.US, "%.1f", record.uploadMbps),
                String.format(Locale.US, "%.1f", record.latencyMs),
                String.format(Locale.US, "%.1f", record.jitterMs),
                record.totalBytesConsumed.toString(),
                FormatUtils.formatBytes(record.totalBytesConsumed),
                record.serverHost
            )
            builder.append(formatRow(row)).append(CRLF)
        }

        return builder.toString()
    }
}
