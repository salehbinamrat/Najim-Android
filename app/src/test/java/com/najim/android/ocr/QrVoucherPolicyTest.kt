package com.najim.android.ocr

import org.junit.Assert.*
import org.junit.Test

class QrVoucherPolicyTest {
    private fun extract(vararg values: String?) = QrVoucherPolicy.extract(values.toList())
    private fun code(vararg values: String?) = (extract(*values) as? ScanSuggestion.Candidate)?.value

    @Test fun emptyDetectionIsNone() = assertEquals(ScanSuggestion.None, extract())
    @Test fun exactNumericVoucherPreservesZeros() = assertEquals("000123", code("000123"))
    @Test fun arabicDigitsNormalize() = assertEquals("001234", code("٠٠١٢٣٤"))
    @Test fun persianDigitsNormalize() = assertEquals("001234", code("۰۰۱۲۳۴"))
    @Test fun duplicateValuesAgree() = assertEquals("001234", code("001234", "٠٠١٢٣٤"))
    @Test fun differentCodesAreAmbiguous() = assertEquals(ScanSuggestion.Ambiguous, extract("1234", "4321"))
    @Test fun validWithUnsupportedIsAmbiguous() = assertEquals(ScanSuggestion.Ambiguous, extract("1234", "https://example.test"))
    @Test fun urlsAndIntentsRejected() {
        for (value in listOf("https://a.test/1234", "http://10.0.0.1/login", "intent:1234", "tel:1234", "javascript:1234"))
            assertEquals(ScanSuggestion.Unsupported, extract(value))
    }
    @Test fun structuredPayloadsRejected() {
        for (value in listOf("WIFI:S:net;P:1234;;", "PIN:1234", "{\"user\":\"1234\"}", "BEGIN:VCARD", "AB1234"))
            assertEquals(ScanSuggestion.Unsupported, extract(value))
    }
    @Test fun whitespaceAndControlsRejected() {
        for (value in listOf(" 1234", "1234 ", "12 34", "12\n34", "12\u200B34"))
            assertEquals(ScanSuggestion.Unsupported, extract(value))
    }
    @Test fun invalidLengthsAndMissingValuesRejected() {
        for (value in listOf(null, "", "123", "1".repeat(33))) assertEquals(ScanSuggestion.Unsupported, extract(value))
    }
    @Test fun maximumLengthAccepted() = assertEquals("0".repeat(32), code("0".repeat(32)))
    @Test fun tooManyDetectionsRejected() = assertEquals(ScanSuggestion.Ambiguous,
        QrVoucherPolicy.extract(List(17) { "1234" }))
    @Test fun unsupportedResetsLiveStability() {
        val gate = ScanStability()
        gate.accept(extract("1234"))
        gate.accept(extract("1234"))
        assertEquals(ScanSuggestion.Unsupported, gate.accept(extract("https://a.test")))
        assertEquals(ScanSuggestion.Unstable, gate.accept(extract("1234")))
    }
}
