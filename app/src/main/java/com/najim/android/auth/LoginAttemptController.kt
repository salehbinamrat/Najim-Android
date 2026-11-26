package com.najim.android.auth

import com.najim.android.network.DiscoveryState
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface LoginAttemptState {
    data object Idle : LoginAttemptState
    data object Running : LoginAttemptState
    data object Cancelling : LoginAttemptState
    data object Cancelled : LoginAttemptState
    data class Finished(val result: GatewayLoginResult) : LoginAttemptState
}

/** All public methods and completion callbacks run on the owner (main) thread. */
internal class LoginAttemptController(
    private val worker: Executor,
    private val dispatch: ((() -> Unit) -> Unit),
    private val login: (DiscoveryState.Ready, String, String?, LoginProtocol, Boolean, () -> Boolean) -> GatewayLoginResult,
    private val publish: (LoginAttemptState) -> Unit
) {
    private var token: AtomicBoolean? = null
    private var selectedNetwork: DiscoveryState.Ready? = null
    private var disposed = false

    fun start(network: DiscoveryState, username: String, password: String?,
              protocol: LoginProtocol, consent: Boolean): Boolean {
        if (disposed || token != null || network !is DiscoveryState.Ready) return false
        val invalid = LoginRequest.validateInput(network, network, username, password, protocol, consent)
        if (invalid != null) {
            publish(LoginAttemptState.Finished(if (invalid == PreparationError.INSECURE_HTTP_NOT_APPROVED)
                GatewayLoginResult.INSECURE_HTTP_NOT_APPROVED else GatewayLoginResult.INVALID_INPUT))
            return false
        }
        val attempt = AtomicBoolean(false)
        token = attempt
        selectedNetwork = network
        publish(LoginAttemptState.Running)
        try {
            worker.execute {
                val result = try {
                    if (attempt.get()) GatewayLoginResult.SESSION_UNVERIFIED
                    else login(network, username, password, protocol, consent, attempt::get)
                } catch (_: RuntimeException) {
                    GatewayLoginResult.SESSION_UNVERIFIED
                }
                dispatch { complete(attempt, result) }
            }
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            complete(attempt, GatewayLoginResult.TRANSPORT_UNAVAILABLE)
            return false
        }
        return true
    }

    fun cancel() {
        val attempt = token ?: return
        if (attempt.compareAndSet(false, true) && !disposed) publish(LoginAttemptState.Cancelling)
    }

    fun networkChanged(network: DiscoveryState) {
        if (selectedNetwork == null || selectedNetwork == network) return
        if (token != null) cancel()
        else {
            selectedNetwork = null
            if (!disposed) publish(LoginAttemptState.Idle)
        }
    }

    fun dispose() {
        disposed = true
        token?.set(true)
    }

    private fun complete(attempt: AtomicBoolean, result: GatewayLoginResult) {
        if (token !== attempt) return
        token = null
        if (disposed) return
        if (attempt.get()) {
            selectedNetwork = null
            publish(LoginAttemptState.Cancelled)
        } else publish(LoginAttemptState.Finished(result))
    }
}
