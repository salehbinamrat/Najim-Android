package com.najim.android.feedback

import com.najim.android.auth.DataAmount
import com.najim.android.auth.SessionDisplay
import com.najim.android.auth.SessionMetrics

/** Typed metrics only; no account, raw page strings, or credential data. */
internal object SessionSpeech {
    private val amountPattern = Regex("""([0-9]{1,19}(?:\.[0-9]{1,3})?) (B|KB|MB|GB|TB|KiB|MiB|GiB|TiB|PiB|EiB)""")
    private val units = mapOf("B" to "بايت", "KB" to "كيلوبايت", "MB" to "ميجابايت",
        "GB" to "جيجابايت", "TB" to "تيرابايت", "KiB" to "كيبي بايت",
        "MiB" to "ميبي بايت", "GiB" to "جيبي بايت", "TiB" to "تيبي بايت",
        "PiB" to "بيبي بايت", "EiB" to "إكسي بايت")
    private fun amount(value: DataAmount?): String {
        val match = SessionDisplay.amount(value)?.let(amountPattern::matchEntire) ?: return "غير متاح"
        return match.groupValues[1] + " " + units.getValue(match.groupValues[2])
    }
    private fun duration(value: Long?): String {
        if (value == null || value !in 0..3_155_760_000L) return "غير متاح"
        return "${value / 3600} ساعة و ${value % 3600 / 60} دقيقة و ${value % 60} ثانية"
    }
    fun summary(metrics: SessionMetrics): String = listOf(
        "بيانات آخر تحديث للجلسة",
        "الرصيد المتبقي: " + amount(metrics.remainingTotal),
        "الوقت المتبقي: " + duration(metrics.timeLeftSeconds),
        "مدة الاتصال: " + duration(metrics.uptimeSeconds),
        "إجمالي الاستهلاك: " + amount(SessionDisplay.total(metrics)),
        "التنزيل: " + amount(metrics.downloaded),
        "الرفع: " + amount(metrics.uploaded)
    ).joinToString(". ")
}
