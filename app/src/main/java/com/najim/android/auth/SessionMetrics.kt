package com.najim.android.auth

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Rounded display values are not exact byte counters. */
internal data class DataAmount(val reported: String, val exactBytes: Long?)

internal data class SessionMetrics(
    val uploaded: DataAmount? = null,
    val downloaded: DataAmount? = null,
    val remainingTotal: DataAmount? = null,
    val remainingUpload: DataAmount? = null,
    val remainingDownload: DataAmount? = null,
    val uptimeSeconds: Long? = null,
    val timeLeftSeconds: Long? = null
)

internal object StatusFields {
    private val hiddenStyle = Regex("""(?:^|;)\s*(?:display\s*:\s*none|visibility\s*:\s*hidden)\b""", RegexOption.IGNORE_CASE)
    private val amount = Regex("""([0-9]{1,19})(?:\.([0-9]{1,3}))?\s*(B|KB|MB|GB|TB|KiB|MiB|GiB|TiB)?""", RegexOption.IGNORE_CASE)
    private val duration = Regex("""(?:([0-9]{1,9})w)?(?:([0-9]{1,9})d)?(?:([0-9]{1,9})h)?(?:([0-9]{1,9})m)?(?:([0-9]{1,9})s)?""")
    private const val MAX_SECONDS = 3_155_760_000L

    fun visible(element: Element): Boolean =
        (listOf(element) + element.parents()).none {
            it.tagName() in setOf("script", "style", "template", "noscript", "textarea") ||
                it.hasAttr("hidden") || it.attr("aria-hidden").equals("true", ignoreCase = true) ||
                hiddenStyle.containsMatchIn(it.attr("style"))
        }

    /** Conservative evidence text: reject concealed descendants rather than joining their text.
     * External stylesheets and arbitrary CSS are not evaluated by this parser.
     */
    fun visibleEvidence(element: Element): Boolean =
        visible(element) && element.getAllElements().all(::visible)

    fun rows(doc: Document): List<Pair<String, String>> =
        doc.select("tr").filter(::visible).mapNotNull { row ->
            val cells = row.children().toList()
            if (cells.size != 2 || cells.any { it.tagName() !in setOf("td", "th") || !visibleEvidence(it) ||
                    it.select("[style]").any { child -> child !== it } }) null
            else cells[0].text().trim().trimEnd(':').lowercase() to cells[1].text().trim()
        }

    fun parse(doc: Document): SessionMetrics {
        val rows = rows(doc)
        fun values(vararg labels: String) = rows.filter { it.first in labels }.map { it.second }
        fun part(index: Int, vararg labels: String): List<String> = values(*labels).map {
            val pieces = it.split('/')
            if (pieces.size == 2) pieces[index].trim() else ""
        }
        // Bare numeric values are bytes only under explicitly byte-labelled fields.
        fun explicitAmount(value: String): String =
            if (value.trim().any { it.isLetter() }) value else ""
        fun <T> unique(values: List<String>, parse: (String) -> T?): T? {
            if (values.isEmpty()) return null
            val parsed = values.map { parse(it) ?: return null }
            return parsed.distinct().singleOrNull()
        }
        return SessionMetrics(
            uploaded = unique(values("bytes-in") + part(0, "bytes up / down", "bytes up/down") +
                values("upload", "uploaded", "الرفع").map(::explicitAmount), ::parseAmount),
            downloaded = unique(values("bytes-out") + part(1, "bytes up / down", "bytes up/down") +
                values("download", "downloaded", "التنزيل").map(::explicitAmount), ::parseAmount),
            remainingTotal = unique(values("remain-bytes-total") +
                values("remaining data", "الرصيد المتبقي", "البيانات المتبقية").map(::explicitAmount), ::parseAmount),
            remainingUpload = unique(values("remain-bytes-in") + values("remaining upload").map(::explicitAmount), ::parseAmount),
            remainingDownload = unique(values("remain-bytes-out") + values("remaining download").map(::explicitAmount), ::parseAmount),
            uptimeSeconds = unique(values("uptime", "uptime-secs", "connected", "مدة الاتصال") +
                part(0, "connected / left", "connected/left"), ::parseDuration),
            timeLeftSeconds = unique(values("session-time-left", "session-time-left-secs",
                "time left", "الوقت المتبقي") + part(1, "connected / left", "connected/left"), ::parseDuration)
                ?.takeIf { it > 0L } // Zero may mean no timeout.
        )
    }

    internal fun parseAmount(value: String): DataAmount? {
        val match = amount.matchEntire(value.trim()) ?: return null
        val integer = match.groupValues[1].toLongOrNull() ?: return null
        val fractional = match.groupValues[2]
        val unit = match.groupValues[3]
        if (unit.isEmpty() || unit.equals("B", true)) {
            if (fractional.isNotEmpty()) return null
            return DataAmount("$integer B", integer)
        }
        val number = if (fractional.isEmpty()) "$integer" else "$integer.$fractional"
        return DataAmount(number + " " + unit.uppercase().replace("IB", "iB"), null)
    }

    internal fun parseDuration(value: String): Long? {
        val raw = value.trim()
        if (raw.isEmpty()) return null
        if (raw.all { it in '0'..'9' }) return raw.toLongOrNull()?.takeIf { it <= MAX_SECONDS }
        val match = duration.matchEntire(raw) ?: return null
        val factors = listOf(604800L, 86400L, 3600L, 60L, 1L)
        var seconds = 0L
        for ((i, factor) in factors.withIndex()) {
            val count = match.groupValues[i + 1].ifEmpty { "0" }.toLong()
            seconds += count * factor
            if (seconds > MAX_SECONDS) return null
        }
        return seconds
    }
}
