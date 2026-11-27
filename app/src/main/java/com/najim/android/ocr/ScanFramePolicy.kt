package com.najim.android.ocr

/** Bound bitmap allocation before converting CameraX buffers. */
internal object ScanFramePolicy {
    const val MAX_PIXELS = 1920L * 1080L

    fun accepts(width: Int, height: Int, rotation: Int): Boolean =
        width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS &&
            rotation in setOf(0, 90, 180, 270)
}
