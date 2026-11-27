package com.najim.android.ocr

import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
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
 * Bundled on-device OCR or QR recognition. All calls and Task completion use the main thread.
 * Caller owns image lifetime and must release it in onFinished, even after close().
 * No camera permission, disk persistence, remote request or auto-login here.
 */
internal class LocalVoucherRecognizer(
    mode: ScanMode, private val bounds: ScanBounds = ScanBounds()
) : AutoCloseable {
    private val textReader = if (mode == ScanMode.OCR)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) else null
    private val qrReader = if (mode == ScanMode.QR) BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
    ) else null

    private fun process(image: InputImage): Task<ScanSuggestion> {
        val text = textReader
        return if (text != null) text.process(image).continueWith { task ->
            val lines = task.result.textBlocks.flatMap { block -> block.lines.map { it.text } }
            VoucherCandidates.extract(lines, bounds)
        } else requireNotNull(qrReader).process(image).continueWith { task ->
            QrVoucherPolicy.extract(task.result.map { barcode ->
                if (barcode.format == Barcode.FORMAT_QR_CODE) barcode.rawValue else null
            }, bounds)
        }
    }

    private fun closeEngine() {
        textReader?.close()
        qrReader?.close()
    }
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
            process(image)
        } catch (_: RuntimeException) {
            finish(RecognitionResult.Failed, onFinished)
            return
        }
        task.addOnCompleteListener { completed ->
            val result = if (closed) RecognitionResult.Closed
                else if (!completed.isSuccessful) RecognitionResult.Failed
                else try {
                    RecognitionResult.Read(completed.result)
                } catch (_: RuntimeException) {
                    RecognitionResult.Failed
                }
            finish(result, onFinished)
        }
    }

    private fun finish(result: RecognitionResult, callback: (RecognitionResult) -> Unit) {
        inFlight = false
        try {
            if (closed) closeEngine()
        } finally {
            callback(result)
        }
    }

    override fun close() {
        checkMainThread()
        if (closed) return
        closed = true
        if (!inFlight) closeEngine()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "OCR owner must use the main thread" }
    }
}
