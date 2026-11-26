package com.najim.android.ocr

import com.najim.android.domain.VoucherInput

/** Suggestions only. Never silently correct, concatenate or submit a credential. */
internal sealed interface ScanSuggestion {
    data object None : ScanSuggestion
    data object Ambiguous : ScanSuggestion
    data object Unstable : ScanSuggestion
    class Candidate(val value: String) : ScanSuggestion {
        override fun toString() = "Candidate(redacted)"
    }
}

internal data class ScanBounds(val min: Int = 4, val max: Int = 32) {
    init { require(min in 1..128 && max in min..128) }
}

internal object VoucherCandidates {
    private val digits = Regex("[0-9]+")
    private val labeled = Regex(
        """(?:voucher(?:\s+(?:code|number))?|pin|code|username|رقم\s+الكرت|رمز\s+الكرت|اسم\s+المستخدم)\s*[:：]\s*([0-9]+)""",
        RegexOption.IGNORE_CASE
    )

    fun extract(lines: List<String>, bounds: ScanBounds = ScanBounds()): ScanSuggestion {
        // Reject excessive OCR output instead of truncating it into a misleading candidate.
        if (lines.size > 256 || lines.any { it.length > 512 }) return ScanSuggestion.Ambiguous
        val candidates = linkedSetOf<String>()
        for (raw in lines) {
            val line = VoucherInput.normalize(raw).trim()
            val value = if (digits.matches(line)) line
                else labeled.matchEntire(line)?.groupValues?.get(1) ?: continue
            if (value.length !in bounds.min..bounds.max) continue
            if (VoucherInput.validate(value).error != null) continue
            candidates.add(value)
            if (candidates.size > 1) return ScanSuggestion.Ambiguous
        }
        return candidates.singleOrNull()?.let { ScanSuggestion.Candidate(it) } ?: ScanSuggestion.None
    }
}

/** One scanner session. Independent consecutive frames must agree before suggesting a number. */
internal class ScanStability(private val requiredFrames: Int = 3) {
    init { require(requiredFrames in 2..10) }
    private var previous: String? = null
    private var count = 0

    fun accept(suggestion: ScanSuggestion): ScanSuggestion {
        if (suggestion !is ScanSuggestion.Candidate) {
            reset()
            return suggestion
        }
        if (suggestion.value == previous) count = (count + 1).coerceAtMost(requiredFrames)
        else {
            previous = suggestion.value
            count = 1
        }
        return if (count >= requiredFrames) ScanSuggestion.Candidate(suggestion.value)
            else ScanSuggestion.Unstable
    }

    fun reset() {
        previous = null
        count = 0
    }
}
