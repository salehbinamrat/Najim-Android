package com.najim.android.ocr

import org.junit.Assert.*
import org.junit.Test

class VoucherCandidatesTest {
    private fun candidate(vararg lines: String, bounds: ScanBounds = ScanBounds()): String? =
        (VoucherCandidates.extract(lines.toList(), bounds) as? ScanSuggestion.Candidate)?.value

    @Test fun preservesLeadingZeros() = assertEquals("000123", candidate("000123"))
    @Test fun arabicIndicDigitsNormalized() = assertEquals("001234", candidate("٠٠١٢٣٤"))
    @Test fun persianDigitsNormalized() = assertEquals("001234", candidate("۰۰۱۲۳۴"))
    @Test fun mixedDigitScriptsNormalized() = assertEquals("012345", candidate("0١۲3٤5"))
    @Test fun explicitEnglishLabel() = assertEquals("000123", candidate("Voucher code: 000123"))
    @Test fun explicitArabicLabel() = assertEquals("000123", candidate("رقم الكرت: ٠٠٠١٢٣"))
    @Test fun noJoiningGroupsOrLines() {
        assertNull(candidate("12 3456"))
        assertNull(candidate("12", "34"))
        assertNull(candidate("1234-5678"))
    }
    @Test fun noLetterToDigitGuessing() {
        assertNull(candidate("00O123"))
        assertNull(candidate("AB123456"))
        assertNull(candidate("123456I"))
    }
    @Test fun noDatesAmountsOrPhoneLabels() {
        for (line in listOf("2026/11/27", "5000 YER", "Price: 1000", "Phone: 777123456",
            "السعر: 5000")) assertNull(candidate(line))
    }
    @Test fun distinctNumbersAreAmbiguousEvenIfOneLabeled() =
        assertEquals(ScanSuggestion.Ambiguous, VoucherCandidates.extract(listOf("123456", "PIN: 654321")))
    @Test fun repeatedSameValueIsOneSuggestion() = assertEquals("123456", candidate("123456", "PIN: ١٢٣٤٥٦"))
    @Test fun boundsDoNotTruncate() {
        assertNull(candidate("123456789", bounds = ScanBounds(4, 8)))
        assertEquals("123", candidate("123", bounds = ScanBounds(3, 3)))
    }
    @Test fun excessiveInputIsRejected() {
        assertEquals(ScanSuggestion.Ambiguous, VoucherCandidates.extract(List(257) { "123456" }))
        assertEquals(ScanSuggestion.Ambiguous, VoucherCandidates.extract(listOf("1".repeat(513))))
    }
    @Test fun invalidBoundsRejected() {
        for ((min, max) in listOf(0 to 4, 8 to 4, 1 to 129)) {
            assertThrows(IllegalArgumentException::class.java) { ScanBounds(min, max) }
        }
    }
    @Test fun candidateDoesNotExposeSecretInToString() =
        assertEquals("Candidate(redacted)", VoucherCandidates.extract(listOf("123456")).toString())
    @Test fun invisibleControlNotSilentlyRemoved() = assertNull(candidate("123\u200B456"))
    @Test fun noCandidateForBlankInput() = assertEquals(ScanSuggestion.None, VoucherCandidates.extract(listOf("", " ")))
}
