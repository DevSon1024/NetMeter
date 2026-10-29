package com.devson.netmeter.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        val tb = gb * 1024.0

        val bytesDouble = bytes.toDouble()
        return when {
            bytesDouble >= tb -> String.format(Locale.US, "%.2f TB", bytesDouble / tb)
            bytesDouble >= gb -> String.format(Locale.US, "%.2f GB", bytesDouble / gb)
            bytesDouble >= mb -> String.format(Locale.US, "%.2f MB", bytesDouble / mb)
            bytesDouble >= kb -> String.format(Locale.US, "%.1f KB", bytesDouble / kb)
            else -> "$bytes B"
        }
    }

    fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun formatDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("EEE, MMM d", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    fun formatTimeRange(startMillis: Long, endMillis: Long): String {
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val startFormatted = timeFormat.format(Date(startMillis))
        val endFormatted = timeFormat.format(Date(endMillis))
        return "$startFormatted - $endFormatted"
    }

    fun formatDateTime(timeMillis: Long): String {
        if (timeMillis <= 0L) return "N/A"
        val sdf = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }
}

