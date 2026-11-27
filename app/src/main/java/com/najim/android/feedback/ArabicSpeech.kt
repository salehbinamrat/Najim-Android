package com.najim.android.feedback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.najim.android.R

/**
 * Only fixed, public phrases go to the platform engine. No network-required voices,
 * no language-data downloads, no queued or replayed events. Engine behavior is external.
 */
internal class ArabicSpeech(context: Context, private val availability: (Boolean) -> Unit) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var engine: TextToSpeech? = null
    private var generation = 0
    private var ready = false

    fun prepare() {
        if (engine != null) return
        val attempt = ++generation
        try {
            engine = TextToSpeech(app) { result ->
                main.post {
                    if (attempt == generation) {
                        val current = engine
                        ready = if (result != TextToSpeech.SUCCESS || current == null) false
                        else runCatching {
                            val voice = current.voices.orEmpty()
                                .filter { it.locale.language == "ar" && !it.isNetworkConnectionRequired &&
                                    "notInstalled" !in it.features.orEmpty() }
                                .sortedWith(compareByDescending<android.speech.tts.Voice> { it.quality }
                                    .thenBy { it.latency }.thenBy { it.name })
                                .firstOrNull()
                            voice != null && current.setVoice(voice) == TextToSpeech.SUCCESS &&
                                current.voice?.let { it.locale.language == "ar" && !it.isNetworkConnectionRequired } == true
                        }.getOrDefault(false)
                        availability(ready)
                    }
                }
            }
        } catch (_: RuntimeException) { availability(false) }
    }

    fun speak(cue: SpeechCue): Boolean {
        val current = engine ?: return false
        if (!ready) return false
        return runCatching {
            val voice = current.voice
            if (voice == null || voice.locale.language != "ar" || voice.isNetworkConnectionRequired ||
                "notInstalled" in voice.features.orEmpty()) false
            else current.speak(app.getString(phrase(cue)), TextToSpeech.QUEUE_FLUSH, null,
                "najim-public-guidance") == TextToSpeech.SUCCESS
        }.getOrDefault(false)
    }

    fun stop() {
        generation++
        ready = false
        val old = engine
        engine = null
        if (old != null) {
            runCatching { old.stop() }
            runCatching { old.shutdown() }
        }
        availability(false)
    }

    private fun phrase(cue: SpeechCue): Int = when (cue) {
        SpeechCue.LOGIN_CONFIRMED -> R.string.speech_login_confirmed
        SpeechCue.LOGIN_CAUTION -> R.string.speech_login_caution
        SpeechCue.LOGIN_FAILED -> R.string.speech_login_failed
        SpeechCue.LOGOUT_CONFIRMED -> R.string.speech_logout_confirmed
        SpeechCue.LOGOUT_CAUTION -> R.string.speech_logout_caution
        SpeechCue.LOGOUT_FAILED -> R.string.speech_logout_failed
        SpeechCue.SCAN_ACCEPTED -> R.string.speech_scan_accepted
    }
}
