package com.najim.android.domain

/** Local input validation only; never establishes validity on a hotspot. */
object VoucherInput {
    enum class Error { EMPTY, WHITESPACE, CONTROL_CHARACTER, TOO_LONG }
    data class Result(val value: String, val error: Error?)

    fun normalize(raw: String): String = buildString(raw.length) {
        raw.forEach { ch ->
            append(when (ch) {
                in '٠'..'٩' -> '0' + (ch - '٠')
                in '۰'..'۹' -> '0' + (ch - '۰')
                else -> ch
            })
        }
    }

    fun validate(raw: String): Result {
        val value = normalize(raw)
        val error = when {
            value.isEmpty() -> Error.EMPTY
            value.any { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() } -> Error.CONTROL_CHARACTER
            value.any { it.isWhitespace() || Character.isSpaceChar(it) } -> Error.WHITESPACE
            value.length > 128 -> Error.TOO_LONG
            else -> null
        }
        return Result(value, error)
    }
}
