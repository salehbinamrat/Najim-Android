package com.najim.android.ocr

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage

/** Main-thread analyzer: copy only accepted frames, release CameraX buffer before inference. */
internal class CameraVoucherAnalyzer(
    mode: ScanMode,
    private val publish: (RecognitionResult) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val recognizer = LocalVoucherRecognizer(mode)
    private val gate = FrameGate()
    private val stability = ScanStability()

    override fun analyze(image: ImageProxy) {
        if (!gate.tryStart(SystemClock.elapsedRealtime())) {
            image.close()
            return
        }
        val rotation: Int
        val bitmap = try {
            rotation = image.imageInfo.rotationDegrees
            require(image.width.toLong() * image.height <= 1920L * 1080L)
            image.toBitmap()
        } catch (_: RuntimeException) {
            gate.finish()
            stability.reset()
            if (gate.mayPublish()) publish(RecognitionResult.Failed)
            return
        } finally {
            image.close()
        }
        val input = try {
            InputImage.fromBitmap(bitmap, rotation)
        } catch (_: RuntimeException) {
            bitmap.recycle()
            gate.finish()
            stability.reset()
            if (gate.mayPublish()) publish(RecognitionResult.Failed)
            return
        }
        recognizer.recognize(input) { result ->
            bitmap.recycle()
            gate.finish()
            if (gate.mayPublish()) {
                val stable = if (result is RecognitionResult.Read) {
                    RecognitionResult.Read(stability.accept(result.suggestion))
                } else {
                    stability.reset()
                    result
                }
                if (stable is RecognitionResult.Read && stable.suggestion is ScanSuggestion.Candidate) {
                    gate.stop()
                }
                publish(stable)
            }
        }
    }

    override fun close() {
        gate.stop()
        stability.reset()
        recognizer.close()
    }
}
