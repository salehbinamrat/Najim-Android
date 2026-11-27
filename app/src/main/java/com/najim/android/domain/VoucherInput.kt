package com.najim.android.domain

/** Local input validation only; never establishes validity on a hotspot. */
object VoucherInput {
    enum class Error { EMPTY, WHITESPACE, CONTROL_CHARACTER, TOO_LONG }
    data class Result(val value: String, val error: Error?) {
        override fun toString() = "VoucherInput.Result(error=$error, value=redacted)"
    }

    fun normalize(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            append(when (ch) {
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                else -> ch
            })
        }
    }

    /** Reject malformed UTF-16 before URL encoding can silently substitute bytes.
     * Passwords preserve all well-formed non-control characters, including spaces.
     */
    internal fun hasInvalidCharacters(value: String, rejectFormat: Boolean): Boolean {
        var index = 0
        while (index < value.length) {
            val point = Character.codePointAt(value, index)
            if (point in 0xD800..0xDFFF || Character.isISOControl(point) ||
                (rejectFormat && (Character.getType(point) == Character.FORMAT.toInt() || point == 0xFFFD)))
                return true
            index += Character.charCount(point)
        }
        return false
    }

    fun validate(raw: String): Result {
        val value = normalize(raw)
        val error = when {
            value.isEmpty() -> Error.EMPTY
            hasInvalidCharacters(value, rejectFormat = true) -> Error.CONTROL_CHARACTER
            value.any { it.isWhitespace() || Character.isSpaceChar(it) } -> Error.WHITESPACE
            value.length > 128 -> Error.TOO_LONG
            else -> null
        }
        return Result(value, error)
    }
}
