package com.najim.android.feedback

import com.najim.android.auth.DataAmount
import com.najim.android.auth.SessionMetrics
import org.junit.Assert.*
import org.junit.Test

class SessionSpeechTest {
    @Test fun readsTypedMetricsOnly() {
        val speech = SessionSpeech.summary(SessionMetrics(
            remainingTotal = DataAmount("not trusted", 1572864L),
            timeLeftSeconds = 3661L, downloaded = DataAmount("2.5 GB", null)))
        assertTrue(speech.contains("1.50 ميبي بايت"))
        assertTrue(speech.contains("1 ساعة و 1 دقيقة و 1 ثانية"))
        assertTrue(speech.contains("2.5 جيجابايت"))
        assertFalse(speech.contains("not trusted"))
    }
    @Test fun malformedAndMissingDataAreNotInventedOrSpoken() {
        val speech = SessionSpeech.summary(SessionMetrics(
            uploaded = DataAmount("account secret", null), uptimeSeconds = -1))
        assertFalse(speech.contains("account secret"))
        assertTrue(speech.contains("غير متاح"))
    }
}
