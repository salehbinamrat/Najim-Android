package com.najim.android.ocr

import android.content.ContentResolver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface GalleryState {
    data object Idle : GalleryState
    data object Picking : GalleryState
    data object Loading : GalleryState
    data object Failed : GalleryState
    data object NoNumber : GalleryState
    data object Ambiguous : GalleryState
    data object Unsupported : GalleryState
    class Candidate(val value: String) : GalleryState {
        override fun toString() = "GalleryCandidate(redacted)"
    }
}

/** Main-thread owner. Picker state survives its temporary Activity stop, never process recreation. */
internal class GalleryImport(
    private val resolver: ContentResolver,
    private val worker: Executor,
    private val publish: (GalleryState) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private var state: GalleryState = GalleryState.Idle
    private var token: AtomicBoolean? = null
    private var recognizer: LocalVoucherRecognizer? = null
    private var mode: ScanMode = ScanMode.OCR
    private var disposed = false

    fun beginPicker(selectedMode: ScanMode): Boolean {
        if (disposed || state == GalleryState.Picking || token != null) return false
        mode = selectedMode
        set(GalleryState.Picking)
        return true
    }

    fun pickerResult(uri: Uri?) {
        if (disposed || state != GalleryState.Picking) return
        if (uri == null) { set(GalleryState.Idle); return }
        val attempt = AtomicBoolean(false)
        token = attempt
        set(GalleryState.Loading)
        try {
            worker.execute {
                val decoded = if (attempt.get()) null else GalleryImageDecoder.decode(resolver, uri)
                main.post { recognize(attempt, decoded) }
            }
        } catch (_: RejectedExecutionException) {
            token = null
            set(GalleryState.Failed)
        }
    }

    private fun recognize(attempt: AtomicBoolean, decoded: GalleryImageDecoder.Decoded?) {
        if (disposed || token !== attempt || attempt.get()) {
            decoded?.bitmap?.recycle()
            if (token === attempt) token = null
            return
        }
        if (decoded == null) { token = null; set(GalleryState.Failed); return }
        val reader = try { LocalVoucherRecognizer(mode) } catch (_: RuntimeException) {
            decoded.bitmap.recycle()
            token = null
            set(GalleryState.Failed)
            return
        }
        recognizer = reader
        val image = try { InputImage.fromBitmap(decoded.bitmap, decoded.rotation) }
        catch (_: RuntimeException) {
            decoded.bitmap.recycle()
            recognizer = null
            token = null
            reader.close()
            set(GalleryState.Failed)
            return
        }
        reader.recognize(image) { result ->
            decoded.bitmap.recycle()
            if (recognizer === reader) recognizer = null
            reader.close()
            if (token === attempt) {
                token = null
                if (!disposed && !attempt.get()) set(when (result) {
                    is RecognitionResult.Read -> when (val suggestion = result.suggestion) {
                        is ScanSuggestion.Candidate -> GalleryState.Candidate(suggestion.value)
                        ScanSuggestion.Ambiguous -> GalleryState.Ambiguous
                        ScanSuggestion.Unsupported -> GalleryState.Unsupported
                        else -> GalleryState.NoNumber
                    }
                    else -> GalleryState.Failed
                })
            }
        }
    }

    fun confirm(): String? {
        val candidate = state as? GalleryState.Candidate ?: return null
        set(GalleryState.Idle)
        return candidate.value
    }

    fun stop() {
        if (state != GalleryState.Picking) cancel()
    }

    fun cancel() {
        token?.set(true)
        // Do not close the reader while its callback owns the bitmap; close defers until completion.
        recognizer?.close()
        set(GalleryState.Idle)
    }

    fun dispose() {
        disposed = true
        cancel()
    }

    private fun set(next: GalleryState) {
        state = next
        if (!disposed) publish(next)
    }
}
