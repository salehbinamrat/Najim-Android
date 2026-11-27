package com.najim.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.najim.android.R
import com.najim.android.ocr.CameraVoucherAnalyzer
import com.najim.android.ocr.RecognitionResult
import com.najim.android.ocr.ScanSuggestion
import com.najim.android.ocr.ScanMode

@Composable
internal fun VoucherScanner(owner: LifecycleOwner, mode: ScanMode, enabled: Boolean, onConfirmed: (String) -> Unit) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<String?>(null) }
    var reading by remember { mutableStateOf<RecognitionResult?>(null) }
    var generation by remember { mutableStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        open = granted && enabled
    }

    LaunchedEffect(enabled) {
        if (!enabled) {
            open = false
            candidate = null
            reading = null
            failed = false
        }
    }

    if (!open || !enabled) {
        OutlinedButton(onClick = {
            denied = false
            failed = false
            candidate = null
            reading = null
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                open = true
            } else permission.launch(Manifest.permission.CAMERA)
        }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scan_open))
        }
        if (denied) Text(stringResource(R.string.scan_permission_denied))
        return
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleMedium)
            val code = candidate
            if (code != null) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(code, style = MaterialTheme.typography.headlineSmall)
                }
                Text(stringResource(R.string.scan_confirm_hint))
                Button(onClick = {
                    candidate = null
                    open = false
                    reading = null
                    onConfirmed(code)
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.scan_use)) }
            } else if (failed) {
                Text(stringResource(R.string.scan_camera_unavailable))
            } else {
                Text(stringResource(if (mode == ScanMode.QR) R.string.qr_instructions else R.string.scan_instructions))
                key(generation) {
                    CameraPreview(owner, mode, onRead = { result ->
                        reading = result
                        val value = (result as? RecognitionResult.Read)?.suggestion as? ScanSuggestion.Candidate
                        if (value != null) candidate = value.value
                    }, onFailure = { failed = true })
                }
                val message = when (val result = reading) {
                    is RecognitionResult.Read -> when (result.suggestion) {
                        ScanSuggestion.Ambiguous -> R.string.scan_ambiguous
                        ScanSuggestion.Unstable -> R.string.scan_unstable
                        ScanSuggestion.Unsupported -> R.string.qr_unsupported
                        else -> R.string.scan_searching
                    }
                    RecognitionResult.Failed -> R.string.scan_failed
                    else -> R.string.scan_searching
                }
                Text(stringResource(message))
            }
            if (code != null || failed) {
                OutlinedButton(onClick = {
                    candidate = null
                    failed = false
                    reading = null
                    generation++
                }) { Text(stringResource(R.string.scan_again)) }
            }
            OutlinedButton(onClick = {
                open = false
                candidate = null
                reading = null
                failed = false
            }) { Text(stringResource(R.string.scan_close)) }
        }
    }
}

@Composable
private fun CameraPreview(
    owner: LifecycleOwner, mode: ScanMode, onRead: (RecognitionResult) -> Unit, onFailure: () -> Unit
) {
    val context = LocalContext.current
    val read by rememberUpdatedState(onRead)
    val failure by rememberUpdatedState(onFailure)
    val view = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(240.dp))
    DisposableEffect(owner, view, mode) {
        var active = true
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null
        var analysis: ImageAnalysis? = null
        var analyzer: CameraVoucherAnalyzer? = null
        val executor = ContextCompat.getMainExecutor(context)
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (active) {
                try {
                    val camera = future.get()
                    provider = camera
                    check(camera.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                    val p = Preview.Builder().build()
                    preview = p
                    p.setSurfaceProvider(view.surfaceProvider)
                    val selector = ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
                    ).build()
                    val frames = ImageAnalysis.Builder()
                        .setResolutionSelector(selector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    analysis = frames
                    val worker = CameraVoucherAnalyzer(mode) { result -> if (active) read(result) }
                    analyzer = worker
                    frames.setAnalyzer(executor, worker)
                    camera.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, p, frames)
                } catch (_: Exception) {
                    if (active) failure()
                }
            }
        }, executor)
        onDispose {
            active = false
            analysis?.clearAnalyzer()
            analyzer?.close()
            val useCases = listOfNotNull(preview, analysis).toTypedArray()
            if (useCases.isNotEmpty()) provider?.unbind(*useCases)
        }
    }
}
