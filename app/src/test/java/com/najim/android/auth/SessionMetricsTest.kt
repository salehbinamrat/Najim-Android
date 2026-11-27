package com.najim.android.auth

import java.net.URI
import org.junit.Assert.*
import org.junit.Test

class SessionMetricsTest {
    private val origin = URI("https://10.0.0.1/")
    private fun page(rows: String, extra: String = "") =
        PortalParser.parse(origin, origin.resolve("/status"), 200,
            ("<h1>Welcome, 0012!</h1><form action='/logout'></form><table>$rows</table>$extra").toByteArray())
    private fun row(label: String, value: String) = "<tr><td>$label</td><td>$value</td></tr>"
    private fun metrics(rows: String) = (page(rows) as PortalPage.AccountStatus).metrics

    @Test fun stockCombinedRowsKeepDirectionAndDuration() {
        val m = metrics(row("bytes up / down", "123 / 456") + row("connected / left", "1h2m3s / 5h"))
        assertEquals(123L, m.uploaded?.exactBytes)
        assertEquals(456L, m.downloaded?.exactBytes)
        assertEquals(3723L, m.uptimeSeconds)
        assertEquals(18000L, m.timeLeftSeconds)
    }
    @Test fun explicitCountersAndRemainingAreIndependent() {
        val m = metrics(row("bytes-in", "10") + row("bytes-out", "20") + row("remain-bytes-total", "0"))
        assertEquals(10L, m.uploaded?.exactBytes)
        assertEquals(20L, m.downloaded?.exactBytes)
        assertEquals(0L, m.remainingTotal?.exactBytes)
        assertNull(m.remainingUpload)
    }
    @Test fun roundedUnitNeverBecomesExactBytes() {
        val m = metrics(row("bytes up / down", "1.25 MiB / 4 GB"))
        assertEquals("1.25 MiB", m.uploaded?.reported)
        assertNull(m.uploaded?.exactBytes)
        assertEquals("4 GB", m.downloaded?.reported)
    }
    @Test fun noInventedRemainingTotal() {
        val m = metrics(row("remain-bytes-in", "100") + row("remain-bytes-out", "200"))
        assertNull(m.remainingTotal)
        assertEquals(100L, m.remainingUpload?.exactBytes)
        assertEquals(200L, m.remainingDownload?.exactBytes)
    }
    @Test fun missingMetricsRemainUnknown() = assertEquals(SessionMetrics(), metrics(""))
    @Test fun zeroTimeoutIsNotProofOfExpiry() {
        val m = metrics(row("session-time-left-secs", "0") + row("uptime-secs", "0"))
        assertNull(m.timeLeftSeconds)
        assertEquals(0L, m.uptimeSeconds)
    }
    @Test fun conflictingValuesAreUnknown() {
        val m = metrics(row("bytes-in", "100") + row("upload", "200"))
        assertNull(m.uploaded)
    }
    @Test fun malformedDuplicateInvalidatesField() {
        assertNull(metrics(row("bytes-in", "100") + row("upload", "---")).uploaded)
    }
    @Test fun identicalValuesAgree() {
        assertEquals(100L, metrics(row("bytes-in", "100") + row("upload", "100 B")).uploaded?.exactBytes)
    }
    @Test fun invalidAmountsRejected() {
        for (value in listOf("-1", "---", "unlimited", "NaN", "1.5 B", "9999999999999999999", "10 MB extra"))
            assertNull(StatusFields.parseAmount(value))
    }
    @Test fun boundedDurationParsing() {
        assertEquals(788645L, StatusFields.parseDuration("1w2d3h4m5s"))
        assertEquals(90L, StatusFields.parseDuration("1:30"))
        assertEquals(3723L, StatusFields.parseDuration("01:02:03"))
        for (value in listOf("", "-1", "1h2h", "999999999w", "1:60", "1:02:60", "never"))
            assertNull(StatusFields.parseDuration(value))
    }
    @Test fun hiddenRowsIgnored() {
        assertNull(metrics("<tr hidden><td>bytes-in</td><td>999</td></tr>").uploaded)
    }
    @Test fun numericStatusRefreshIsNotExecuted() {
        assertTrue(page("", "<meta http-equiv='refresh' content='30'>") is PortalPage.AccountStatus)
        assertEquals(PortalPage.Unknown, page("", "<meta http-equiv='refresh' content='0;url=https://other.test'>"))
    }
    @Test fun hiddenAccountCannotEstablishSession() {
        val html = "<h1 hidden>Welcome, 0012!</h1><form action='/logout'></form>"
        assertEquals(PortalPage.Unknown, PortalParser.parse(origin, origin.resolve("/status"), 200, html.toByteArray()))
    }
    @Test fun configuredSessionTimeoutIsNotRemainingTime() {
        assertNull(metrics(row("session-timeout", "5h")).timeLeftSeconds)
        assertEquals(60L, metrics(row("session-timeout", "5h") +
            row("session-time-left", "1m")).timeLeftSeconds)
    }
    @Test fun ambiguousBalanceNeverBecomesBytes() {
        for (value in listOf("100", "100 ريال", "1.50", "100 USD"))
            assertNull(metrics(row("الرصيد المتبقي", value)).remainingTotal)
        assertEquals("1.5 GB", metrics(row("الرصيد المتبقي", "1.5 GB")).remainingTotal?.reported)
        assertEquals(100L, metrics(row("remain-bytes-total", "100")).remainingTotal?.exactBytes)
    }
    @Test fun genericTrafficLabelsRequireUnits() {
        val m = metrics(row("upload", "100") + row("التنزيل", "200") + row("remaining upload", "300"))
        assertNull(m.uploaded)
        assertNull(m.downloaded)
        assertNull(m.remainingUpload)
        assertEquals("200 MB", metrics(row("التنزيل", "200 MB")).downloaded?.reported)
    }
    @Test fun hiddenDescendantDoesNotSupplyMetrics() {
        assertNull(metrics(row("bytes-in", "<span aria-hidden='TRUE'>999</span>")).uploaded)
    }
    @Test fun arabicLabelsReportOnlyExplicitUnitsAndDurations() {
        val m = metrics(row("حجم التنزيل", "5 MB") + row("حجم الرفع", "2 MB") +
            row("حجم البيانات المتبقي", "10 GB") + row("مدة الاستخدام", "5m") + row("الزمن المتبقي", "2h"))
        assertEquals("5 MB", m.downloaded?.reported)
        assertEquals("2 MB", m.uploaded?.reported)
        assertEquals("10 GB", m.remainingTotal?.reported)
        assertEquals(300L, m.uptimeSeconds)
        assertEquals(7200L, m.timeLeftSeconds)
        assertNull(metrics(row("حجم البيانات المتبقي", "100")).remainingTotal)
    }
}
