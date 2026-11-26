package com.najim.android.ocr

import org.junit.Assert.*
import org.junit.Test

class ScanStabilityTest {
    private fun value(code: String) = ScanSuggestion.Candidate(code)
    @Test fun requiresThreeIndependentMatchingFrames() {
        val stability = ScanStability()
        assertEquals(ScanSuggestion.Unstable, stability.accept(value("0012")))
        assertEquals(ScanSuggestion.Unstable, stability.accept(value("0012")))
        assertEquals("0012", (stability.accept(value("0012")) as ScanSuggestion.Candidate).value)
    }
    @Test fun differentNumberResetsStreak() {
        val stability = ScanStability()
        stability.accept(value("0012"))
        stability.accept(value("0012"))
        assertEquals(ScanSuggestion.Unstable, stability.accept(value("0013")))
        assertEquals(ScanSuggestion.Unstable, stability.accept(value("0012")))
    }
    @Test fun ambiguousOrEmptyFrameResetsStreak() {
        for (bad in listOf(ScanSuggestion.Ambiguous, ScanSuggestion.None)) {
            val stability = ScanStability()
            stability.accept(value("0012"))
            stability.accept(value("0012"))
            assertEquals(bad, stability.accept(bad))
            assertEquals(ScanSuggestion.Unstable, stability.accept(value("0012")))
        }
    }
    @Test fun explicitResetClearsStreak() {
        val stability = ScanStability(2)
        stability.accept(value("0012"))
        stability.reset()
        assertEquals(ScanSuggestion.Unstable, stability.accept(value("0012")))
    }
}
