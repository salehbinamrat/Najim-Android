package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface SessionViewState {
    data object Idle : SessionViewState
    data object Loading : SessionViewState
    data object Cancelling : SessionViewState
    class Snapshot(val status: SessionStatus, val legacy: Boolean) : SessionViewState {
        override fun toString() = "SessionViewState.Snapshot(redacted)"
    }
}

/** Owner-thread state. No cached status survives a network change or background transition. */
internal class SessionRefreshController(
    private val worker: Executor,
    private val dispatch: ((() -> Unit) -> Unit),
    private val read: (DiscoveryState.Ready, LoginProtocol, Boolean, () -> Boolean) -> SessionStatus,
    private val publish: (SessionViewState) -> Unit
) {
    private var token: AtomicBoolean? = null
    private var selected: DiscoveryState.Ready? = null
    private var disposed = false

    val busy: Boolean get() = token != null

    fun refresh(network: DiscoveryState, legacy: Boolean, consent: Boolean): Boolean {
        if (disposed || busy || network !is DiscoveryState.Ready) return false
        if (legacy && !consent) {
            publish(SessionViewState.Snapshot(SessionStatus.ConsentRequired, true))
            return false
        }
        val mode = if (legacy) LoginProtocol.HTTP_PAP else LoginProtocol.HTTPS_PAP
        val attempt = AtomicBoolean(false)
        token = attempt
        selected = network
        publish(SessionViewState.Loading)
        try {
            worker.execute {
                val result = try {
                    if (attempt.get()) SessionStatus.NetworkChanged
                    else read(network, mode, consent && legacy, attempt::get)
                } catch (_: RuntimeException) {
                    SessionStatus.Unavailable
                }
                dispatch { complete(attempt, result, legacy) }
            }
        } catch (_: RejectedExecutionException) {
            complete(attempt, SessionStatus.Unavailable, legacy)
            return false
        }
        return true
    }

    fun networkChanged(network: DiscoveryState) {
        if (selected != null && selected != network) invalidate()
    }

    fun invalidate() {
        selected = null
        token?.set(true)
        if (!disposed) publish(if (busy) SessionViewState.Cancelling else SessionViewState.Idle)
    }

    fun dispose() {
        disposed = true
        selected = null
        token?.set(true)
    }

    private fun complete(attempt: AtomicBoolean, result: SessionStatus, legacy: Boolean) {
        if (token !== attempt) return
        token = null
        if (disposed) return
        if (attempt.get()) publish(SessionViewState.Idle)
        else publish(SessionViewState.Snapshot(result, legacy))
    }
}
