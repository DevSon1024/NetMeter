package com.devson.netmeter.core.util

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
}
