package com.najim.android.ocr

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class ImageImportPolicyTest {
    @Test fun exactLimitAccepted() = assertArrayEquals(byteArrayOf(1, 2, 3),
        ImageImportPolicy.readBounded(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 3))
    @Test fun oversizedRejectedNotTruncated() = assertNull(
        ImageImportPolicy.readBounded(ByteArrayInputStream(ByteArray(5)), 4))
    @Test fun emptyRejected() = assertNull(ImageImportPolicy.readBounded(ByteArrayInputStream(ByteArray(0))))
    @Test fun stalledReadFails() {
        val stream = object : InputStream() {
            override fun read(): Int = 0
            override fun read(b: ByteArray, off: Int, len: Int): Int = 0
        }
        assertThrows(IOException::class.java) { ImageImportPolicy.readBounded(stream) }
    }
    @Test fun smallImageUnchanged() = assertEquals(1, ImageImportPolicy.sampleSize(1280, 720))
    @Test fun largeImageSampled() = assertEquals(4, ImageImportPolicy.sampleSize(4000, 3000))
    @Test fun panoramaBounded() = assertEquals(8, ImageImportPolicy.sampleSize(16000, 200))
    @Test fun invalidOrHugeDimensionsRejected() {
        for ((w,h) in listOf(0 to 100, -1 to 50, Int.MAX_VALUE to 100, 20000 to 20000))
            assertNull(ImageImportPolicy.sampleSize(w,h))
    }
    @Test fun oddSizesUseCeilingBound() = assertEquals(2, ImageImportPolicy.sampleSize(1921, 1081))
    @Test fun exifRotations() {
        assertEquals(0, ImageImportPolicy.rotation(1))
        assertEquals(90, ImageImportPolicy.rotation(6))
        assertEquals(180, ImageImportPolicy.rotation(3))
        assertEquals(270, ImageImportPolicy.rotation(8))
    }
    @Test fun mirroredAndInvalidExifRejected() {
        for (value in listOf(2,4,5,7,9,-1)) assertNull(ImageImportPolicy.rotation(value))
    }
}
