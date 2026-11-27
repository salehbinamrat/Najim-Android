package com.najim.android.ocr

import org.junit.Assert.*
import org.junit.Test

class ScanFramePolicyTest {
    @Test fun supportedRotationsAndTypicalResolution() {
        for (rotation in listOf(0, 90, 180, 270))
            assertTrue(ScanFramePolicy.accepts(1280, 720, rotation))
    }
    @Test fun exactPixelLimitIsAccepted() =
        assertTrue(ScanFramePolicy.accepts(1920, 1080, 0))
    @Test fun oversizedAndOverflowDimensionsRejected() {
        assertFalse(ScanFramePolicy.accepts(1921, 1080, 0))
        assertFalse(ScanFramePolicy.accepts(Int.MAX_VALUE, Int.MAX_VALUE, 0))
    }
    @Test fun invalidDimensionsRejected() {
        assertFalse(ScanFramePolicy.accepts(0, 720, 0))
        assertFalse(ScanFramePolicy.accepts(-1, -1, 0))
    }
    @Test fun invalidRotationRejected() {
        assertFalse(ScanFramePolicy.accepts(1280, 720, 45))
        assertFalse(ScanFramePolicy.accepts(1280, 720, 360))
    }
}
