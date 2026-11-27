package com.najim.android.settings

import android.content.Context

/** Non-sensitive appearance only; never store credentials or consent here. Worker thread access. */
internal class AppearancePreferences(context: Context) {
    private val app = context.applicationContext
    private fun preferences() = app.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun read(): AppearanceMode = AppearanceMode.fromStored(preferences().getString("mode", null))

    fun write(mode: AppearanceMode): Boolean =
        preferences().edit().putString("mode", mode.storedValue).commit()
}
