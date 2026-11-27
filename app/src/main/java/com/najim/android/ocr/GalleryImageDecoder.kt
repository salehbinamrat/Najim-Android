package com.najim.android.ocr

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.IOException

/** Worker thread only. No file writes, URI persistence or application network requests. */
internal object GalleryImageDecoder {
    class Decoded(val bitmap: Bitmap, val rotation: Int)

    fun decode(resolver: ContentResolver, uri: Uri, cancelled: () -> Boolean = { false }): Decoded? {
        if (cancelled() || uri.scheme != ContentResolver.SCHEME_CONTENT) return null
        val bytes = try {
            resolver.openInputStream(uri)?.use { ImageImportPolicy.readBounded(it, cancelled = cancelled) }
        } catch (_: IOException) { null } catch (_: RuntimeException) { null } ?: return null
        try {
            if (cancelled()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val sample = ImageImportPolicy.sampleSize(bounds.outWidth, bounds.outHeight) ?: return null
            val rotation = when (bounds.outMimeType) {
                "image/jpeg" -> ImageImportPolicy.rotation(
                    ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL))
                "image/png" -> 0
                else -> null
            } ?: return null
            if (cancelled()) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            if (cancelled() || bitmap.width > ImageImportPolicy.MAX_SIDE || bitmap.height > ImageImportPolicy.MAX_SIDE ||
                bitmap.width.toLong() * bitmap.height > ImageImportPolicy.MAX_PIXELS) {
                bitmap.recycle()
                return null
            }
            return Decoded(bitmap, rotation)
        } catch (_: IOException) {
            return null
        } catch (_: RuntimeException) {
            return null
        } finally {
            bytes.fill(0)
        }
    }
}
