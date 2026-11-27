package com.najim.android.ocr

/** Main-thread owner; at most one inference and one bounded frame copy at a time. */
internal class FrameGate(private val intervalMillis: Long = 250L) {
    init { require(intervalMillis >= 0L) }
    private var lastStarted: Long? = null
    private var inFlight = false
    private var stopped = false

    fun tryStart(nowMillis: Long): Boolean {
        if (stopped || inFlight) return false
        val last = lastStarted
        if (last != null && nowMillis - last < intervalMillis) return false
        lastStarted = nowMillis
        inFlight = true
        return true
    }

    fun finish() { inFlight = false }
    fun stop() { stopped = true }
    fun mayPublish(): Boolean = !stopped
}
