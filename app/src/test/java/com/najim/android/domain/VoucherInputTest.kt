package com.najim.android.domain

import org.junit.Assert.*
import org.junit.Test

class VoucherInputTest {
    @Test fun easternDigits() = assertEquals("0123456789", VoucherInput.normalize("٠١٢٣٤۵۶۷۸۹"))
    @Test fun preserveCaseAndZeroes() = assertEquals("00Ab12", VoucherInput.validate("٠٠Ab١٢").value)
    @Test fun empty() = assertEquals(VoucherInput.Error.EMPTY, VoucherInput.validate("").error)
    @Test fun spaces() = assertEquals(VoucherInput.Error.WHITESPACE, VoucherInput.validate("12 34").error)
    @Test fun control() = assertEquals(VoucherInput.Error.CONTROL_CHARACTER, VoucherInput.validate("12\n34").error)
    @Test fun invisibleFormat() = assertEquals(VoucherInput.Error.CONTROL_CHARACTER, VoucherInput.validate("12\u200B34").error)
    @Test fun length() = assertEquals(VoucherInput.Error.TOO_LONG, VoucherInput.validate("a".repeat(129)).error)
    @Test fun phoneLikeIsNotRejected() = assertNull(VoucherInput.validate("771234567").error)
    @Test fun allZerosAreNotRejected() = assertNull(VoucherInput.validate("00000000").error)
    @Test fun validationResultNeverPrintsCredential() {
        val result = VoucherInput.validate("0012Ab")
        assertEquals("0012Ab", result.value)
        assertFalse(result.toString().contains("0012Ab"))
        assertTrue(result.toString().contains("redacted"))
    }
    @Test fun malformedSurrogatesAndSupplementaryFormatAreRejected() {
        for (raw in listOf("12\uD80034", "12\uDC0034", "12\uDB40\uDC0134", "12\uFFFD34")) {
            assertEquals(VoucherInput.Error.CONTROL_CHARACTER, VoucherInput.validate(raw).error)
        }
        assertNull(VoucherInput.validate("A\uD83D\uDE0034").error)
    }
}
