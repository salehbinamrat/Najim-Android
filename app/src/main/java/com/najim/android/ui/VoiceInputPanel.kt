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

/** One bounded, user-triggered recording; no cloud recognizer fallback or audio files. */
@Composable
internal fun VoiceInputPanel(lifecycleOwner: LifecycleOwner, enabled: Boolean,
                            onListening: (Boolean) -> Unit, silenceAudio: () -> Unit,
                            onAccepted: (String) -> Unit) {
    val context = LocalContext.current
    val currentListening by rememberUpdatedState(onListening)
    val currentAccepted by rememberUpdatedState(onAccepted)
    val currentEnabled by rememberUpdatedState(enabled)
    var candidate by remember { mutableStateOf<String?>(null) }
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
        notice = if (it) R.string.voice_permission else R.string.voice_unavailable
    }
    val available = remember(context) {
        Build.VERSION.SDK_INT >= 31 && runCatching {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        }.getOrDefault(false)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                finish()
                candidate = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            finish()
            candidate = null
        }
    }
    LaunchedEffect(enabled) {
        if (!enabled) {
            finish()
            candidate = null
        }
    }
    OutlinedButton(onClick = {
        candidate = null
        notice = null
        if (Build.VERSION.SDK_INT < 31 || !available) {
            notice = R.string.voice_unavailable
        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            notice = R.string.voice_permission
            try { permission.launch(Manifest.permission.RECORD_AUDIO) }
            catch (_: RuntimeException) { notice = R.string.voice_unavailable }
        } else if (recognizer == null && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            try {
                silenceAudio()
                val instance = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
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
                        notice = R.string.voice_failed
                    }
                    override fun onResults(results: Bundle?) {
                        if (recognizer !== instance) return
                        val number = if (currentEnabled &&
                            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                            SpokenVoucher.candidate(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
                        else null
                        finish()
                        candidate = number
                        notice = if (number == null) R.string.voice_failed else null
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
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                })
            } catch (_: RuntimeException) {
                finish()
                notice = R.string.voice_unavailable
            }
        }
    }, enabled = enabled && !listening, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.voice_input))
    }
    Text(stringResource(R.string.voice_hint), style = MaterialTheme.typography.bodySmall)
    if (listening) {
        StatusNotice(stringResource(R.string.voice_listening))
        OutlinedButton(onClick = { finish() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.voice_stop))
        }
    }
    notice?.let { StatusNotice(stringResource(it)) }
    val reviewed = candidate
    if (reviewed != null && enabled) {
        Text(stringResource(R.string.voice_review))
        Text(reviewed, style = MaterialTheme.typography.titleMedium)
        Button(onClick = { candidate = null; currentAccepted(reviewed) },
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.voice_accept)) }
        OutlinedButton(onClick = { candidate = null }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.voice_discard))
        }
    }
}
