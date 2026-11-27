package com.najim.android.ui

/** Layout thresholds use available content width, not physical screen pixels. */
internal object AdaptiveLayout {
    fun sidePadding(widthDp: Float): Int = if (widthDp < 360f) 12 else 20

    fun metricColumns(widthDp: Float, fontScale: Float): Int =
        if (widthDp.isFinite() && fontScale.isFinite() && fontScale > 0f &&
            widthDp >= 304f && widthDp / maxOf(1f, fontScale) >= 304f) 2 else 1
}
