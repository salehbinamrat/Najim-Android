package com.najim.android.feedback

import android.media.AudioManager
import android.media.ToneGenerator

/** Local, brief non-verbal sounds. Does not request audio focus or change device volume. */
internal class AndroidTonePlayer {
    private var player: ToneGenerator? = null

    fun play(cue: FeedbackCue) {
        try {
            val generator = player ?: ToneGenerator(AudioManager.STREAM_MUSIC, 35).also { player = it }
            val tone = when (cue) {
                FeedbackCue.CONFIRMED -> ToneGenerator.TONE_PROP_ACK
                FeedbackCue.CAUTION -> ToneGenerator.TONE_PROP_BEEP2
                FeedbackCue.FAILED -> ToneGenerator.TONE_PROP_NACK
                FeedbackCue.SCAN_ACCEPTED -> ToneGenerator.TONE_PROP_BEEP
            }
            generator.stopTone()
            generator.startTone(tone, 180)
        } catch (_: RuntimeException) { stop() }
    }

    fun stop() {
        val generator = player
        player = null
        if (generator != null) {
            try { generator.stopTone() } catch (_: RuntimeException) {}
            try { generator.release() } catch (_: RuntimeException) {}
        }
    }
}
