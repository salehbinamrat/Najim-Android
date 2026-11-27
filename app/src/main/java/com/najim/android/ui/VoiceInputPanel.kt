package com.najim.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.najim.android.R
import com.najim.android.domain.SpokenVoucher

/** Local recognition preferred. System provider only after separate explicit consent;
 * no automatic fallback, audio files, password dictation or automatic login.
 */
@Composable
internal fun VoiceInputPanel(lifecycleOwner: LifecycleOwner, enabled: Boolean,
                            onListening: (Boolean) -> Unit, silenceAudio: () -> Unit,
                            onAccepted: (String) -> Unit) {
    val context = LocalContext.current
    val currentListening by rememberUpdatedState(onListening)
    val currentAccepted by rememberUpdatedState(onAccepted)
    val currentEnabled by rememberUpdatedState(enabled)
    var candidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var systemConfirmation by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf("ar-SA") }
    var notice by remember { mutableStateOf<Int?>(null) }
    var listening by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var timeout by remember { mutableStateOf<Runnable?>(null) }
    fun finish() {
        timeout?.let(main::removeCallbacks)
        timeout = null
        val old = recognizer
        recognizer = null
        if (old != null) {
            runCatching { old.cancel() }
            runCatching { old.destroy() }
        }
        listening = false
        currentListening(false)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notice = if (it) R.string.voice_permission_granted else R.string.voice_permission_denied
    }
    fun localAvailable(): Boolean = Build.VERSION.SDK_INT >= 31 && runCatching {
        SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }.getOrDefault(false)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                finish()
                candidates = emptyList()
                systemConfirmation = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            finish()
            candidates = emptyList()
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            finish()
            candidates = emptyList()
        }
    }
    fun begin(systemProvider: Boolean) {
        candidates = emptyList()
        notice = null
        if (!systemProvider && !localAvailable()) {
            notice = R.string.voice_unavailable
        } else if (systemProvider && !runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)) {
            notice = R.string.voice_no_service
        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            notice = R.string.voice_permission
            try { permission.launch(Manifest.permission.RECORD_AUDIO) }
            catch (_: RuntimeException) { notice = R.string.voice_unavailable }
        } else if (recognizer == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            try {
                silenceAudio()
                val instance = if (systemProvider) SpeechRecognizer.createSpeechRecognizer(context)
                    else if (Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    else return
                recognizer = instance
                listening = true
                currentListening(true)
                instance.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onError(error: Int) {
                        if (recognizer !== instance) return
                        finish()
                        notice = when (error) {
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_permission_denied
                            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> R.string.voice_language_unavailable
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_network_failure
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_recognizer_busy
                            else -> R.string.voice_failed
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        if (recognizer !== instance) return
                        val options = if (currentEnabled &&
                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                            SpokenVoucher.reviewOptions(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                        else emptyList()
                        finish()
                        candidates = options
                        notice = if (options.isEmpty()) R.string.voice_failed else null
                    }
                })
                val limit = Runnable {
                    if (recognizer === instance) {
                        finish()
                        notice = R.string.voice_failed
                    }
                }
                timeout = limit
                main.postDelayed(limit, 12_000)
                instance.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                })
            } catch (_: RuntimeException) {
                finish()
                notice = R.string.voice_unavailable
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        FilterChip(selected = language == "ar-SA", onClick = { language = "ar-SA"; candidates = emptyList() },
            enabled = enabled && !listening, label = { Text(stringResource(R.string.voice_arabic)) })
        FilterChip(selected = language == "en-US", onClick = { language = "en-US"; candidates = emptyList() },
            enabled = enabled && !listening, label = { Text(stringResource(R.string.voice_english)) })
    }
    OutlinedButton(onClick = { begin(false) }, enabled = enabled && !listening,
        modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.voice_input))
    }
    OutlinedButton(onClick = { systemConfirmation = true }, enabled = enabled && !listening,
        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.voice_system)) }
    if (systemConfirmation && enabled && !listening) {
        AlertDialog(onDismissRequest = { systemConfirmation = false },
            title = { Text(stringResource(R.string.voice_system)) },
            text = { Text(stringResource(R.string.voice_system_warning)) },
            confirmButton = { TextButton(onClick = {
                systemConfirmation = false
                begin(true)
            }) { Text(stringResource(R.string.voice_system_accept)) } },
            dismissButton = { TextButton(onClick = { systemConfirmation = false }) {
                Text(stringResource(R.string.voice_discard))
            } })
    }
    Text(stringResource(R.string.voice_hint), style = MaterialTheme.typography.bodySmall)
    if (listening) {
        StatusNotice(stringResource(R.string.voice_listening))
        OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.voice_stop))
        }
    }
    notice?.let { StatusNotice(stringResource(it)) }
    if (candidates.isNotEmpty() && enabled) {
        Text(stringResource(R.string.voice_review))
        for (reviewed in candidates) {
            OutlinedButton(onClick = { candidates = emptyList(); currentAccepted(reviewed) },
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.voice_accept_number, reviewed))
            }
        }
        OutlinedButton(onClick = { candidates = emptyList() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.voice_discard))
        }
    }
}
