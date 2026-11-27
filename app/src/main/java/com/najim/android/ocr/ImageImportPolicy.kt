package com.najim.android.ocr

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException

internal object ImageImportPolicy {
    const val MAX_BYTES = 12 * 1024 * 1024
    const val MAX_PIXELS = 2_073_600L
    const val MAX_SIDE = 2048
    private const val MAX_SOURCE_PIXELS = 100_000_000L

    /** Caller closes the stream. Reject rather than truncate an oversized input. */
    fun readBounded(stream: InputStream, limit: Int = MAX_BYTES): ByteArray? {
        require(limit in 1..MAX_BYTES)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count == -1) return output.toByteArray().takeIf { it.isNotEmpty() }
            if (count == 0) throw IOException("Empty stream read")
            if (count > limit - output.size()) return null
            output.write(buffer, 0, count)
        }
    }

    /** Power-of-two sample size with conservative ceiling dimensions. */
    fun sampleSize(width: Int, height: Int): Int? {
        if (width <= 0 || height <= 0 || width > 20_000 || height > 20_000 ||
            width.toLong() * height > MAX_SOURCE_PIXELS) return null
        var sample = 1
        while (true) {
            val w = (width + sample - 1) / sample
            val h = (height + sample - 1) / sample
            if (w <= MAX_SIDE && h <= MAX_SIDE && w.toLong() * h <= MAX_PIXELS) return sample
            sample *= 2
        }
    }

    // Reject mirrored or malformed EXIF values instead of guessing a transform.
    fun rotation(orientation: Int): Int? = when (orientation) {
        0, 1 -> 0
        3 -> 180
        6 -> 90
        8 -> 270
        else -> null
    }
}
