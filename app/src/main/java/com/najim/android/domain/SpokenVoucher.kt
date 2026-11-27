package com.najim.android.domain

/** Accept digit strings or individually spoken Arabic/English digits only.
 * Never infer a number from a sentence, confidence score, or conflicting alternatives.
 */
internal object SpokenVoucher {
    private val digits = mapOf(
        "صفر" to "0", "واحد" to "1", "واحدة" to "1",
        "اثنان" to "2", "اثنين" to "2", "إثنين" to "2",
        "ثلاثة" to "3", "ثلاث" to "3", "أربعة" to "4", "اربعة" to "4",
        "خمسة" to "5", "ستة" to "6", "سبعة" to "7", "ثمانية" to "8", "تسعة" to "9",
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9"
    )
    fun candidate(alternatives: List<String>): String? {
        if (alternatives.isEmpty() || alternatives.size > 5) return null
        return alternatives.map { parse(it) ?: return null }.distinct().singleOrNull()
    }
    private fun parse(raw: String): String? {
        if (raw.length > 512 || raw.any { it.isISOControl() ||
                Character.getType(it) == Character.FORMAT.toInt() }) return null
        val tokens = VoucherInput.normalize(raw).trim(' ').lowercase().split(Regex(" +"))
        val number = buildString {
            for (token in tokens) {
                if (token.isEmpty()) return null
                if (token.all { it in '0'..'9' }) append(token)
                else append(digits[token] ?: return null)
            }
        }
        return number.takeIf { it.length in 4..32 }
    }
}
