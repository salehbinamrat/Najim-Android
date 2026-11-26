package com.najim.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.najim.android.auth.AndroidGatewayTransport
import com.najim.android.auth.LoginAttemptController
import com.najim.android.auth.LoginAttemptState
import com.najim.android.network.DiscoveryState
import com.najim.android.network.WifiDiscovery
import com.najim.android.ui.FoundationScreen
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val discoveryState = mutableStateOf<DiscoveryState>(DiscoveryState.Unavailable)
    private val attemptState = mutableStateOf<LoginAttemptState>(LoginAttemptState.Idle)
    private val inputGeneration = mutableStateOf(0)
    private lateinit var discovery: WifiDiscovery
    private lateinit var attempts: LoginAttemptController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val transport = AndroidGatewayTransport(applicationContext)
        val main = Handler(Looper.getMainLooper())
        attempts = LoginAttemptController(
            worker = loginWorker,
            dispatch = { task -> main.post { task() } },
            login = transport::login,
            publish = { attemptState.value = it }
        )
        discovery = WifiDiscovery(this) {
            attempts.networkChanged(it)
            discoveryState.value = it
        }
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    key(inputGeneration.value) {
                        FoundationScreen(discoveryState.value, discovery::refresh, attemptState.value,
                            startLogin = { user, password, mode, consent ->
                                attempts.start(discoveryState.value, user, password, mode, consent)
                            }, cancelLogin = attempts::cancel)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        discovery.start()
    }

    override fun onStop() {
        attempts.cancel()
        inputGeneration.value += 1
        discovery.stop()
        super.onStop()
    }

    override fun onDestroy() {
        attempts.dispose()
        super.onDestroy()
    }

    companion object {
        // Serialize requests even across Activity recreation. Never interrupt an uncertain POST and retry it.
        private val loginWorker = Executors.newSingleThreadExecutor()
    }
}
