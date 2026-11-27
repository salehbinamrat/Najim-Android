package com.najim.android.domain

import org.junit.Assert.*
import org.junit.Test

class SpokenVoucherTest {
    @Test fun ArabicAndEnglishDigitsKeepLeadingZeros() {
        assertEquals("001234", SpokenVoucher.candidate(listOf("صفر صفر واحد اثنين ثلاثة أربعة", "٠٠١٢٣٤")))
        assertEquals("0012", SpokenVoucher.candidate(listOf("zero zero one two", "۰۰۱۲")))
    }
    @Test fun conflictsAndSentencesAreRejected() {
        for (values in listOf(listOf("1234", "1235"), listOf("رقم الكرت 1234"),
            listOf("ألف ومئتان"), listOf("12-34"), listOf("12\u200b34"), listOf("12\n34"),
            listOf("123"), listOf("1".repeat(33)), listOf("1234", "unknown"))) {
            assertNull(SpokenVoucher.candidate(values))
        }
    }
    @Test fun noUnboundedAlternativesOrInput() {
        assertNull(SpokenVoucher.candidate(emptyList()))
        assertNull(SpokenVoucher.candidate(List(6) { "1234" }))
        assertNull(SpokenVoucher.candidate(listOf(" ".repeat(513) + "1234")))
    }
    @Test fun explicitReviewOffersDistinctValidAlternativesWithoutGuessing() {
        assertEquals(listOf("0012", "0013"),
            SpokenVoucher.reviewOptions(listOf("٠٠١٢", "zero zero one two", "0013", "unknown")))
        assertTrue(SpokenVoucher.reviewOptions(List(6) { "1234" }).isEmpty())
        assertTrue(SpokenVoucher.reviewOptions(listOf("1234", " ".repeat(513))).isEmpty())
    }
}
