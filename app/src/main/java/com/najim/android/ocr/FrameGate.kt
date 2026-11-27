package com.najim.android.ocr

/** Thread-safe gate shared by frame worker and main-thread recognition; at most one inference and one bounded frame copy at a time. */
internal class FrameGate(private val intervalMillis: Long = 250L) {
    init { require(intervalMillis >= 0L) }
    private var lastStarted: Long? = null
    private var inFlight = false
    private var stopped = false

    @Synchronized
    fun tryStart(nowMillis: Long): Boolean {
        if (stopped || inFlight) return false
        val last = lastStarted
        if (last != null && nowMillis - last < intervalMillis) return false
        lastStarted = nowMillis
        inFlight = true
        return true
    }

    @Synchronized
    fun finish() { inFlight = false }
    @Synchronized
    fun stop() { stopped = true }
    @Synchronized
    fun mayPublish(): Boolean = !stopped
}
