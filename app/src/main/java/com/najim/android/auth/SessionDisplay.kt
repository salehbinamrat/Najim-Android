package com.najim.android.auth

import java.util.Locale

/** Presentation only. Never extrapolates remaining time, traffic, or account quota. */
internal object SessionDisplay {
    fun maskAccount(username: String): String =
        if (username.length <= 4) "••••" else "•••• " + username.takeLast(4)

    /** Format exact byte counters without changing the reported underlying amount. */
    fun amount(value: DataAmount?): String? {
        if (value == null) return null
        val bytes = value.exactBytes ?: return value.reported
        if (bytes < 0L) return null
        if (bytes < 1024L) return "$bytes B"
        val units = listOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")
        var number = bytes.toDouble()
        var index = 0
        while (number >= 1024.0 && index < units.lastIndex) {
            number /= 1024.0
            index++
        }
        return String.format(Locale.ROOT, "%.2f %s", number, units[index])
    }

    /** Sum only two exact byte counters; no inference from rounded or absent values. */
    fun total(metrics: SessionMetrics): DataAmount? {
        val up = metrics.uploaded?.exactBytes ?: return null
        val down = metrics.downloaded?.exactBytes ?: return null
        if (up < 0 || down < 0 || up > Long.MAX_VALUE - down) return null
        val sum = up + down
        return DataAmount("$sum B", sum)
    }

    fun duration(seconds: Long?): String? {
        if (seconds == null || seconds < 0) return null
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds % 60)
    }
}
