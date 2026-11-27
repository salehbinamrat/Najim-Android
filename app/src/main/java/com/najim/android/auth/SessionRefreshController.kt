package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface SessionViewState {
    data object Idle : SessionViewState
    data object Loading : SessionViewState
    data object Cancelling : SessionViewState
    data object LoggingOut : SessionViewState
    data class LogoutFinished(val result: LogoutResult) : SessionViewState
    class Snapshot(val status: SessionStatus, val legacy: Boolean) : SessionViewState {
        override fun toString() = "SessionViewState.Snapshot(redacted)"
    }
}

/** Owner-thread state. No cached status survives a network change or background transition. */
internal class SessionRefreshController(
    private val worker: Executor,
    private val dispatch: ((() -> Unit) -> Unit),
    private val read: (DiscoveryState.Ready, LoginProtocol, Boolean, () -> Boolean) -> SessionStatus,
    private val publish: (SessionViewState) -> Unit,
    private val endSession: (DiscoveryState.Ready, String, Boolean, Boolean, () -> Boolean) -> LogoutResult =
        { _, _, _, _, _ -> LogoutResult.PREFLIGHT_UNKNOWN }
) {
    private var token: AtomicBoolean? = null
    private var selected: DiscoveryState.Ready? = null
    private var disposed = false
    private var snapshot: SessionViewState.Snapshot? = null

    val busy: Boolean get() = token != null

    fun refresh(network: DiscoveryState, legacy: Boolean, consent: Boolean): Boolean {
        if (disposed || busy || network !is DiscoveryState.Ready) return false
        snapshot = null
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

    fun logout(network: DiscoveryState, expected: SessionViewState.Snapshot, consent: Boolean): Boolean {
        if (disposed || busy || network !is DiscoveryState.Ready || selected != network ||
            snapshot !== expected) return false
        val active = expected.status as? SessionStatus.Active ?: return false
        if (active.legacy != expected.legacy || (expected.legacy && !consent)) return false
        snapshot = null
        val attempt = AtomicBoolean(false)
        token = attempt
        publish(SessionViewState.LoggingOut)
        try {
            worker.execute {
                val result = try {
                    if (attempt.get()) LogoutResult.NETWORK_CHANGED
                    else endSession(network, active.account.username, expected.legacy, consent, attempt::get)
                } catch (_: RuntimeException) { LogoutResult.UNVERIFIED }
                dispatch { completeLogout(attempt, result) }
            }
        } catch (_: RejectedExecutionException) {
            completeLogout(attempt, LogoutResult.PREFLIGHT_UNKNOWN)
            return false
        }
        return true
    }

    private fun completeLogout(attempt: AtomicBoolean, result: LogoutResult) {
        if (token !== attempt) return
        token = null
        if (disposed) return
        if (attempt.get()) publish(SessionViewState.Idle)
        else publish(SessionViewState.LogoutFinished(result))
    }

    fun networkChanged(network: DiscoveryState) {
        if (selected != null && selected != network) invalidate()
    }

    fun invalidate() {
        snapshot = null
        selected = null
        token?.set(true)
        if (!disposed) publish(if (busy) SessionViewState.Cancelling else SessionViewState.Idle)
    }

    fun dispose() {
        disposed = true
        snapshot = null
        selected = null
        token?.set(true)
    }

    private fun complete(attempt: AtomicBoolean, result: SessionStatus, legacy: Boolean) {
        if (token !== attempt) return
        token = null
        if (disposed) return
        if (attempt.get()) publish(SessionViewState.Idle)
        else {
            val next = SessionViewState.Snapshot(result, legacy)
            snapshot = next
            publish(next)
        }
    }
}
