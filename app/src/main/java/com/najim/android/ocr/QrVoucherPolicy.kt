package com.najim.android.ocr

import com.najim.android.domain.VoucherInput

internal enum class ScanMode { OCR, QR }

/** QR is untrusted data. Accept only complete numeric voucher payloads, never a URI or command. */
internal object QrVoucherPolicy {
    fun extract(values: List<String?>, bounds: ScanBounds = ScanBounds()): ScanSuggestion {
        if (values.isEmpty()) return ScanSuggestion.None
        if (values.size > 16) return ScanSuggestion.Ambiguous
        val unique = linkedSetOf<String>()
        var unsupported = false
        for (raw in values) {
            if (raw == null || raw.length !in bounds.min..bounds.max) {
                unsupported = true
                continue
            }
            val code = VoucherInput.normalize(raw)
            if (code.any { it !in '0'..'9' } || VoucherInput.validate(code).error != null) {
                unsupported = true
                continue
            }
            unique.add(code)
        }
        if (unique.size > 1 || (unsupported && unique.isNotEmpty())) return ScanSuggestion.Ambiguous
        if (unsupported) return ScanSuggestion.Unsupported
        return unique.singleOrNull()?.let { ScanSuggestion.Candidate(it) } ?: ScanSuggestion.None
    }
}
