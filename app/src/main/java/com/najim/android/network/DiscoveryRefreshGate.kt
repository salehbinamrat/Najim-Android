package com.najim.android.network

/** Thread-safe admission for callbacks from one foreground monitoring lifetime.
 * An old listener cannot enqueue work or consume a newer listener's pending refresh.
 */
internal class DiscoveryRefreshGate {
    class Lifetime internal constructor()

    private var current: Lifetime? = null
    private var pending = false

    @Synchronized
    fun start(): Lifetime {
        check(current == null)
        return Lifetime().also { current = it; pending = false }
    }

    @Synchronized
    fun stop() {
        current = null
        pending = false
    }

    @Synchronized
    fun request(lifetime: Lifetime): Boolean {
        if (current !== lifetime || pending) return false
        pending = true
        return true
    }

    @Synchronized
    fun consume(lifetime: Lifetime): Boolean {
        if (current !== lifetime || !pending) return false
        pending = false
        return true
    }
}
