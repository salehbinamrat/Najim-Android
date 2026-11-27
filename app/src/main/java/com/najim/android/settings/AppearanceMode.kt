package com.najim.android.settings

internal enum class AppearanceMode(val storedValue: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");

    fun usesDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromStored(value: String?): AppearanceMode =
            entries.firstOrNull { it.storedValue == value } ?: SYSTEM
    }
}
