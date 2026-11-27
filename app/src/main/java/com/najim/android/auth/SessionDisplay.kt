package com.najim.android.auth

import java.util.Locale

/** Presentation only. Never extrapolates remaining time, traffic, or account quota. */
internal object SessionDisplay {
    fun maskAccount(username: String): String =
        if (username.length <= 4) "••••" else "•••• " + username.takeLast(4)

    fun duration(seconds: Long?): String? {
        if (seconds == null || seconds < 0) return null
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds % 60)
    }
}
