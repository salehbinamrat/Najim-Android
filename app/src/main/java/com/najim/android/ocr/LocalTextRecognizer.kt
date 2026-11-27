package com.najim.android.ocr

import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

internal sealed interface RecognitionResult {
    data object Busy : RecognitionResult
    data object Failed : RecognitionResult
    data object Closed : RecognitionResult
    class Read(val suggestion: ScanSuggestion) : RecognitionResult {
        override fun toString() = "RecognitionResult.Read(redacted)"
    }
}

/**
 * Bundled on-device Latin recognition. All calls and Task completion use the main thread.
 * Caller owns image lifetime and must release it in onFinished, even after close().
 * No camera permission, disk persistence, remote request or auto-login here.
 */
internal class LocalTextRecognizer(private val bounds: ScanBounds = ScanBounds()) : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var inFlight = false
    private var closed = false

    fun recognize(image: InputImage, onFinished: (RecognitionResult) -> Unit) {
        checkMainThread()
        if (closed) {
            onFinished(RecognitionResult.Closed)
            return
        }
        if (inFlight) {
            onFinished(RecognitionResult.Busy)
            return
        }
        inFlight = true
        val task = try {
            recognizer.process(image)
        } catch (_: RuntimeException) {
            finish(RecognitionResult.Failed, onFinished)
            return
        }
        task.addOnCompleteListener { completed ->
            val result = if (closed) RecognitionResult.Closed
                else if (!completed.isSuccessful) RecognitionResult.Failed
                else try {
                    val lines = completed.result.textBlocks.flatMap { block -> block.lines.map { it.text } }
                    RecognitionResult.Read(VoucherCandidates.extract(lines, bounds))
                } catch (_: RuntimeException) {
                    RecognitionResult.Failed
                }
            finish(result, onFinished)
        }
    }

    private fun finish(result: RecognitionResult, callback: (RecognitionResult) -> Unit) {
        inFlight = false
        try {
            if (closed) recognizer.close()
        } finally {
            callback(result)
        }
    }

    override fun close() {
        checkMainThread()
        if (closed) return
        closed = true
        if (!inFlight) recognizer.close()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "OCR owner must use the main thread" }
    }
}
