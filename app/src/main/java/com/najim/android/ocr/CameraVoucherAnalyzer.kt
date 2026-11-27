package com.najim.android.ocr

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * CameraX worker copies only admitted frames and releases the ImageProxy before main-thread
 * recognition. The gate remains occupied until the result is processed or discarded.
 * Recognizer and stability state are main-thread owned; close() is main-thread only.
 */
internal class CameraVoucherAnalyzer(
    mode: ScanMode,
    private val publish: (RecognitionResult) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private val recognizer = LocalVoucherRecognizer(mode)
    private val gate = FrameGate()
    private val stability = ScanStability()

    override fun analyze(image: ImageProxy) {
        if (!gate.tryStart(SystemClock.elapsedRealtime())) {
            image.close()
            return
        }
        var rotation = 0
        val bitmap = try {
            rotation = image.imageInfo.rotationDegrees
            require(ScanFramePolicy.accepts(image.width, image.height, rotation))
            // A stopped session need not allocate a bitmap.
            if (gate.mayPublish()) image.toBitmap() else null
        } catch (_: RuntimeException) {
            null
        } finally {
            image.close()
        }
        if (!gate.mayPublish()) {
            bitmap?.recycle()
            gate.finish()
            return
        }
        if (!main.post { recognizeCopied(bitmap, rotation) }) {
            bitmap?.recycle()
            gate.finish()
        }
    }

    private fun recognizeCopied(bitmap: Bitmap?, rotation: Int) {
        if (!gate.mayPublish()) {
            bitmap?.recycle()
            gate.finish()
            return
        }
        if (bitmap == null) {
            complete(RecognitionResult.Failed)
            return
        }
        val input = try {
            InputImage.fromBitmap(bitmap, rotation)
        } catch (_: RuntimeException) {
            bitmap.recycle()
            complete(RecognitionResult.Failed)
            return
        }
        recognizer.recognize(input) { result ->
            bitmap.recycle()
            complete(result)
        }
    }

    private fun complete(result: RecognitionResult) {
        try {
            if (!gate.mayPublish()) return
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
        } finally {
            gate.finish()
        }
    }

    override fun close() {
        gate.stop()
        stability.reset()
        recognizer.close()
    }

    companion object {
        // One process-owned serial camera callback executor. CameraX KEEP_ONLY_LATEST
        // controls frame backpressure; no application-owned frame queue.
        val frameWorker = Executors.newSingleThreadExecutor()
    }
}
